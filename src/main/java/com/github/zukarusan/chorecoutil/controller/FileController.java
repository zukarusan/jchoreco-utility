package com.github.zukarusan.chorecoutil.controller;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;
import be.tarsos.dsp.io.UniversalAudioInputStream;
import be.tarsos.dsp.io.jvm.AudioDispatcherFactory;
import com.github.zukarusan.chorecoutil.ChordAnalyzerCache;
import com.github.zukarusan.chorecoutil.model.ChordsVisualComponent;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.util.LinkedList;
import java.util.List;

public class FileController implements ChordViewController {

    final Stage stage;
    private final Runnable callback;

    FileChooser.ExtensionFilter extFilter = new FileChooser.ExtensionFilter("Jchoreco chord (*.cho)", "*.cho");
    FileChooser fileChooser = new FileChooser();

    private File chordBuffer;
    private final ChordAnalyzerCache cacheChord;
    private boolean isSaved;

    private static final int bufferSize = 1024 * 16;
    private long totalSegment;
    private long totalByteSample;
    private AudioDispatcher playDispatcher;

    public static class Segment {
        public double from;
        public double until;
        public String chord;
        public Segment(double from, double until, String chord) {
            this.from = from; this.until = until; this.chord = chord;
        }
    }

    List<Segment> segments = new LinkedList<>();


    public FileController(Stage stage, File file, Runnable callback) throws UnsupportedAudioFileException, FileNotFoundException, UnsupportedEncodingException {
        this.stage = stage;
        this.callback = callback;
        this.fileChooser.getExtensionFilters().add(extFilter);
        String ext = getFileExtension(file);
        if (ext.equals(".cho")) {
            cacheChord = null;
            openFromSaved(file);
        }
        else if (ext.equals(".wav")) {
            cacheChord = new ChordAnalyzerCache(file, bufferSize);
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

    private void setProperties(List<Segment> savedSegment, long totalSegment, long totalByteSample,
                          File chordSaved, AudioDispatcher playDispatcher) {
        this.fileChooser.getExtensionFilters().add(extFilter);
        this.segments = savedSegment;
        this.totalSegment = totalSegment;
        this.totalByteSample = totalByteSample;
        this.chordBuffer = chordSaved;
        this.playDispatcher = playDispatcher;

        // validate
        if (totalSegment != segments.size()) { // TODO: more validation please
            throw new IllegalStateException("Validation error");
        }
    }

    private void openFromAudio(File audio) {
        this.isSaved = false;
        AudioDispatcher audioDispatcher;
        try {
            audioDispatcher = AudioDispatcherFactory.fromFile(audio, bufferSize, bufferSize/2);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create cache", e);
        } catch (UnsupportedAudioFileException e) {
            throw new IllegalStateException("Cannot dispatch audio", e);
        }

        synchronized (cacheChord) {
            try {
                cacheChord.waitForProcessFinished();
            } catch (InterruptedException e) {
                throw new IllegalStateException("Chord analyzing interrupted", e);
            }
        }

        List<Segment> segments = cacheChord.getSegments();
        long totalByteSample = cacheChord.getTotalBytes();
        long totalSegment = segments.size();

        setProperties(segments, totalSegment, totalByteSample, null, audioDispatcher);
    }

    private void openFromSaved(File savedChords) throws FileNotFoundException {
        isSaved = true;
        BufferedReader reader = new BufferedReader(new FileReader(savedChords));
        String row;
        String[] header;
        List<Segment> segments = new LinkedList<>();
        float sampleRate;
        int sampleSizeInBits, channels;
        boolean signed, bigEndian;
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
            sampleSizeInBits = Integer.parseInt(header[1]);
            channels = Integer.parseInt(header[2]);
            signed = Boolean.parseBoolean(header[3]);
            bigEndian = Boolean.parseBoolean(header[4]);
            totalSegment = Long.parseLong(header[5]);
            totalByteSample = Long.parseLong(header[6]);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read header cache file", e);
        } catch (IndexOutOfBoundsException e) {
            throw new IllegalStateException("Error file read", e);
        }

        FileChannel channel;
        try (RandomAccessFile fSamples = new RandomAccessFile(savedChords, "r")) {
            fSamples.readLine();
            for(int i = 0; i < totalSegment; i++) {
                fSamples.readLine();
            }
            String sHeader = "SAMPLES";
            if (sHeader.equals(fSamples.readLine())) {
                channel = fSamples.getChannel();
            }
            else {
                throw new IllegalStateException("Cannot read float buffer");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot float buffer header file", e);
        }

        // TODO: debug this, test if play correctly
        InputStream byteStream;
        AudioDispatcher audioDispatcher;
        TarsosDSPAudioFormat format = new TarsosDSPAudioFormat(sampleRate, sampleSizeInBits, channels, signed, bigEndian);
        if (channel != null) {
            byteStream = Channels.newInputStream(channel);
            UniversalAudioInputStream playStream = new UniversalAudioInputStream(byteStream, format);
            audioDispatcher = new AudioDispatcher(playStream, bufferSize, bufferSize/2);
            setProperties(segments, totalSegment, totalByteSample, savedChords, audioDispatcher);
        }
        else {
            throw new IllegalStateException("Error in loading audio dispatcher");
        }
    }

    @FXML
    public void open(Event event) {
    }

    @FXML
    public void close(Event event) {
        if (cacheChord != null) cacheChord.close();
        callback.run();
    }

    private ChordsVisualComponent chordVisual;

    @FXML
    Canvas chordCanvas;
    
    @FXML
    public void update(Event event) {
        chordVisual.drawOn(chordCanvas);
    }

    @FXML
    public void initialize(Event event) {
        chordVisual = new ChordsVisualComponent(segments);
        chordVisual.drawOn(chordCanvas);
    }

    @FXML
    public void start(Event event) {

    }

    @FXML
    public void stop(Event event) {

    }

    @FXML
    public void save(Event event) {
        if (isSaved) throw new IllegalCallerException("Chords already saved");
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
            TarsosDSPAudioFormat format = playDispatcher.getFormat();
            writer = new BufferedWriter(new FileWriter(chordBuffer));
            writer.write(
                        format.getSampleRate()+ "," +
                            format.getSampleSizeInBits() + "," +
                            format.getChannels() + "," +
                            (format.getEncoding() == TarsosDSPAudioFormat.Encoding.PCM_SIGNED) + "," +
                            format.isBigEndian() + "," +
                            totalSegment + "," +
                            totalByteSample);
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
            cacheChord.overwriteInto(channel, false);
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
