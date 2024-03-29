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
package dev.ikm.tinkar.collection;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public final class SerializeTestHelper
{
    private SerializeTestHelper()
    {
        throw new AssertionError("Suppress default constructor for noninstantiability");
    }

    public static <T> T serializeDeserialize(T sourceObject)
    {
        byte[] pileOfBytes = serialize(sourceObject);
        return (T) deserialize(pileOfBytes);
    }

    public static <T> byte[] serialize(T sourceObject)
    {
        ByteArrayOutputStream baos = SerializeTestHelper.getByteArrayOutputStream(sourceObject);
        return baos.toByteArray();
    }

    public static <T> ByteArrayOutputStream getByteArrayOutputStream(T sourceObject)
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try
        {
            writeObjectToStream(sourceObject, baos);
        }
        catch (IOException e)
        {
            Verify.fail("Failed to marshal an object", e);
        }
        return baos;
    }

    private static <T> void writeObjectToStream(Object sourceObject, ByteArrayOutputStream baos) throws IOException
    {
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(baos))
        {
            objectOutputStream.writeObject(sourceObject);
            objectOutputStream.flush();
            objectOutputStream.close();
        }
    }

    private static Object readOneObject(ByteArrayInputStream bais)
            throws IOException, ClassNotFoundException
    {
        try (ObjectInputStream objectStream = new ObjectInputStream(bais))
        {
            return objectStream.readObject();
        }
    }

    public static Object deserialize(byte[] pileOfBytes)
    {
        ByteArrayInputStream bais = new ByteArrayInputStream(pileOfBytes);
        try
        {
            return readOneObject(bais);
        }
        catch (ClassNotFoundException | IOException e)
        {
            Verify.fail("Failed to unmarshal an object", e);
        }

        return null;
    }
}
