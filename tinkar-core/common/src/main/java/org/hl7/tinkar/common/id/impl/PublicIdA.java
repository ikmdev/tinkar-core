package org.hl7.tinkar.common.id.impl;

import org.hl7.tinkar.common.id.PublicId;

import java.util.UUID;

import static org.hl7.tinkar.common.id.IdCollection.TO_STRING_LIMIT;

public abstract class PublicIdA implements PublicId {
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        UUID[] uuids = asUuidArray();
        for (int i = 0; i < uuids.length && i <= TO_STRING_LIMIT; i++) {
            sb.append("\"");
            sb.append(uuids[i].toString());
            sb.append("\"");
            sb.append(", ");
            if (i == TO_STRING_LIMIT) {
                sb.append("..., ");
            }
        }
        sb.delete(sb.length() - 2, sb.length() - 1);
        sb.deleteCharAt(sb.length() - 1);
        sb.append("]");
        return sb.toString();
    }
}
