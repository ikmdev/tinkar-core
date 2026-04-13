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

/**
 * An {@link InputStream} wrapper that counts the number of bytes read from the
 * underlying stream.
 */
public class CountingInputStream extends InputStream implements AutoCloseable {

    private long bytesRead = 0 ;

    private final InputStream stream ;

    /**
     * Wraps the given input stream so that bytes read through this stream are counted.
     *
     * @param stream the underlying input stream to wrap
     */
    public CountingInputStream(InputStream stream) {
        this.stream = stream ;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int read() throws IOException {
        int result = stream.read() ;
        if (result != -1) {
            bytesRead++;
        }
        return result ;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws IOException {
        super.close();
        stream.close();
    }

    /**
     * Returns the total number of bytes read from the underlying stream so far.
     *
     * @return the byte count
     */
    public long getBytesRead() {
        return bytesRead ;
    }
}