package org.hl7.tinkar.common.util.id;

import org.eclipse.collections.impl.utility.Iterate;
import org.hl7.tinkar.common.util.id.impl.PublicIdCollections;

import java.util.Objects;

public class PublicIdSetFactory {
    public static final PublicIdSetFactory INSTANCE = new PublicIdSetFactory();

    public <E extends PublicId> PublicIdSet<E> empty()
    {
        return (PublicIdSet<E>) PublicIdCollections.SetN.EMPTY_SET;
    }

    public <E extends PublicId> PublicIdSet<E> of()
    {
        return this.empty();
    }

    public <E extends PublicId> PublicIdSet<E> of(E one)
    {
        return new PublicIdCollections.Set12<>(one);
    }

    public <E extends PublicId> PublicIdSet<E> of(E one, E two)
    {
        if (Objects.equals(one, two))
        {
            return this.of(one);
        }
        return new PublicIdCollections.Set12<>(one, two);
    }

    public <E extends PublicId> PublicIdSet<E> of(E one, E two, E three)
    {
        if (Objects.equals(one, two))
        {
            return this.of(one, three);
        }
        if (Objects.equals(one, three))
        {
            return this.of(one, two);
        }
        if (Objects.equals(two, three))
        {
            return this.of(one, two);
        }
        return new PublicIdCollections.SetN<>(one, two, three);
    }

    public <E extends PublicId> PublicIdSet<E> of(E one, E two, E three, E four)
    {
        if (Objects.equals(one, two))
        {
            return this.of(one, three, four);
        }
        if (Objects.equals(one, three))
        {
            return this.of(one, two, four);
        }
        if (Objects.equals(one, four))
        {
            return this.of(one, two, three);
        }
        if (Objects.equals(two, three))
        {
            return this.of(one, two, four);
        }
        if (Objects.equals(two, four))
        {
            return this.of(one, two, three);
        }
        if (Objects.equals(three, four))
        {
            return this.of(one, two, three);
        }
        return new PublicIdCollections.SetN<>(one, two, three, four);
    }
    public PublicIdSet<PublicId> ofArray(PublicId[] items) {
        return of(items);
    }

    public <E extends PublicId> PublicIdSet<E> of(E... items)
    {
        if (items == null || items.length == 0)
        {
            return this.of();
        }

        switch (items.length)
        {
            case 1:
                return this.of(items[0]);
            case 2:
                return this.of(items[0], items[1]);
            case 3:
                return this.of(items[0], items[1], items[2]);
            case 4:
                return this.of(items[0], items[1], items[2], items[3]);
            default:
                return new PublicIdCollections.SetN<>(items);
        }
    }
    public static final PublicId[] emptyArray = new PublicId[0];
    public <E extends PublicId> PublicIdSet<E> of(Iterable<? extends E> items)
    {
        if (items instanceof PublicIdSet<?>)
        {
            return (PublicIdSet<E>) items;
        }

        if (Iterate.isEmpty(items))
        {
            return this.of();
        }
        return this.of((E[]) Iterate.toArray(items, emptyArray));
    }
}
