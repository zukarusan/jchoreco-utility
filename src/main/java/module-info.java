module com.github.zukarusan.chorecoutil {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;
    requires TarsosDSP;
    requires jlayer;
    requires JTransforms;
    requires org.tensorflow.core.api;
    requires org.tensorflow.core.platform;
    requires org.tensorflow.ndarray;
    requires java.desktop;
    requires jchoreco;

    exports com.github.zukarusan.chorecoutil.controller;
    exports com.github.zukarusan.chorecoutil.model;
    exports com.github.zukarusan.chorecoutil;
    opens com.github.zukarusan.chorecoutil.controller to javafx.fxml;
}