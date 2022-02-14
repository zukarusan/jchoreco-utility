package com.github.zukarusan.chorecoutil.controller;

import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import javax.sound.sampled.*;
import java.io.*;
import java.util.function.Consumer;

public class RecordController implements ChordViewController {
    final Stage stage;
    private final Runnable callback;
    private final Consumer<File> redirector;

    FileChooser fileChooser = new FileChooser();
    FileChooser.ExtensionFilter wavFilter = new FileChooser.ExtensionFilter("Audio WAV files (*.wav)", "*.wav");


    static class FormatProperties {
        static public final AudioFormat.Encoding ENCODING = AudioFormat.Encoding.PCM_SIGNED;
        static public final float RATE = 44100.0f;
        static public final int CHANNELS = 1;
        static public final int SAMPLE_SIZE = 16;
        static public final boolean BIG_ENDIAN = true;
    }

    public static class Recorder implements Runnable {
        private AudioInputStream audioInputStream;
        private AudioFormat format;
        public Thread thread;
        private double duration;

        public Recorder() {
            super();
        }

        public Recorder(AudioFormat format) {
            this.format = format;
        }

        public void build(AudioFormat format) {
            this.format = format;
        }

        public void start() {
            thread = new Thread(this);
            thread.setName("Capture Microphone");
            thread.start();
        }

        public void stop() {
            thread = null;
        }

        @Override
        public void run() {
            duration = 0;

            try (final ByteArrayOutputStream out = new ByteArrayOutputStream(); final TargetDataLine line = getTargetDataLineForRecord();) {

                int frameSizeInBytes = format.getFrameSize();
                int bufferLengthInFrames = line.getBufferSize() / 8;
                final int bufferLengthInBytes = bufferLengthInFrames * frameSizeInBytes;
                buildByteOutputStream(out, line, frameSizeInBytes, bufferLengthInBytes);
                this.audioInputStream = new AudioInputStream(line);
                setAudioInputStream(convertToAudioIStream(out, frameSizeInBytes));
                audioInputStream.reset();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        public void buildByteOutputStream(final ByteArrayOutputStream out, final TargetDataLine line, int frameSizeInBytes, final int bufferLengthInBytes) throws IOException {
            final byte[] data = new byte[bufferLengthInBytes];
            int numBytesRead;

            line.start();
            while (thread != null) {
                if ((numBytesRead = line.read(data, 0, bufferLengthInBytes)) == -1) {
                    break;
                }
                out.write(data, 0, numBytesRead);
            }
        }

        private void setAudioInputStream(AudioInputStream aStream) {
            this.audioInputStream = aStream;
        }

        public AudioInputStream convertToAudioIStream(final ByteArrayOutputStream out, int frameSizeInBytes) {
            byte[] audioBytes = out.toByteArray();
            ByteArrayInputStream bais = new ByteArrayInputStream(audioBytes);
            AudioInputStream audioStream = new AudioInputStream(bais, format, audioBytes.length / frameSizeInBytes);
            long milliseconds = (long) ((audioInputStream.getFrameLength() * 1000) / format.getFrameRate());
            duration = milliseconds / 1000.0;
            System.out.println("Recorded duration in seconds:" + duration);
            return audioStream;
        }

        public TargetDataLine getTargetDataLineForRecord() {
            TargetDataLine line;
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            if (!AudioSystem.isLineSupported(info)) {
                return null;
            }
            try {
                line = (TargetDataLine) AudioSystem.getLine(info);
                line.open(format, line.getBufferSize());
            } catch (final Exception ex) {
                return null;
            }
            return line;
        }

        public AudioInputStream getAudioInputStream() {
            return audioInputStream;
        }

        public AudioFormat getFormat() {
            return format;
        }

        public void setFormat(AudioFormat format) {
            this.format = format;
        }

        public Thread getThread() {
            return thread;
        }

        public double getDuration() {
            return duration;
        }
    }

    public RecordController(Stage stage, Runnable callback, Consumer<File> mainViewRedirect) {
        this.stage = stage;
        this.callback = callback;
        this.redirector = mainViewRedirect;
        fileChooser.getExtensionFilters().add(wavFilter);
    }

    public static AudioFormat buildAudioFormat() {
        AudioFormat.Encoding encoding = FormatProperties.ENCODING;
        float rate = FormatProperties.RATE;
        int channels = FormatProperties.CHANNELS;
        int sampleSize = FormatProperties.SAMPLE_SIZE;
        boolean bigEndian = FormatProperties.BIG_ENDIAN;

        return new AudioFormat(encoding, rate, sampleSize, channels, (sampleSize / 8) * channels, rate, bigEndian);
    }

    public boolean saveToFile(File file, AudioFileFormat.Type fileType, AudioInputStream audioInputStream) {
        System.out.println("Saving...");
        if (null == file || null == fileType || audioInputStream == null) {
            return false;
        }
        if (!FileController.getFileExtension(file).equals(".wav")) {
            file = new File(file.getPath()+"." + fileType.getExtension());
        }
        try {
            audioInputStream.reset();
        } catch (Exception e) {
            return false;
        }
        int i = 0;
        while (file.exists()) {
            String temp = "" + i + file.getName();
            file = new File(temp);
        }
        try {
            AudioSystem.write(audioInputStream, fileType, file);
        } catch (Exception ex) {
            return false;
        }
        System.out.println("Saved " + file.getAbsolutePath());
        return true;
    }

    @FXML Button recordButton;
    @FXML Button stopButton;

    Recorder recorder;
    public void startRecord() {
        AudioFormat format = buildAudioFormat();
        recorder = new Recorder();
        recorder.build(format);
        System.out.println("Start recording ....");
        recorder.start();
    }

    @FXML
    public void initialize() {
        recordButton.setOnAction(this::start);
        stopButton.setOnAction(this::stop);
        stopButton.setDisable(true);
    }

    @FXML
    public void start(Event event) {
        recordButton.setDisable(true);
        startRecord();
        stopButton.setDisable(false);
    }

    @FXML
    public void stop(Event event) {
        recorder.stop();
        File file = fileChooser.showSaveDialog(stage);
        if (file == null) {
            recordButton.setDisable(false);
            stopButton.setDisable(true);
            return;
        }
        if (!saveToFile(file, AudioFileFormat.Type.WAVE, recorder.getAudioInputStream())) {
            throw new IllegalAccessError("Error in recording");
        }
        recordButton.setDisable(false);
        stopButton.setDisable(true);
        redirector.accept(file);
    }

    @FXML
    public void close(Event event) {
        try{

        } finally {
            callback.run();
        }
    }
}
