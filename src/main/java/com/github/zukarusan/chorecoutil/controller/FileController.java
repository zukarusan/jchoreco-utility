package com.github.zukarusan.chorecoutil.controller;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.io.jvm.AudioDispatcherFactory;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.channels.FileChannel;
import java.util.LinkedList;
import java.util.List;

public class FileController implements ChordViewController {

    public static final int ALL_MODE = 0;
    public static final int ONE_MODE = 1;

    Stage stage;

    FileChooser.ExtensionFilter extFilter = new FileChooser.ExtensionFilter("Jchoreco chord (*.chord)", "*.chord");
    FileChooser fileChooser = new FileChooser();

    private File chordBuffer;
    private int currentMode;
    private Parent AllView;
    private Parent OneView;

    private static final int bufferSize = 1024 * 16;
    private ByteBuffer samples;
    private final float sampleRate;
    private /*final*/ long totalSegment;
    private /*final*/ long totalByteSample;
    private final AudioDispatcher audioDispatcher; // change to tarsos

    private final Runnable callback;

    public static class Segment {
        public float from;
        public float until;
        public String chord;
        Segment(float from, float until, String chord) {
            this.from = from; this.until = until; this.chord = chord;
        }
    }

    List<Segment> segments = new LinkedList<>();

    public FileController(Stage stage, File audio, Runnable callback) throws FileNotFoundException {
        this.stage = stage;
        this.fileChooser.getExtensionFilters().add(extFilter);
        this.currentMode = ALL_MODE;
        this.chordBuffer = null;
        this.callback = callback;

        try {
            this.audioDispatcher = AudioDispatcherFactory.fromFile(audio, bufferSize, bufferSize/2);
            this.sampleRate = audioDispatcher.getFormat().getSampleRate();
            analyzeChord();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create cache", e);
        } catch (UnsupportedAudioFileException e) {
            throw new IllegalStateException("Cannot dispatch audio", e);
        }

    }

    private FileController(Stage stage, List<Segment> savedSegment, float sampleRate, long totalSegment,
                          File chordSaved, AudioDispatcher audioDispatcher, ByteBuffer samples, Runnable callback) {
        this.stage = stage;
        this.fileChooser.getExtensionFilters().add(extFilter);
        this.segments = savedSegment;
        this.sampleRate = sampleRate;
        this.totalSegment = totalSegment;
        this.callback = callback;
        this.currentMode = ALL_MODE;
        this.chordBuffer = chordSaved;
        this.audioDispatcher = audioDispatcher;
        this.samples = samples;

        // validate
        if (sampleRate != audioDispatcher.getFormat().getSampleRate()
                && totalSegment != segments.size()) {
            analyzeChord();
        }
    }

    public static FileController openSaved(Stage stage, File savedChords, Runnable callback) throws FileNotFoundException, UnsupportedAudioFileException {
        BufferedReader reader = new BufferedReader(new FileReader(savedChords));
        String row;
        String[] header;
        List<Segment> segments = new LinkedList<>();
        float sampleRate;
        long totalSegment, totalByteSample;
        try {
            row = reader.readLine();
            header = row.split(",");
            while ((row = reader.readLine()) != null) {
                String[] parse = row.split(" ");
                if (parse.length != 3)
                    continue;
                segments.add(new Segment(Float.parseFloat(parse[0]), Float.parseFloat(parse[1]), parse[2]));
            }
            reader.close();
            sampleRate = Float.parseFloat(header[0]);
            totalSegment = Long.parseLong(header[1]);
            totalByteSample = Long.parseLong(header[2]);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read header cache file", e);
        }

        ByteBuffer buff = ByteBuffer.allocate((int) totalByteSample);
        try (RandomAccessFile fSamples = new RandomAccessFile(savedChords, "r")) {
            fSamples.readLine();
            for(int i = 0; i < totalSegment; i++) {
                fSamples.readLine();
            }
            String sHeader = "SAMPLES";
            if (sHeader.equals(fSamples.readLine())) {
                FileChannel channel = fSamples.getChannel();
                channel.read(buff);
                channel.close();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read header cache file", e);
        }

        float[] samples = buff.asFloatBuffer().array();
        AudioDispatcher audioDispatcher = AudioDispatcherFactory
                .fromFloatArray(samples, (int) sampleRate, bufferSize, bufferSize/2);

        return new FileController(stage, segments, sampleRate, totalSegment,
                savedChords, audioDispatcher, buff, callback);
    }

    public void analyzeChord() {

    }

    public void setViewMode(int mode) {
        this.currentMode = mode;
    }

    public void setAllModeView() {
    }

    @FXML
    public void close() {
        callback.run();
    }

    @FXML
    public void initialize() {

    }

    @FXML
    public void start() {

    }

    @FXML
    public void stop() {

    }

    @FXML
    public void save() {
        if (this.chordBuffer == null) {
            saveAs(null);
            return;
        }
        BufferedWriter writer;
        try {
            if (chordBuffer.exists()) {
                if (!chordBuffer.delete()) throw new IllegalStateException("File is busy");
                return;
            }
            writer = new BufferedWriter(new FileWriter(chordBuffer));
            writer.write(sampleRate + "," + totalSegment + "," + totalByteSample);
            for (Segment current : segments) {
                writer.newLine();
                writer.write(current.from+" "+current.until+" "+current.chord);
            }
            writer.newLine();
            writer.append("SAMPLES");
            writer.newLine();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try (RandomAccessFile samples = new RandomAccessFile(chordBuffer, "w")) {
            samples.seek(samples.length());
            FileChannel channel = samples.getChannel();
            channel.write(this.samples);
            channel.close();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read header cache file", e);
        }
    }

    @FXML void saveAs(Event event) {
        File savedFile = fileChooser.showSaveDialog(stage);
        if (savedFile == null) return;
        this.chordBuffer = savedFile;
        save();
    }

}
