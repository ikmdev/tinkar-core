package org.hl7.tinkar.dto;


import org.hl7.tinkar.dto.binary.Marshalable;
import org.hl7.tinkar.dto.binary.TinkarOutput;

import java.io.IOException;
import java.io.OutputStream;

public class TinkarFailingOutput extends TinkarOutput {

    public TinkarFailingOutput() {
        super(new FailingOutput(), Marshalable.marshalVersion);
    }

    public TinkarFailingOutput(int tinkerFormatVersion) {
        super(new FailingOutput(), tinkerFormatVersion);
    }
    private static class FailingOutput extends OutputStream {

        @Override
        public void write(int b) throws IOException {
            throw new IOException("Destined to fail...");
        }
    }
}

