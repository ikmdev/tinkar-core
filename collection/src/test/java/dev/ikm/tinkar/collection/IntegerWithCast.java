package dev.ikm.tinkar.collection;

public final class IntegerWithCast
{
    private final int value;

    public IntegerWithCast(int value)
    {
        this.value = value;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null)
        {
            return false;
        }

        IntegerWithCast that = (IntegerWithCast) o;
        return this.value == that.value;
    }

    @Override
    public int hashCode()
    {
        return this.value;
    }
}
