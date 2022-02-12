package com.github.zukarusan.chorecoutil.model;

import javafx.beans.property.DoubleProperty;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.shape.Rectangle;

import java.util.concurrent.Callable;

public class PointerComponent {

    private Rectangle mask;
    final private GraphicsContext gc;
    final private DoubleProperty widthProperty;
    final private DoubleProperty heightProperty;
    final private Callable<Double> yPosition;

    public PointerComponent(Canvas canvas, Callable<Double> yBuffer){
        this.yPosition = yBuffer;
        this.widthProperty = canvas.widthProperty();
        this.heightProperty = canvas.heightProperty();
        this.gc = canvas.getGraphicsContext2D();

    }

    public void draw() {
        // mask clear
//        gc.clearRect();
    }
}
