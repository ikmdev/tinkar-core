package org.hl7.tinkar.common.util.id.impl;

import org.hl7.tinkar.common.util.id.PublicId;

import java.util.UUID;

public abstract class PublicIdA implements PublicId {
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        UUID[] uuids = asUuidArray();
        for (int i = 0; i < uuids.length; i++) {
            sb.append("\"");
            sb.append(uuids[i].toString());
            sb.append("\"");
            sb.append(", ");
        }
        sb.delete(sb.length() - 2, sb.length() - 1);
        sb.deleteCharAt(sb.length() - 1);
        sb.append("]");
        return sb.toString();
    }
}
