package com.github.zukarusan.chorecoutil;

import com.github.zukarusan.chorecoutil.controller.MenuController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;
import java.net.URL;
import java.util.List;

public class MainApplication extends Application {

    @Override
    public void start(Stage stage) {
        stage.setResizable(false);
        MenuController menu = new MenuController(stage, this.getClass());
        menu.showWithRecentFiles();
    }

    public static void main(String[] args) {
        launch();
    }
}