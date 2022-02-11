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
    private final AudioDispatcher dispatcher;
    private final List<FileController.Segment> SEGMENTS_CACHE;
    private final Thread runner;
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
            dispatcher = AudioDispatcherFactory.fromFile(audio, bufferSize, bufferSize/2);
            loBuffer = new PipedOutputStream();
            liBuffer = new PipedInputStream(loBuffer);
            chordLinker = new BufferedReader(new InputStreamReader(liBuffer));
            byteCache = new RandomAccessFile(BYTE_CACHE, "rw");
        } catch (IOException e) {
            throw new IllegalStateException("Error loading audio file", e);
        } catch (UnsupportedAudioFileException e) {
            throw new IllegalStateException("Unsupported audio file", e);
        }

        ChordProcessor segmentProcessor = new ChordProcessor(dispatcher.getFormat().getSampleRate(), bufferSize, loBuffer){
            @Override
            synchronized public boolean process(AudioEvent audioEvent) {
                if (super.process(audioEvent)) {
                    try {
                        String chord = chordLinker.readLine();
                        if (chord == null) return false;
                        double from = audioEvent.getTimeStamp();
                        double until = audioEvent.getEndTimeStamp();
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
            synchronized public void processingFinished() {
                super.processingFinished();
                try {
                    loBuffer.close();
                    liBuffer.close();
                    chordLinker.close();
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
                    // TODO: Check if a sample consists of 1 float value. so is it 4 byte or more?
                    totalBytes += (long) audioEvent.getBufferSize() << 2;
//                    if (audioEvent.getByteBuffer().length >> 2 == bufferSize) {
//                        System.out.println("Check buffer");
//                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    return false;
                }
                return true;
            }

            @Override
            public void processingFinished() {
                try {
                    synchronized (dispatcher) {
                        isFinished = true;
                        byteCache.close();
                        segmentProcessor.close();
                        dispatcher.notify();
                    }
//                    dispatcher.stop();
//                    runner.stop();
                } catch (IOException e) {
                    throw new IllegalStateException("Couldn't close cache", e);
                }
            }
        };

        dispatcher.addAudioProcessor(segmentProcessor);
        dispatcher.addAudioProcessor(byteProcessor);

        isFinished = false;
        runner = new Thread(dispatcher, "CACHE_RUNNER");
        runner.start();
    }

    public List<FileController.Segment> getSegments() {
        if (!isFinished) throw new IllegalAccessError("Process not yet finished");
        return this.SEGMENTS_CACHE;
    }

    public void waitForProcessFinished() throws InterruptedException {
        if (isFinished) return;
//        runner.wait();
        synchronized (dispatcher) {
            dispatcher.wait();
            runner.interrupt();
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
        dispatcher.stop();
        runner.stop();
        try {
            byteCache.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (!BYTE_CACHE.delete()) throw new IllegalAccessError("File is busy");
    }
}
