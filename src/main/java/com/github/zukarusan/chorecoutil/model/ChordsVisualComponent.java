package com.github.zukarusan.chorecoutil.model;

import com.github.zukarusan.chorecoutil.controller.FileController;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;

public class ChordsVisualComponent {
    public static final int[] color_value = {0, 255/2, 255};
    private final List<FileController.Segment> filteredSegments;
    private final HashMap<String, Color> charts;
    private final double durationSeconds;
    private final double overlapDur;
    private final Canvas canvas;
    private final GraphicsContext gc;

    public static Color getColorBase3(int value) {
        assert value >= 0;
        int[] rgb = new int[3];
        for (int i = 0; i < 3; i++, value/=3) {
            rgb[i] = color_value[value%3];
        }
        return Color.rgb(rgb[0], rgb[1], rgb[2]);
    }

    public ChordsVisualComponent(Canvas canvas, List<FileController.Segment> segments) {
        this.charts = new HashMap<>();
        this.gc = canvas.getGraphicsContext2D();
        this.canvas = canvas;
        filteredSegments = new LinkedList<>();

        durationSeconds = segments.get(segments.size()-1).until;
        FileController.Segment cur = segments.get(0);
        overlapDur = (cur.until - cur.from) / 2.0;

        double prev_until = cur.until;
        int i = 0;
        int c = 0;
//        double assert_totalDur_check = 0;
        for (FileController.Segment segment : segments) {
            if (!cur.chord.equals(segment.chord)) {
//                assert_totalDur_check += prev_until - cur.from - overlapDur;
                filteredSegments.add(new FileController.Segment(cur.from, prev_until, cur.chord));
                cur = segment;
                if (!charts.containsKey(cur.chord)) charts.put(cur.chord, getColorBase3(c++));
            } else if (i == segments.size()-1) {
//                assert_totalDur_check += segment.until - cur.from - overlapDur;
                filteredSegments.add(new FileController.Segment(cur.from, prev_until, cur.chord));
                if (!charts.containsKey(cur.chord)) charts.put(cur.chord, getColorBase3(c));
                break;
            }
            prev_until = segment.until;
            i++;
        }
//        if (assert_totalDur_check == durationSeconds) {
//            System.out.println("TEST DURATIONS");
//        }

        // TODO: Overlap duration still not precise enough, subtract each segment overlap and
        //          save into filtered segments new data structure
//        assert filteredSegments.get(filteredSegments.size()-1).until == durationSeconds;
    }

    private List<Rectangle> rect_buffer = null;
    double rect_height = -1;
    double width = -1;
    double height = -1;
    double y_rect;
    double overlapLen;
    public void draw() {
        if ((height != canvas.getHeight() || width != canvas.getWidth()) || rect_buffer == null) {
            rect_height = canvas.getHeight()/3;
            width = canvas.getWidth();
            y_rect = (canvas.getHeight()/2)-(rect_height/2);
            overlapLen = overlapDur * getPixelPerSecond(width);
            rect_buffer = getRectangles(rect_height, width);
        }

        // clear canvas
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        // border fill
        double x = 0;
        gc.setFill(Color.BLACK);
        for (Rectangle rect : rect_buffer) {
            gc.fillRect(x+2, y_rect+2, rect.getWidth()-1, rect_height-2);
            x += rect.getWidth()-overlapLen;
        }
        // chord charts, rectangle fill
        x = 0;
        for (Rectangle rect : rect_buffer) {
            gc.setFill(rect.getFill());
            gc.fillRect(x, y_rect, rect.getWidth(), rect_height);
            x += rect.getWidth();
        }
    }

    public final Callable<Double> getRectYEnd = new Callable<>() {
        @Override
        public Double call() {
            synchronized (canvas) {
                return y_rect+rect_height;
            }
        }
    };

    public double getDurationSeconds() {return durationSeconds;}

    public List<FileController.Segment> getFilteredSegments() {
        return this.filteredSegments;
    }

    public HashMap<String, Color> getChordColorMap() {
        return charts;
    }

    public double getPixelPerSecond(double width) {
        return width/durationSeconds;
    }

    public List<Rectangle> getRectangles(double height, double container_width) {
        LinkedList<Rectangle> chordVis = new LinkedList<>();
        double pps = getPixelPerSecond(container_width);
        for (FileController.Segment segment : filteredSegments) {
            double dur = segment.until - segment.from;
            Rectangle rect = new Rectangle(pps * dur, height, charts.get(segment.chord));
            rect.setStroke(Color.BLACK);
            rect.setStrokeWidth(2);
            chordVis.add(rect);
        }
        return chordVis;
    }

}
