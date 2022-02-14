package com.github.zukarusan.chorecoutil;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.io.jvm.AudioDispatcherFactory;
import com.github.zukarusan.chorecoutil.controller.FileController;
import com.github.zukarusan.chorecoutil.controller.exception.UnsupportedChordFileException;
import com.github.zukarusan.jchoreco.system.ChordProcessor;
import javafx.application.Platform;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.util.LinkedList;
import java.util.List;

public class ChordCache implements Closeable{
    private final File audio;
    private final List<FileController.Segment> SEGMENTS_CACHE;

    private final static int DEFAULT_BUFFER_WRITE_SIZE = 1024 * 16;
    private final AudioDispatcher chordDispatcher;
    private Thread chordRunner = null;
    private boolean isFinished;
    private boolean hasAudioCache = false;

    public ChordCache(File audio) {
        this(audio, DEFAULT_BUFFER_WRITE_SIZE);
    }

    public ChordCache(File audio, int bufferSize) {
        this.audio = audio;
        PipedOutputStream loBuffer;
        PipedInputStream liBuffer;
        BufferedReader chordLinker;
        SEGMENTS_CACHE = new LinkedList<>();

        try {
            chordDispatcher = AudioDispatcherFactory.fromFile(audio, bufferSize, bufferSize/2);
            loBuffer = new PipedOutputStream();
            liBuffer = new PipedInputStream(loBuffer);
            chordLinker = new BufferedReader(new InputStreamReader(liBuffer));
        } catch (IOException e) {
            throw new IllegalStateException("Error loading audio file", e);
        } catch (UnsupportedAudioFileException e) {
            throw new IllegalStateException("Unsupported audio file", e);
        }

        ChordProcessor segmentProcessor = new ChordProcessor(chordDispatcher.getFormat().getSampleRate(), bufferSize, loBuffer){
            @Override
            public boolean process(AudioEvent audioEvent) {
                double from = audioEvent.getTimeStamp();
                audioEvent.setBytesProcessing(audioEvent.getBufferSize() << 2);
                double until = audioEvent.getEndTimeStamp();
                if (super.process(audioEvent)) {
                    try {
                        String chord = chordLinker.readLine();
                        if (chord == null) return false;
                        SEGMENTS_CACHE.add(new FileController.Segment(from, until, chord));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    return false;
                }
                return true;
            }

            @Override
            public void processingFinished() {
                super.processingFinished();
                try {
                    synchronized (chordDispatcher) {
                        isFinished = true;
                        loBuffer.close();
                        liBuffer.close();
                        chordLinker.close();
                        close();
                        chordDispatcher.notify();
                    }
                } catch (IOException e) {
                    throw new IllegalStateException("Couldn't close cache", e);
                }
            }
        };

        chordDispatcher.addAudioProcessor(segmentProcessor);
        isFinished = false;
        chordRunner = new Thread(chordDispatcher, "CACHE_CHORD_RUNNER");
        chordRunner.start();
    }

    private ChordCache(File audio, List<FileController.Segment> segments) {
        this.audio = audio;
        this.SEGMENTS_CACHE = segments;
        this.chordDispatcher = null;
    }

    public List<FileController.Segment> getSegments() {
        if (!isFinished) throw new IllegalAccessError("Process not yet finished");
        return this.SEGMENTS_CACHE;
    }

    public void waitForProcessFinished() throws InterruptedException {
        if (chordDispatcher != null) {
            synchronized (chordDispatcher) {
                if (isFinished) {
                    chordRunner.interrupt();
                    return;
                }
                chordDispatcher.wait();
                chordRunner.interrupt();
            }
        }
        System.out.println("Process Finished");
    }

    public void mergeAudioWith(FileChannel chordChannel, boolean toClose) {
        try (FileChannel audioChannel = new FileInputStream(audio).getChannel()) {
            for (long count = Files.size(audio.toPath()); count > 0L;) {
                final long transferred = audioChannel.transferTo(
                        audioChannel.position(), count, chordChannel);
                audioChannel.position(audioChannel.position() + transferred);
                count -= transferred;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Error in merging audio with file channel: ", e);
        }
    }

    public static ChordCache fromSavedChord(File chordFile) throws UnsupportedChordFileException, FileNotFoundException {
        File audio = new File("temp_audio.wav");
        if (audio.exists()) if (!audio.delete()) throw new IllegalAccessError("File cache is busy");

        BufferedReader reader = new BufferedReader(new FileReader(chordFile));
        String row;
        long totalSegment;
        List<FileController.Segment> segments = new LinkedList<>();
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
                segments.add(new FileController.Segment(Float.parseFloat(parse[0]), Float.parseFloat(parse[1]), parse[2]));
            }
            reader.close();
        } catch (IOException e) {
            throw new IllegalStateException("Error reading file", e);
        } catch (IndexOutOfBoundsException e) {
            throw new IllegalStateException("Error file read", e);
        }

        // TODO: check if correctly parsed the bytes, check the byte order or check the sample sections
        try (RandomAccessFile rafChord = new RandomAccessFile(chordFile, "r")) {
            String rafRow = rafChord.readLine();
            if (!rafRow.equals("SEGMENTS")) throw new UnsupportedChordFileException();
            rafRow = rafChord.readLine();
            long rafTS = Long.parseLong(rafRow);
            for(int i = 0; i < rafTS; i++) {
                rafChord.readLine();
            }
            String aHeader = "AUDIO";
            if (aHeader.equals(rafChord.readLine())) {
                try (FileChannel rafChordChannel = rafChord.getChannel();
                     FileChannel audioChannel = new RandomAccessFile(audio, "rw").getChannel()) {
                    for (long count = Files.size(chordFile.toPath())-rafChordChannel.position(); count > 0L;) {
                        final long transferred = rafChordChannel.transferTo(
                                rafChordChannel.position(), count, audioChannel);
                        rafChordChannel.position(rafChordChannel.position() + transferred);
                        count -= transferred;
                    }
                }
            }
            else {
                throw new IllegalStateException("Cannot read float buffer");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load audio cache file", e);
        }
        ChordCache cache = new ChordCache(audio, segments);
        cache.hasAudioCache = true;
        cache.isFinished = true;
        return cache;
    }

    public File getSourceAudio() {
        return audio;
    }

    @Override
    public void close() {
        if (chordDispatcher != null) {
            chordDispatcher.stop();
            chordRunner.stop();
        }
        if (hasAudioCache){
            Platform.runLater(() -> {
                if (!audio.delete())
                    throw new IllegalAccessError("WARNING: Cannot delete audio cache, file is busy");
            });
        }
    }
}
