module com.github.zukarusan.chorecoutil {
    requires javafx.fxml;
    requires javafx.controls;
    requires javafx.media;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;
    requires java.desktop;
    requires TarsosDSP;
    requires jchoreco;
    requires jlayer;

    exports com.github.zukarusan.chorecoutil.controller;
    exports com.github.zukarusan.chorecoutil.controller.exception;
    exports com.github.zukarusan.chorecoutil.model;
    exports com.github.zukarusan.chorecoutil;
    opens com.github.zukarusan.chorecoutil.controller to javafx.fxml;
    opens com.github.zukarusan.chorecoutil to jchoreco;
}