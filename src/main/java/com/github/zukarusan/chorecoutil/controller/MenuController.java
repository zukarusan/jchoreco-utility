package com.github.zukarusan.chorecoutil.controller;

import com.github.zukarusan.chorecoutil.MainApplication;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;
import java.net.URL;

public class MenuController {

    public static final int STREAM = 7;
    public static final int FILE = 8;
    public static final int RECORD = 9;

    public URL urlMenu;
    public URL urlMain;

    public URL recentFiles = null;

    private final Scene menu;
    private final Stage primary;
    private Scene main;
    private boolean isOpen = false;

    public MenuController(Stage primary, Class<? extends MainApplication> resourceGetter) {
        try {
            this.primary = primary;
            urlMenu = resourceGetter.getResource("menu-view.fxml");
            urlMain = resourceGetter.getResource("choreco-main-view.fxml");
            assert urlMenu != null;

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
    public void closeMenu(Event event) {
        primary.close();
        Platform.exit();
        event.consume();
    }

    private void checkOpen() {
        if (isOpen) {
            throw new IllegalCallerException("Is still open");
        }
    }

    @FXML
    public void openFromFile() {
        checkOpen();
    }

    protected void openMainUtil(ChordViewController controller) {
        if (isOpen) {
            throw new IllegalCallerException("Is still open");
        }
        try {
            FXMLLoader loader = new FXMLLoader(urlMain);
            Parent root = loader.load();
            main = new Scene(root, 800, 600);
            isOpen = true;
            primary.setScene(main);
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
            primary.setScene(menu);
        }
    }
}