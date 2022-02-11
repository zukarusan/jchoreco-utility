package com.github.zukarusan.chorecoutil.controller;

import com.github.zukarusan.chorecoutil.MainApplication;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.Background;
import javafx.scene.paint.Color;
import javafx.stage.*;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;
import java.net.URL;

public class MenuController {

    public static final int STREAM = 7;
    public static final int FILE = 8;
    public static final int RECORD = 9;

    public URL urlMenu;
    public URL urlFiles;
    public URL urlRecord;
    public URL urlStyleSheet;
    public URL recentFiles = null;

    FileChooser fileChooser = new FileChooser();
    FileChooser.ExtensionFilter extFilter = new FileChooser.ExtensionFilter("Audio WAV files (*.wav)", "*.wav");

    private Scene viewScene;
    private final Scene menu;
    private final Stage primary;
    private Scene main;
    private boolean isOpen = false;
    private final ClassLoader classLoader;
    FileController fileController;
    RecordController recordController;

    private final double max_h;
    private final double max_w;

    public MenuController(Stage primary, Class<? extends MainApplication> resourceGetter) {
        try {
            this.primary = primary;
            classLoader = resourceGetter.getClassLoader();
            urlMenu = resourceGetter.getResource("menu-view.fxml");
            urlFiles = resourceGetter.getResource("choreco-main-view.fxml");
            urlStyleSheet = resourceGetter.getResource("style.css");
            assert urlMenu != null;

            fileChooser.setTitle("Choose audio files");
            fileChooser.getExtensionFilters().add(extFilter);

            FXMLLoader loader = new FXMLLoader(urlMenu);
            loader.setClassLoader(classLoader);
            loader.setController(this);
            menu = new Scene(loader.load(), 500, 400);
            menu.getStylesheets().add(urlStyleSheet.toExternalForm());

            primary.initStyle(StageStyle.UNDECORATED);
            primary.setScene(menu);
            max_h = Screen.getPrimary().getBounds().getHeight();
            max_w = Screen.getPrimary().getBounds().getWidth();
        } catch (IOException e) {
            throw new IllegalStateException("Error loading view", e);
        }
    }

    @FXML protected Button closeButton;
    @FXML protected Button fromFile;

    @FXML
    public void initialize() {
        closeButton.setStyle("");
        closeButton.setOnAction(this::closeMenu);
        fromFile.setOnAction(this::openFromFile);

        primary.setOnCloseRequest(t -> {
            fileController.close(null);
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
    public void openFromFile(Event event) {
        try {
            File file = fileChooser.showOpenDialog(primary);
            if (file == null) return;
//            primary.hide();  // A.... bug? or just use new stage or new application
            fileController = new FileController(primary, file, this::openMenuBack);
            openViewer(urlFiles, fileController);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create controller", e);
        } catch (UnsupportedAudioFileException e) {
            throw new IllegalStateException("Unsupported file", e);
        }
    }

    final double WIDTH = 1280;
    final double HEIGHT = 720;
    double X_buffer;
    double Y_buffer;
    protected void openViewer(URL viewUrl, ChordViewController controller) {
        if (isOpen) {
            throw new IllegalCallerException("Is still open");
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
            primary.show();
        }
    }
}