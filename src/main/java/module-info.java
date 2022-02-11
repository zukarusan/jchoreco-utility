module com.github.zukarusan.chorecoutil {
    requires javafx.fxml;
    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;
    requires java.desktop;
    requires TarsosDSP;
    requires jchoreco;

    exports com.github.zukarusan.chorecoutil.controller;
    exports com.github.zukarusan.chorecoutil.model;
    exports com.github.zukarusan.chorecoutil;
    opens com.github.zukarusan.chorecoutil.controller to javafx.fxml;
    opens com.github.zukarusan.chorecoutil to jchoreco;
}