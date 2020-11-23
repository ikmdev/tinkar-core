package org.hl7.tinkar.json;

import org.hl7.tinkar.binary.TinkarOutput;

import java.io.IOException;
import java.io.OutputStream;

public class TinkarFailingOutput extends TinkarOutput {
    public TinkarFailingOutput() {
        super(new FailingOutput());
    }

    private static class FailingOutput extends OutputStream {

        @Override
        public void write(int b) throws IOException {
            throw new IOException("Destined to fail...");
        }
    }
}

