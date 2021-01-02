package org.hl7.tinkar.lombok.dto;

import org.hl7.tinkar.lombok.dto.binary.Marshalable;
import org.hl7.tinkar.lombok.dto.binary.TinkarInput;

import java.io.IOException;
import java.io.InputStream;

public class TinkarFailingInput extends TinkarInput {
    public TinkarFailingInput() {
        super(new FailingInput(), Marshalable.marshalVersion);
    }

    public TinkarFailingInput(int tinkerFormatVersion) {
        super(new FailingInput(), tinkerFormatVersion);
    }

    private static class FailingInput extends InputStream {

        @Override
        public int read() throws IOException {
            throw new IOException("Destined to fail...");
        }
    }
}
