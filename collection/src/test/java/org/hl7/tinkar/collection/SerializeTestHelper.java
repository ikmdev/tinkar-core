package org.hl7.tinkar.collection;

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
