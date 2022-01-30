module com.github.zukarusan.chorecoutil {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;

    opens com.github.zukarusan.chorecoutil to javafx.fxml;
    exports com.github.zukarusan.chorecoutil;
    exports com.github.zukarusan.chorecoutil.controller;
    opens com.github.zukarusan.chorecoutil.controller to javafx.fxml;
}