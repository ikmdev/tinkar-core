package org.hl7.tinkar.common.id;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PublicIdsTest {

    @Test
    public void publicList() {
        PublicIdList idList = PublicIds.list.of(
                PublicIds.newRandom(),
                PublicIds.newRandom()
                );
        assertEquals(idList.size(), 2);
        PublicIdList idList2 = PublicIds.list.of(idList.toIdArray());
        assertEquals(idList, idList2);

        PublicIdSet idSet = PublicIds.set.of(idList);
        PublicIdSet idSet2 = PublicIds.set.of(idList);
        assertEquals(idSet, idSet2);
        PublicIdSet set3 = PublicIds.set.of(idList2.toIdArray());
    }
}