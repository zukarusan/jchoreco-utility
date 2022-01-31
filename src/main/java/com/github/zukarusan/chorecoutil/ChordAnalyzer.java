package com.github.zukarusan.chorecoutil;

import com.github.zukarusan.jchoreco.system.ChordProcessor;

import java.io.OutputStream;
import java.io.PrintStream;

public class ChordAnalyzer extends ChordProcessor{


    public ChordAnalyzer(float sampleRate, int bufferSize, PrintStream predicted) {
        super(sampleRate, bufferSize, predicted);
    }


}
