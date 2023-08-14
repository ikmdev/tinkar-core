/*
 * Copyright © 2015 Integrated Knowledge Management (support@ikm.dev)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.ikm.tinkar.dto;


import dev.ikm.tinkar.dto.binary.Marshalable;
import dev.ikm.tinkar.dto.binary.TinkarOutput;

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

