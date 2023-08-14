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
import dev.ikm.tinkar.dto.binary.TinkarInput;

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
