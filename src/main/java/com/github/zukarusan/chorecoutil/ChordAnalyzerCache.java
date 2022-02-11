package com.github.zukarusan.chorecoutil;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.jvm.AudioDispatcherFactory;
import com.github.zukarusan.chorecoutil.controller.FileController;
import com.github.zukarusan.jchoreco.system.ChordProcessor;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.LinkedList;
import java.util.List;

public class ChordAnalyzerCache implements Closeable{
    File BYTE_CACHE;
    private final RandomAccessFile byteCache;

    private final static int _DEFAULT_BUFFER_SIZE_ = 1024 * 16;
    private final AudioDispatcher chordDispatcher, byteDispatcher;
    private final List<FileController.Segment> SEGMENTS_CACHE;
    private final Thread chordRunner, byteRunner;
    private long totalBytes;
    private boolean isFinished;

    public ChordAnalyzerCache(File audio) {
        this(audio, _DEFAULT_BUFFER_SIZE_);
    }

    public ChordAnalyzerCache(File audio, int bufferSize) {
        BYTE_CACHE = new File(".cache_byte");

        if (BYTE_CACHE.exists()) {
            if (!BYTE_CACHE.delete()) throw new IllegalStateException("File is busy");
        }

        PipedOutputStream loBuffer;
        PipedInputStream liBuffer;
        BufferedReader chordLinker;
        SEGMENTS_CACHE = new LinkedList<>();

        try {
            chordDispatcher = AudioDispatcherFactory.fromFile(audio, bufferSize, bufferSize/2);
            byteDispatcher = AudioDispatcherFactory.fromFile(audio, bufferSize, 0);
            loBuffer = new PipedOutputStream();
            liBuffer = new PipedInputStream(loBuffer);
            chordLinker = new BufferedReader(new InputStreamReader(liBuffer));
            byteCache = new RandomAccessFile(BYTE_CACHE, "rw");
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

        totalBytes = 0;
        AudioProcessor byteProcessor = new AudioProcessor() {
            @Override
            public boolean process(AudioEvent audioEvent) {
                try {
                    byteCache.write(audioEvent.getByteBuffer());
                    totalBytes += (long) audioEvent.getBufferSize() << 2;
                } catch (IOException e) {
                    e.printStackTrace();
                    return false;
                }
                return true;
            }

            @Override
            public void processingFinished() {
                synchronized (byteDispatcher) {
                    isFinished = true;
                    try {
                        byteCache.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    byteDispatcher.notify();
                }
            }
        };

        chordDispatcher.addAudioProcessor(segmentProcessor);
        byteDispatcher.addAudioProcessor(byteProcessor);

        isFinished = false;
        chordRunner = new Thread(chordDispatcher, "CACHE_CHORD_RUNNER");
        byteRunner = new Thread(byteDispatcher, "CACHE_BYTE_RUNNER");
        chordRunner.start();
        byteRunner.start();
    }

    public List<FileController.Segment> getSegments() {
        if (!isFinished) throw new IllegalAccessError("Process not yet finished");
        return this.SEGMENTS_CACHE;
    }

    public void waitForProcessFinished() throws InterruptedException {
//        runner.wait();
        synchronized (chordDispatcher) {
            if (isFinished) {
                chordRunner.interrupt();
                return;
            }
            chordDispatcher.wait();
            chordRunner.interrupt();
        }
        synchronized (byteDispatcher) {
            if (isFinished) {
                byteRunner.interrupt();
                return;
            }
            byteDispatcher.wait();
            byteRunner.interrupt();
        }
        System.out.println("Process Finished");
    }

    public void overwriteInto(FileChannel channel, boolean toClose) throws IOException {
        if (!isFinished) throw new IllegalAccessError("Process not yet finished");
        channel.truncate(channel.position());
        int len = _DEFAULT_BUFFER_SIZE_ << 2;  // check if this float sample
        byte[] buffer = new byte[len];
        ByteBuffer wrapped = ByteBuffer.wrap(buffer);
        byteCache.seek(0);
        FileChannel cacheChannel = byteCache.getChannel();

        int readTotal;
        long readSum = 0;
        while((readTotal = cacheChannel.read(wrapped)) != -1) {
            if (readTotal == len)
                channel.write(wrapped);
            else
                channel.write(ByteBuffer.wrap(buffer, 0, readTotal));
            wrapped.clear();
            readSum += readTotal;
        }

        cacheChannel.close();
        byteCache.seek(0);
        if (toClose) channel.close();
        wrapped.clear();
    }

    public long getTotalBytes() {
        if (!isFinished) throw new IllegalAccessError("Process not yet finished");
        return totalBytes;
    }

    @Override
    public void close() {
        byteDispatcher.stop();
        chordDispatcher.stop();
        chordRunner.stop();
        byteRunner.stop();
        try {
            byteCache.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (!BYTE_CACHE.delete()) throw new IllegalAccessError("File is busy");
    }
}
