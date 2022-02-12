package com.github.zukarusan.chorecoutil.controller;

import com.github.zukarusan.chorecoutil.ChordCache;
import com.github.zukarusan.chorecoutil.controller.exception.UnsupportedChordFileException;
import com.github.zukarusan.chorecoutil.model.ChordsVisualComponent;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.Pane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.*;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.util.LinkedList;
import java.util.List;


public class FileController implements ChordViewController {

    final Stage stage;
    private final Runnable callback;

    FileChooser.ExtensionFilter extFilter = new FileChooser.ExtensionFilter("Jchoreco chord (*.cho)", "*.cho");
    FileChooser fileChooser = new FileChooser();

    private File chordBuffer;
    private final ChordCache cacheChord;
    private boolean isSaved;
    private boolean isPlaying;

    private static final int bufferSize = 1024 * 16;
    private long totalSegment;
    private MediaPlayer mediaPlayer;

    public static class Segment {
        public double from;
        public double until;
        public String chord;
        public Segment(double from, double until, String chord) {
            this.from = from; this.until = until; this.chord = chord;
        }
    }

    List<Segment> segments = new LinkedList<>();

    final MenuController.LoadingWaiter initLoading;
    public FileController(Stage stage, File file, Runnable callback, MenuController.LoadingWaiter init) throws UnsupportedAudioFileException, FileNotFoundException, UnsupportedEncodingException, UnsupportedChordFileException {
        this.stage = stage;
        this.callback = callback;
        this.fileChooser.getExtensionFilters().add(extFilter);
        this.initLoading = init;
        String ext = getFileExtension(file);
        if (ext.equals(".cho")) {
            cacheChord = ChordCache.fromSavedChord(file);
            openFromSaved(file);
        }
        else if (ext.equals(".wav")) {
            cacheChord = new ChordCache(file, bufferSize);
            openFromAudio(file);
        }
        else {
            throw new UnsupportedEncodingException();
        }
    }


    private static String getFileExtension(File file) {
        String name = file.getName();
        int lastIndexOf = name.lastIndexOf(".");
        if (lastIndexOf == -1) {
            return ""; // empty extension
        }
        return name.substring(lastIndexOf);
    }

    private void setProperties(List<Segment> savedSegment, long totalSegment, File chordSaved, MediaPlayer mediaPlayer) {
        this.fileChooser.getExtensionFilters().add(extFilter);
        this.segments = savedSegment;
        this.totalSegment = totalSegment;
        this.chordBuffer = chordSaved;
        this.mediaPlayer = mediaPlayer;

        // validate
        if (totalSegment != segments.size()) { // TODO: more validation please
            throw new IllegalStateException("Validation error");
        }
    }

    private void openFromAudio(File audio) {
        this.isSaved = false;

        synchronized (cacheChord) {
            try {
                cacheChord.waitForProcessFinished();
            } catch (InterruptedException e) {
                throw new IllegalStateException("Chord analyzing interrupted", e);
            }
        }
        List<Segment> segments = cacheChord.getSegments();
        long totalSegment = segments.size();

        String path = audio.toURI().toString();
        MediaPlayer player = new MediaPlayer(new Media(path));

        setProperties(segments, totalSegment, null, player);
    }

    private void openFromSaved(File savedChords) throws FileNotFoundException, UnsupportedChordFileException {
        isSaved = true;
        BufferedReader reader = new BufferedReader(new FileReader(savedChords));
        String row;
        long totalSegment;
        List<Segment> segments = new LinkedList<>();
        try {
            row = reader.readLine();
            if (!row.equals("SEGMENTS")) throw new UnsupportedChordFileException("Cannot read segments");
            row = reader.readLine();
            totalSegment = Long.parseLong(row);
            for (int i = 0; i < totalSegment; i++) {
                row = reader.readLine();
                String[] parse = row.split(" ");
                if (parse.length != 3)
                    continue;
                segments.add(new Segment(Float.parseFloat(parse[0]), Float.parseFloat(parse[1]), parse[2]));
            }
            reader.close();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read header cache file", e);
        } catch (IndexOutOfBoundsException e) {
            throw new IllegalStateException("Error file read", e);
        }
        MediaPlayer player;
        if (cacheChord != null) {
            String path = cacheChord.getSourceAudio().toURI().toString();
            player = new MediaPlayer(new Media(path));
        } else throw new FileNotFoundException("Cannot find audio in chord file "+ savedChords.getName());

        setProperties(segments, totalSegment, savedChords, player);
    }

    @FXML
    public void open(Event event) {
    }

    @FXML
    public void close(Event event) {
        if (isPlaying) mediaPlayer.stop();
        mediaPlayer.dispose();
        cacheChord.close();
        callback.run();
    }

    private ChordsVisualComponent chordVisual;

    @FXML Canvas chordCanvas;
    @FXML MenuItem close;
    @FXML Pane canvasPane;
    @FXML Button playButton;
    @FXML MenuItem saveMenuItem;

    @FXML
    public void initialize() {
        chordVisual = new ChordsVisualComponent(chordCanvas, segments);
        chordCanvas.widthProperty().bind(canvasPane.widthProperty());
        chordCanvas.heightProperty().bind(canvasPane.heightProperty());
        chordCanvas.widthProperty().addListener(evt -> chordVisual.draw());
        chordCanvas.heightProperty().addListener(evt -> chordVisual.draw());

        new Thread(() -> {
            initLoading.run();
            Platform.runLater(() -> chordVisual.draw());
        }).start();

        close.setOnAction(this::close);
        playButton.setOnAction(this::start);
        saveMenuItem.setOnAction(this::save);
    }


    @FXML
    public void start(Event event) {
        isPlaying = true;
        mediaPlayer.play();
    }

    @FXML
    public void stop(Event event) {

    }

    @FXML
    public void save(Event event) {
        if (isSaved) {
            System.out.println("Chords already saved");
            return;
        }
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
            writer.write("SEGMENTS");
            writer.newLine();
            writer.write(String.valueOf(totalSegment));
            for (Segment current : segments) {
                writer.newLine();
                writer.write(current.from+" "+current.until+" "+current.chord);
            }
            writer.newLine();
            writer.append("AUDIO");
            writer.newLine();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try (RandomAccessFile samples = new RandomAccessFile(chordBuffer, "rw")) {
            samples.seek(samples.length());
            FileChannel channel = samples.getChannel();
            cacheChord.mergeAudioWith(channel, false);
            channel.close();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read header cache file", e);
        }
        isSaved = true;
    }

    @FXML
    public void saveAs(Event event) {
        File savedFile = fileChooser.showSaveDialog(stage);
        if (savedFile == null) return;
        this.chordBuffer = savedFile;
        isSaved = false;
        save(null);
    }

}
