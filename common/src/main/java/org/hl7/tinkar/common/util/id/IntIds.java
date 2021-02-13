package org.hl7.tinkar.common.util.id;

import org.hl7.tinkar.common.util.id.impl.IntIdListFactory;
import org.hl7.tinkar.common.util.id.impl.IntIdSetFactory;

public class IntIds {
    public static final IntIdListFactory list = IntIdListFactory.INSTANCE;
    public static final IntIdSetFactory set = IntIdSetFactory.INSTANCE;
}
