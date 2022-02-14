package com.github.zukarusan.chorecoutil;

import com.github.zukarusan.chorecoutil.controller.MenuController;
import javafx.application.Application;
import javafx.stage.Stage;

public class MainApplication extends Application {

    @Override
    public void start(Stage stage) {
        // TODO: different stage for viewer and menu to apply (un)decorated settings
//        stage.setResizable(false);
        MenuController menu = new MenuController(stage, this.getClass());
        menu.showWithRecentFiles();
    }

    public static void main(String[] args) {
        launch();
    }
}