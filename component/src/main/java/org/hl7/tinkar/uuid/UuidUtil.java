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
package org.hl7.tinkar.uuid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

//~--- classes ----------------------------------------------------------------

/**
 * Various UUID related utilities.
 *
 * @author darmbrust
 * @author kec
 */
public class UuidUtil {
    /**
     * Nil UUID
     * The "nil" UUID, a special case, is the UUID 00000000-0000-0000-0000-000000000000; that is, all bits set to zero.[2]
     */
    public static final UUID NIL_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");
   /**
    * Convert.
    *
    * @param data the data
    * @return the uuid
    */
   public static UUID convert(long[] data) {
      return new UUID(data[0], data[1]);
   }

   /**
    * Convert.
    *
    * @param id the id
    * @return the long[]
    */
   public static long[] convert(UUID id) {
      final long[] data = new long[2];

      data[0] = id.getMostSignificantBits();
      data[1] = id.getLeastSignificantBits();
      return data;
   }

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
}

