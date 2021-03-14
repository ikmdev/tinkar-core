package org.hl7.tinkar.common.util.id;


import org.eclipse.collections.impl.factory.primitive.IntSets;

public interface IntIdList extends IdList, IntIdCollection {
    int get(int index);

}
