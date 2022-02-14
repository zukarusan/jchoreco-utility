package com.github.zukarusan.chorecoutil.controller;

import com.github.zukarusan.chorecoutil.ChordCache;
import com.github.zukarusan.chorecoutil.RecentFilesFactory;
import com.github.zukarusan.chorecoutil.controller.exception.UnsupportedChordFileException;
import com.github.zukarusan.chorecoutil.component.ChordsVisualComponent;

import com.github.zukarusan.chorecoutil.component.PointerComponent;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.Pane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.*;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;


public class FileController implements ChordViewController {

    final Stage stage;
    private final Runnable callback;

    FileChooser.ExtensionFilter extFilter = new FileChooser.ExtensionFilter("Jchoreco chord (*.cho)", "*.cho");
    FileChooser fileChooser = new FileChooser();

    private File chordBuffer;
    private final ChordCache cacheChord;
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
        RecentFilesFactory.prependRecentFile(file);

        waiter = new MenuController.LoadingWaiter();
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


    public static String getFileExtension(File file) {
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

        if (totalSegment != segments.size()) {
            throw new IllegalStateException("Validation error");
        }
    }

    private Media media;
    private Thread playerThread;
    private final MenuController.LoadingWaiter waiter;

    private void openFromAudio(File audio) {
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
        media = new Media(path);
        final MediaPlayer[] player = new MediaPlayer[1];
        playerThread = new Thread(() -> {
            player[0] = new MediaPlayer(media);
            waiter.setFinished();
        });
        playerThread.start();
        waiter.run();

        setProperties(segments, totalSegment, null, player[0]);
    }

    private void openFromSaved(File savedChords) throws FileNotFoundException, UnsupportedChordFileException {

        final MediaPlayer[] player = new MediaPlayer[1];
        if (cacheChord != null) {
            String path = cacheChord.getSourceAudio().toURI().toString();
            media = new Media(path);
            playerThread = new Thread(() -> {
                player[0] = new MediaPlayer(media);
                waiter.setFinished();
            });
            playerThread.start();
            waiter.run();
        } else throw new FileNotFoundException("Cannot find audio in chord file "+ savedChords.getName());

        List<Segment> segments = cacheChord.getSegments();
        setProperties(segments, segments.size(), savedChords, player[0]);
    }

    @FXML
    public void close(Event event) {
        try {
            mediaPlayer.stop();
            playerThread.interrupt();
            mediaPlayer.dispose();
            cacheChord.close();
        } finally {
            callback.run();
        }
    }

    private ChordsVisualComponent chordVisual;
    private PointerComponent pointer;

    @FXML Canvas chordCanvas;
    @FXML MenuItem close;
    @FXML Pane canvasPane;
    @FXML Button playButton;
    @FXML Button stopButton;
    @FXML MenuItem saveMenuItem;
    Text chordLabel;
    Text nextLabel;
    Text nextChordLabel;

    @FXML
    public void initialize() {
        chordLabel = new Text();
        nextLabel = new Text();
        nextChordLabel = new Text();
        chordLabel.setFont(Font.font("Lemon Regular"));
        chordLabel.setStyle("-fx-font-size: 30;");
        chordLabel.setStroke(Color.AQUA);
        chordLabel.setStrokeWidth(1);
        nextLabel.setFont(Font.font("Lemon Regular"));
        nextLabel.setStyle("-fx-font-size: 25; -fx-fill: #d13328");
        nextChordLabel.setFont(Font.font("Lemon Regular"));
        nextChordLabel.setStyle("-fx-font-size: 20;");
        nextChordLabel.setStroke(Color.BLUEVIOLET);
        nextChordLabel.setStrokeWidth(1);canvasPane.getChildren().add(chordLabel);
        canvasPane.getChildren().add(nextLabel);
        canvasPane.getChildren().add(nextChordLabel);

        chordVisual = new ChordsVisualComponent(chordCanvas, segments);
        filSeg = chordVisual.getFilteredSegments().toArray(new Segment[0]);
        segSize = filSeg.length;
        overlapDur = chordVisual.getOverlapDur();
        charts = chordVisual.getChordColorMap();

        chordCanvas.widthProperty().bind(canvasPane.widthProperty());
        chordCanvas.heightProperty().bind(canvasPane.heightProperty());
        chordCanvas.widthProperty().addListener(evt -> {
            chordVisual.draw();
            chordLabel.setLayoutX((chordCanvas.getWidth()/2) - chordLabel.prefWidth(-1)/2);
            nextChordLabel.setLayoutX((chordCanvas.getWidth()/2) - nextChordLabel.prefWidth(-1)/2 + chordLabel.prefWidth(-1)*2.5);
            nextLabel.setLayoutX(nextChordLabel.getLayoutX()-nextLabel.prefWidth(-1));
        });
        chordCanvas.heightProperty().addListener(evt -> {
            chordVisual.draw();
            chordLabel.setLayoutY((chordCanvas.getHeight()/6) - chordLabel.prefHeight(-1)/2);
            nextChordLabel.setLayoutY((chordCanvas.getHeight()/6) - nextChordLabel.prefHeight(-1)/2);
            nextLabel.setLayoutY(nextChordLabel.getLayoutY());
        });

        close.setOnAction(this::close);
        playButton.setOnAction(this::start);
        stopButton.setOnAction(this::stop);
        saveMenuItem.setOnAction(this::saveAs);

        mediaPlayer.setOnEndOfMedia(() -> stop(null));
        mediaPlayer.setOnPlaying(() -> playButton.setDisable(true));

        pointer = new PointerComponent(chordCanvas, chordVisual.getRectYEnd);
        mediaPlayer.currentTimeProperty().addListener(evt -> {
            updateLabelChord();
            chordLabel.setText(currentSegment.chord);
            if (nextSegment != null) {
                nextLabel.setText("Next: ");
                nextChordLabel.setText(nextSegment.chord);
                nextChordLabel.setFill(charts.get(nextSegment.chord));
            } else {
                nextChordLabel.setText("");
                nextLabel.setText("");
            }
            chordLabel.setFill(charts.get(currentSegment.chord));
            chordLabel.setLayoutX((chordCanvas.getWidth()/2) - (chordLabel.prefWidth(-1)/2));
            nextChordLabel.setLayoutX((chordCanvas.getWidth() * 3/4) - nextChordLabel.prefWidth(-1)/2);
            nextLabel.setLayoutX(nextChordLabel.getLayoutX()-nextLabel.prefWidth(-1));
            pointer.setProgress(mediaPlayer.getCurrentTime().toMillis()/totalTime);
            pointer.updateTransX();
        });

        new Thread(() -> {
            initLoading.run();
            Platform.runLater(() -> {
                chordVisual.draw();
                pointer.linkContainer(canvasPane);
                pointer.updateX();
                chordLabel.setText("Press Play to show chord here");
                chordLabel.setLayoutX((chordCanvas.getWidth()/2) - (chordLabel.prefWidth(-1)/2));
                chordLabel.setLayoutY((chordCanvas.getHeight()/6) - (chordLabel.prefHeight(-1)/2));
                nextChordLabel.setLayoutX((chordCanvas.getWidth()/2) - nextChordLabel.prefWidth(-1)/2 + chordLabel.prefWidth(-1)*2.5);
                nextChordLabel.setLayoutY((chordCanvas.getHeight()/6) - nextChordLabel.prefHeight(-1)/2);
                nextLabel.setLayoutX(nextChordLabel.getLayoutX()-nextLabel.prefWidth(-1));
                nextLabel.setLayoutY(nextChordLabel.getLayoutY());
            });
        }).start();
    }

    Segment[] filSeg;
    double totalTime;
    double overlapDur;
    private int iter;
    private int segSize;
    private Segment currentSegment;
    private Segment nextSegment;
    private HashMap<String, Color> charts;

    public void updateLabelChord() {
        if (nextSegment != null) {
            while (mediaPlayer.getCurrentTime().toSeconds() >= nextSegment.from) {
                if (iter < segSize) {
                    currentSegment = nextSegment;
                    nextSegment = filSeg[iter++];
                } else {
                    currentSegment = nextSegment;
                    nextSegment = null;
                    break;
                }
            }
        }
    }

    @FXML
    public void start(Event event) {
        if (isPlaying) return;
        isPlaying = true;
        iter = 0;
        currentSegment = filSeg[iter++];
        if (iter < segSize) nextSegment = filSeg[iter++]; else nextSegment = null;
        mediaPlayer.play();
        totalTime = mediaPlayer.getTotalDuration().toMillis();
    }

    @FXML
    public void stop(Event event) {
        if (!isPlaying) return;
        isPlaying = false;
        mediaPlayer.seek(Duration.millis(0));
        mediaPlayer.stop();
        playButton.setDisable(false);
    }

    public void save() {
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
    }

    @FXML
    public void saveAs(Event event) {
        File savedFile = fileChooser.showSaveDialog(stage);
        if (savedFile == null) return;
        this.chordBuffer = savedFile;
        save();
    }

}
