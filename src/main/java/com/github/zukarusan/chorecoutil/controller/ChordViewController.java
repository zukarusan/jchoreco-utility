package com.github.zukarusan.chorecoutil.controller;

import javafx.event.Event;
import javafx.fxml.FXML;

public interface ChordViewController {
    @FXML
    void initialize(Event event);
    @FXML
    void start(Event event);
    @FXML
    void stop(Event event);
    @FXML
    void close(Event event);
}
