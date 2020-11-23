package org.hl7.tinkar.json;

import org.hl7.tinkar.binary.TinkarInput;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class TinkarFailingInput extends TinkarInput {
    public TinkarFailingInput() {
        super(new FailingInput());
    }

    private static class FailingInput extends InputStream {

        @Override
        public int read() throws IOException {
            throw new IOException("Destined to fail...");
        }
    }
}
