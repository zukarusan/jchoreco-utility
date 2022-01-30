package com.github.zukarusan.chorecoutil.controller;

import com.github.zukarusan.chorecoutil.MainApplication;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

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
    public URL urlStream;

    public URL recentFiles = null;

    FileChooser fileChooser = new FileChooser();
    FileChooser.ExtensionFilter extFilter = new FileChooser.ExtensionFilter("Audio WAV files (*.wav)", "*.wav");

    private Scene viewScene;
    private final Scene menu;
    private final Stage primary;
    private Scene main;
    private boolean isOpen = false;

    public MenuController(Stage primary, Class<? extends MainApplication> resourceGetter) {
        try {
            this.primary = primary;
            urlMenu = resourceGetter.getResource("menu-view.fxml");
            urlFiles = resourceGetter.getResource("choreco-main-view.fxml");
            assert urlMenu != null;

            fileChooser.setTitle("Choose audio files");
            fileChooser.getExtensionFilters().add(extFilter);

            FXMLLoader loader = new FXMLLoader(urlMenu);
            loader.setController(this);
            loader.setClassLoader(resourceGetter.getClassLoader());
            menu = new Scene(loader.load(), 500, 400);

            primary.initStyle(StageStyle.UNDECORATED);
            primary.setScene(menu);
        } catch (IOException e) {
            throw new IllegalStateException("Error loading view", e);
        }
    }

    @FXML
    protected Button closeButton;

    @FXML
    public void initialize() {
        closeButton.setOnAction(this::closeMenu);
    }

    @FXML
    public void closeMenu(Event event) {
        primary.close();
        Platform.exit();
        event.consume();
    }

    @FXML
    public void openFromFile() {
        try {
            File audioFile = fileChooser.showOpenDialog(primary);
            FileController fileController = new FileController(audioFile, this::openMenuBack);
            openViewer(urlFiles, fileController);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create controller", e);
        }
    }

    protected void openViewer(URL viewUrl, ChordViewController controller) {
        if (isOpen) {
            throw new IllegalCallerException("Is still open");
        }
        try {
            FXMLLoader loader = new FXMLLoader(viewUrl);
            loader.setController(controller);
            Parent root = loader.load();
            viewScene = new Scene(root, 800, 600);
            isOpen = true;
            primary.setScene(viewScene);
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
            primary.setScene(menu);
        }
    }
}