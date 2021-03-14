package org.hl7.tinkar.common.collection;

public final class Key implements Comparable<Key>
{
    private final String value;

    public Key(String value)
    {
        this.value = value;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        if (obj == null || this.getClass() != obj.getClass())
        {
            return false;
        }

        Key that = (Key) obj;

        return this.value.equals(that.value);
    }

    @Override
    public int hashCode()
    {
        return this.value.hashCode();
    }

    @Override
    public String toString()
    {
        return "Key{ '" + this.value + "' }";
    }

    @Override
    public int compareTo(Key o)
    {
        return this.value.compareTo(o.value);
    }
}
