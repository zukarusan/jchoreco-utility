package com.github.zukarusan.chorecoutil.controller;

import com.github.zukarusan.chorecoutil.MainApplication;
import com.github.zukarusan.chorecoutil.RecentFilesFactory;
import com.github.zukarusan.chorecoutil.controller.exception.UnsupportedChordFileException;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.*;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class MenuController {

    public static final int STREAM = 7;
    public static final int FILE = 8;
    public static final int RECORD = 9;

    public URL urlMenu;
    public URL urlFiles;
    public URL urlRecord;
    public URL urlStyleSheet;

    FileChooser fileChooser = new FileChooser();
    FileChooser.ExtensionFilter wavFilter = new FileChooser.ExtensionFilter("Audio WAV files (*.wav)", "*.wav");
    FileChooser.ExtensionFilter choFilter = new FileChooser.ExtensionFilter("Jchoreco chord (*.cho)", "*.cho");

    private Scene viewScene;
    private final Scene menu;
    private final Stage primary;
    private Scene main;
    private boolean isOpen = false;
    private final ClassLoader classLoader;
    FileController fileController;
    RecordController recordController;
    RecentFileController recentFileController;

    private final double max_h;
    private final double max_w;

    public static class LoadingWaiter implements Runnable {
        private boolean loading = true;
        final CountDownLatch waitLatch;

        public void setFinished() {
            this.loading = false;
            waitLatch.countDown();
        }

        public LoadingWaiter() {
            waitLatch = new CountDownLatch(1);
        }

        @Override
        public void run() {
            synchronized (waitLatch) {
                if (!loading) return;
                try {
                    waitLatch.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public MenuController(Stage primary, Class<? extends MainApplication> resourceGetter) {
        try {
            this.primary = primary;
            classLoader = resourceGetter.getClassLoader();
            urlMenu = resourceGetter.getResource("menu-view.fxml");
            urlFiles = resourceGetter.getResource("choreco-main-view.fxml");
            urlRecord = resourceGetter.getResource("choreco-record-view.fxml");
            urlStyleSheet = resourceGetter.getResource("style.css");
            assert urlMenu != null;

            recentFileController = new RecentFileController(resourceGetter);

            fileChooser.setTitle("Choose audio files");
            ObservableList<FileChooser.ExtensionFilter> filter = fileChooser.getExtensionFilters();
            filter.add(wavFilter);
            filter.add(choFilter);

            FXMLLoader loader = new FXMLLoader(urlMenu);
            loader.setClassLoader(classLoader);
            loader.setController(this);
            menu = new Scene(loader.load(), 500, 400);
            menu.getStylesheets().add(urlStyleSheet.toExternalForm());

//            primary.initStyle(StageStyle.UNDECORATED);
            primary.setScene(menu);
            max_h = Screen.getPrimary().getBounds().getHeight();
            max_w = Screen.getPrimary().getBounds().getWidth();
        } catch (IOException e) {
            throw new IllegalStateException("Error loading view", e);
        }
    }

    final private static class RecentFileController {
        public URL recentFiles;

        FXMLLoader recentLoader;
        List<HBox> recentBoxes;
        public RecentFileController(Class<? extends MainApplication> resourceGetter) {
            recentFiles = resourceGetter.getResource("recent-files-subview.fxml");
            recentBoxes = new ArrayList<>();
        }

        public boolean populate(Pane node) {
            List<File> files = RecentFilesFactory.getRecentFiles();
            recentBoxes = new ArrayList<>();
            if (files.isEmpty()) return false;
            try {
                for (File file : files) {
                    HBox box = new FXMLLoader(recentFiles).load();
                    Label labelName = (Label) box.getChildren().get(0);
                    Label labelPath = (Label) box.getChildren().get(1);
                    labelName.setText(file.getName());
                    labelPath.setText(file.getPath());
                    box.getStyleClass().add("boxRecent");
                    recentBoxes.add(box);
                    node.getChildren().add(box);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return true;
        }
    }

    @FXML protected Button closeButton;
    @FXML protected Button fromFile;
    @FXML protected Button fromMic;
    @FXML protected VBox recentCont;
    @FXML protected HBox noneBox;

    @FXML
    public void initialize() {
        closeButton.setStyle("");
        closeButton.setOnAction(this::closeMenu);
        fromFile.setOnAction(this::openWithDialog);
        fromMic.setOnAction(this::openFromMic);

        primary.setOnCloseRequest(t -> {
            if (fileController != null) fileController.close(null);
            if (recordController != null) recordController.close(null);
            Platform.exit();
            System.exit(0);
        });
    }

    @FXML
    public void closeMenu(Event event) {
        event.consume();
        primary.close();
        Platform.exit();
        System.exit(0);
    }

    @FXML
    public void openFromMic(Event event) {
        recordController = new RecordController(primary, this::openMenuBack, this::openFromFile);
        openViewer(urlRecord, recordController);
    }

    @FXML
    public void openWithDialog(Event event) {
        File file = fileChooser.showOpenDialog(primary);
        if (file == null) return;
//            primary.hide();  // A.... bug? or just use new stage or new application
        openFromFile(file);
    }

    public void openFromFile(File file) {
        try {
            LoadingWaiter initWaiter = new LoadingWaiter();
            fileController = new FileController(primary, file, this::openMenuBack, initWaiter);
            openViewer(urlFiles, fileController);
            initWaiter.setFinished();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create controller", e);
        } catch (UnsupportedChordFileException | UnsupportedAudioFileException e) {
            throw new IllegalStateException("Unsupported file", e);
        }
    }

    final double WIDTH = 1280;
    final double HEIGHT = 720;
    double X_buffer;
    double Y_buffer;
    protected void openViewer(URL viewUrl, ChordViewController controller) {
        if (isOpen) {
            viewScene = null;
        }
        try {
            X_buffer = primary.getX();
            Y_buffer = primary.getY();
            primary.setX((max_w-WIDTH)/2);
            primary.setY((max_h-HEIGHT)/2);
            FXMLLoader loader = new FXMLLoader(viewUrl);
            loader.setClassLoader(classLoader);
            loader.setController(controller);
            Parent root = loader.load();
            viewScene = new Scene(root, WIDTH, HEIGHT);
            viewScene.getStylesheets().add(urlStyleSheet.toExternalForm());
            isOpen = true;
            primary.setScene(viewScene);
//            primary.show();
        } catch (IOException e) {
            throw new IllegalStateException("Error in creating main viewer", e);
        }
    }

    public void resetRecentFiles() {

    }

    public void showWithRecentFiles() {
        /* show code */
        recentCont.getChildren().remove(noneBox);
        for (HBox box : recentFileController.recentBoxes) {
            recentCont.getChildren().remove(box);
        }
        if (!recentFileController.populate(recentCont)) recentCont.getChildren().add(noneBox);
        for (HBox box : recentFileController.recentBoxes) {
            Label path = (Label) box.getChildren().get(1);
            box.setOnMouseClicked(
                evt -> openFromFile(new File(path.getText()))
            );

        }
        primary.show();
    }

    public void hideStage() {
        primary.hide();
    }

    public Scene getPrimaryScene() {
        return this.menu;
    }

    public Scene getCurrentMainScene() {
        if (isOpen)
            return this.main;
        else
            return null;
    }

    protected Stage getPrimaryStage() {
        return primary;
    }

    public void openMenuBack() {
        if (isOpen) {
            isOpen = false;
            viewScene = null;
            fileController = null;
            recordController = null;

            primary.setScene(menu);
            primary.setX(X_buffer);
            primary.setY(Y_buffer);
            showWithRecentFiles();
        }
    }
}