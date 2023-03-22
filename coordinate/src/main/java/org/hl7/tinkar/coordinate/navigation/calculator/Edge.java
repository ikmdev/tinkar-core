package org.hl7.tinkar.coordinate.navigation.calculator;

import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.hl7.tinkar.common.id.IntIdSet;

/**
 * The type and destination parts of a relationship displayed in a tree.
 * @author kec
 */
public interface Edge {
    /**
     *
     * @return the concept nid for the type of the linkage to the destination
     */
    IntIdSet typeNids();

    /**
     *
     * @return the destination concept nid.
     */
    int destinationNid();
}
