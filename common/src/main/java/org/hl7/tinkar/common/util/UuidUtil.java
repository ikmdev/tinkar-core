/*
 * Copyright 2020 kec.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hl7.tinkar.common.util;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.api.list.MutableList;

import java.nio.ByteBuffer;
import java.util.*;

//~--- classes ----------------------------------------------------------------

/**
 * Various UUID related utilities.
 *
 * @author darmbrust
 * @author kec
 */
public class UuidUtil {
    /**
     * Utility classes, which are collections of static members, are not meant to be instantiated.
     * Even abstract utility classes, which can be extended, should not have public constructors.
     */
    private UuidUtil() {
    }

    /**
     * Nil UUID
     * The "nil" UUID, a special case, is the UUID 00000000-0000-0000-0000-000000000000; that is, all bits set to zero.[2]
     */
    public static final UUID NIL_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");

   //~--- get methods ---------------------------------------------------------

   /**
    * Gets the uuid.
    *
    * @param string the string
    * @return the uuid
    */
   public static Optional<UUID> getUUID(String string) {
      if (string == null) {
         return Optional.empty();
      }

      if (string.length() != 36) {
         return Optional.empty();
      }

      try {
         return Optional.of(UUID.fromString(string));
      } catch (final IllegalArgumentException e) {
         return Optional.empty();
      }
   }

   /**
    * Checks if uuid.
    *
    * @param string the string
    * @return true, if uuid
    */
   public static boolean isUUID(String string) {
      return (getUUID(string).isPresent());
   }
   
   public static UUID fromList(UUID... uuids) {
       List<String> uuidStrList = new ArrayList<>();
       for (UUID uuid: uuids) {
           uuidStrList.add(uuid.toString());
       }
       uuidStrList.sort((String o1, String o2) -> o1.compareTo(o2));
       StringBuilder buff = new StringBuilder();
       for (String uuidStr: uuidStrList) {
           buff.append(uuidStr);
       }
       return UUID.nameUUIDFromBytes(buff.toString().getBytes());
   }
    public static long[] asArray(UUID uuid) {
        return new long[] {uuid.getMostSignificantBits(), uuid.getLeastSignificantBits()};
    }

    public static long[] asArray(UUID... uuids) {
       Arrays.sort(uuids);
        long[] values = new long[uuids.length * 2];
        for (int i = 0; i < uuids.length; i++) {
            values[i*2] = uuids[i].getMostSignificantBits();
            values[(i*2) + 1] = uuids[i].getLeastSignificantBits();
        }
        return values;
    }

    public static long[] asArray(ListIterable<UUID> uuidList) {
       uuidList = uuidList.toSortedList();
       int size = uuidList.size();
        long[] values = new long[size * 2];
        for (int i = 0; i < size; i++) {
            UUID uuid = uuidList.get(i);
            values[i*2] = uuid.getMostSignificantBits();
            values[(i*2) + 1] = uuid.getLeastSignificantBits();

        }
        return values;
    }

    public static UUID fromArray(long[] array) {
        return new UUID(array[0], array[1]);
    }

    public static ImmutableList<UUID> toList(long[] array) {
        MutableList<UUID> uuidList = Lists.mutable.ofInitialCapacity(array.length/2);
        for (int i = 0; i < array.length/2; i++) {
            uuidList.add(new UUID(array[i*2], array[(i*2) + 1]));
        }
        return uuidList.toImmutable();
    }

    public static UUID[] toArray(long[] array) {
        UUID[] uuidArray = new UUID[array.length/2];
        for (int i = 0; i < array.length/2; i++) {
            uuidArray[i] = new UUID(array[i*2], array[(i*2) + 1]);
        }
        return uuidArray;
    }


    /**
     * This routine adapted from com.fasterxml.uuid.impl,
     * Java Uuid Generator (JUG) which is licensed under Apache 2.
     */
    private static final void appendInt(int value, byte[] buffer, int offset)
    {
        buffer[offset++] = (byte) (value >> 24);
        buffer[offset++] = (byte) (value >> 16);
        buffer[offset++] = (byte) (value >> 8);
        buffer[offset] = (byte) value;
    }

    /**
     * This routine adapted from com.fasterxml.uuid.impl,
     * Java Uuid Generator (JUG) which is licensed under Apache 2.
     *
     * @param uuid the uid
     * @return the raw bytes
     */
    public static byte[] getRawBytes(UUID uuid) {
        long hi = uuid.getMostSignificantBits();
        long lo = uuid.getLeastSignificantBits();
        byte[] result = new byte[16];
        appendInt((int) (hi >> 32), result, 0);
        appendInt((int) hi, result, 4);
        appendInt((int) (lo >> 32), result, 8);
        appendInt((int) lo, result, 12);
        return result;
    }

    /**
     * Generates a uuid from the given {@code byteArray}.
     *
     * @param byteArray the bytes to use for generating the uuid
     * @return the generated uuid
     */
    public static UUID getUuidFromRawBytes(byte[] byteArray) {
        if (byteArray.length != 16) {
            throw new NumberFormatException("UUID must be 16 bytes");
        }

        final ByteBuffer raw = ByteBuffer.wrap(byteArray);

        return new UUID(raw.getLong(raw.position()), raw.getLong(raw.position() + 8));
    }

}

