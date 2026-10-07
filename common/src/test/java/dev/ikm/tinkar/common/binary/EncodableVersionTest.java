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
package dev.ikm.tinkar.common.binary;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The version of an encoded stream ({@code IKE-Network/ike-issues#1172}): every stream is
 * written with the latest version, the first version is still read, and a version this build
 * does not know is refused. Before this change {@link Encodable#checkVersion} built its
 * exception and discarded it, so a stream of any version was decoded with the current layout.
 */
class EncodableVersionTest {

    /** A stream that holds nothing but its version. */
    private static DecoderInput streamOfVersion(int version) {
        return new DecoderInput(ByteBuffer.allocate(Integer.BYTES).putInt(version).array());
    }

    @Test
    void theSupportedVersionsAreReturned() {
        assertEquals(Encodable.FIRST_VERSION, Encodable.checkVersion(streamOfVersion(Encodable.FIRST_VERSION)));
        assertEquals(Encodable.LATEST_VERSION, Encodable.checkVersion(streamOfVersion(Encodable.LATEST_VERSION)));
    }

    @Test
    void aVersionOutsideTheSupportedRangeIsRefused() {
        for (int version : new int[]{Encodable.LATEST_VERSION + 1, Encodable.FIRST_VERSION - 1, 0, -1}) {
            EncodingExceptionUnchecked refused = assertThrows(EncodingExceptionUnchecked.class,
                    () -> Encodable.checkVersion(streamOfVersion(version)),
                    "version " + version + " must be refused, not decoded with another version's layout");
            assertTrue(refused.getMessage().contains("found: " + version), refused.getMessage());
        }
    }

    @Test
    void aStreamIsWrittenWithTheLatestVersion() {
        byte[] stream = Encodable.nullEncodable.toBytes();
        assertEquals(Encodable.LATEST_VERSION, ByteBuffer.wrap(stream).getInt(),
                "the stream begins with the version its layout was written in");
    }

    @Test
    void theLatestVersionIsTheOneThatHoldsNoNid() {
        // The version constants are compared by decoders; this pins their order.
        assertEquals(Encodable.PATH_AS_PUBLIC_ID_VERSION, Encodable.LATEST_VERSION);
        assertTrue(Encodable.FIRST_VERSION < Encodable.PATH_AS_PUBLIC_ID_VERSION);
    }
}
