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
package dev.ikm.tinkar.common.util.io;

import java.io.IOException;
import java.io.InputStream;

public class CountingInputStream extends InputStream implements AutoCloseable {

    private long bytesRead = 0 ;

    private final InputStream stream ;

    public CountingInputStream(InputStream stream) {
        this.stream = stream ;
    }

    @Override
    public int read() throws IOException {
        int result = stream.read() ;
        if (result != -1) {
            bytesRead++;
        }
        return result ;
    }

    /**
     * Reads in bulk, as the stream beneath does. Without this, every bulk read above the
     * counter fell back to one read() per byte of the stream beneath: over a zip entry read
     * through the central directory, one synchronized file read per byte, which made a
     * format-3 import of SNOMED CT take four minutes instead of one
     * (IKE-Network/ike-issues#1275).
     */
    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int result = stream.read(b, off, len);
        if (result > 0) {
            bytesRead += result;
        }
        return result;
    }

    @Override
    public long skip(long n) throws IOException {
        long skipped = stream.skip(n);
        bytesRead += skipped;
        return skipped;
    }

    @Override
    public int available() throws IOException {
        return stream.available();
    }

    @Override
    public void close() throws IOException {
        super.close();
        stream.close();
    }

    public long getBytesRead() {
        return bytesRead ;
    }
}