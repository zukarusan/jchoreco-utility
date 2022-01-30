package com.github.zukarusan.chorecoutil.controller;

import javafx.fxml.FXML;

public interface ChordViewController {
    @FXML
    void initialize();
    @FXML
    void start();
    @FXML
    void stop();
    @FXML
    void save();

}
