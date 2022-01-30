package com.github.zukarusan.chorecoutil.controller;

import javafx.fxml.FXML;
import javafx.scene.Parent;

import java.io.*;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;

public class FileController {

    public static final int ALL_MODE = 0;
    public static final int ONE_MODE = 1;

    private final File chordBuffer;
    private int currentMode;
    private Parent AllView;
    private Parent OneView;

    private final float sampleRate;
    private final int bufferSize;
    private final long totalSegment;
    private final InputStream audioStream; // change to tarsos

    static class Segment {
        public float from;
        public float until;
        public String chord;
        Segment(float from, float until, String chord) {
            this.from = from; this.until = until; this.chord = chord;
        }

    }

    List<Segment> segments = new LinkedList<>();

    public FileController(File chordCache) throws FileNotFoundException {
        this.currentMode = ALL_MODE;
        this.chordBuffer = chordCache;

        BufferedReader reader = new BufferedReader(new FileReader(chordBuffer));
        String row;
        String[] header;
        try {
            row = reader.readLine();
            header = row.split(" ");
            while ((row = reader.readLine()) != null) {
                String[] parse = row.split(" ");
                segments.add(new Segment (Float.parseFloat(parse[0]), Float.parseFloat(parse[1]), parse[2]));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read header cache file", e);
        }
        this.sampleRate = Float.parseFloat(header[0]);
        this.bufferSize = Integer.parseInt(header[1]);
        this.totalSegment = Long.parseLong(header[2]);
        String audioPath = header[3];

        //
        audioStream = new FileInputStream(chordBuffer); // DUMMY

    }

    public void setViewMode(int mode) {
        this.currentMode = mode;
    }

    public void setAllModeView() {

    }

    @FXML
    public void initialize() {

    }

    @FXML
    protected void start() {

    }

    @FXML
    protected void stop() {

    }

}
