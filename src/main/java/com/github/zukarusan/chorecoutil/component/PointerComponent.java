package com.github.zukarusan.chorecoutil.component;

import javafx.beans.property.DoubleProperty;
import javafx.scene.canvas.Canvas;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

public class PointerComponent {

    private final Rectangle pointer;
    final private DoubleProperty canvasWidth;
    final private DoubleProperty canvasHeight;
    final private ChordsVisualComponent.BufferedDouble yPosition;

    double w_buffer;
    double h_buffer;

    double x_buffer;

    final double w_ratio = 1.0/250;
    final double h_ratio = 1.0/30;

    double progress;

    public PointerComponent(Canvas canvas, ChordsVisualComponent.BufferedDouble yBuffer){
        this.progress = 0;
        this.yPosition = yBuffer;
        this.canvasWidth = canvas.widthProperty();
        this.canvasHeight = canvas.heightProperty();

        pointer = new Rectangle(canvasWidth.getValue() * w_ratio, canvasHeight.getValue() * h_ratio);
        pointer.setStroke(Color.AQUA);
        pointer.setFill(Color.DARKRED);
        updateX();
        updateY();

        canvasWidth.addListener(evt -> updateX());
        canvasHeight.addListener(evt -> updateY());
    }

    public void updateX() {
        w_buffer = pointer.getWidth();
        pointer.setWidth(canvasWidth.getValue() * w_ratio);
        pointer.setX(-(pointer.getWidth()/2));
        updateTransX();
    }

    public void updateY() {
        h_buffer = pointer.getHeight();
        pointer.setHeight(canvasHeight.getValue() * h_ratio);
        pointer.setY(yPosition.get() + 3);
    }

    public void updateTransX() {
        x_buffer = pointer.getX();
        pointer.setTranslateX(progress * canvasWidth.getValue());
    }

    public void setProgress(double progress) {
        this.progress = progress;
    }

    public void linkContainer(Pane pane) {
        pane.getChildren().add(pointer);
    }


}
