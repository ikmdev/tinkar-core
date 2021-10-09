package org.hl7.tinkar.entity;

import io.soabase.recordbuilder.core.RecordBuilder;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.common.id.IntIdList;
import org.hl7.tinkar.common.id.IntIdSet;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.common.util.time.DateTimeUtil;
import org.hl7.tinkar.component.SemanticVersion;
import org.hl7.tinkar.terms.EntityFacade;

import java.time.Instant;

@RecordBuilder
public record SemanticVersionRecord(SemanticEntity<SemanticEntityVersion> chronology, int stampNid,
                                    ImmutableList<Object> fields)
        implements SemanticEntityVersion, SemanticVersionRecordBuilder.With {
    public SemanticVersionRecord(SemanticEntity<SemanticEntityVersion> chronology, SemanticVersion version) {
        this(chronology,
                EntityService.get().nidForComponent(version.stamp()),
                Lists.immutable.fromStream(version.fields().stream().map(o -> EntityRecordFactory.externalToInternalObject(o))));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SemanticVersionRecord that = (SemanticVersionRecord) o;
        return stampNid == that.stampNid && fields.equals(that.fields);
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(stampNid);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append(Entity.getStamp(stampNid).describe());
        PatternEntity pattern = Entity.getFast(this.chronology().patternNid());
        if (pattern instanceof PatternRecord patternEntity) {
            // TODO get proper version after relative position computer available.
            // Maybe put stamp coordinate on thread, or relative position computer on thread
            PatternEntityVersion patternEntityVersion = patternEntity.versions().get(0);
            sb.append("\n");
            for (int i = 0; i < fields.size(); i++) {
                sb.append("Field ");
                sb.append((i + 1));
                sb.append(": [");
                StringBuilder fieldStringBuilder = new StringBuilder();

                Object field = fields.get(i);
                if (i < patternEntityVersion.fieldDefinitions().size()) {
                    FieldDefinitionForEntity fieldDefinition = patternEntityVersion.fieldDefinitions().get(i);
                    fieldStringBuilder.append(PrimitiveData.text(fieldDefinition.meaningNid));
                } else {
                    fieldStringBuilder.append("Size error @ " + i);
                }
                fieldStringBuilder.append(": ");
                if (field instanceof EntityFacade entity) {
                    fieldStringBuilder.append(PrimitiveData.text(entity.nid()));
                } else if (field instanceof String string) {
                    fieldStringBuilder.append(string);
                } else if (field instanceof Instant instant) {
                    fieldStringBuilder.append(DateTimeUtil.format(instant));
                } else if (field instanceof IntIdList intIdList) {
                    if (intIdList.size() == 0) {
                        fieldStringBuilder.append("ø");
                    } else {
                        for (int j = 0; j < intIdList.size(); j++) {
                            if (j > 0) {
                                fieldStringBuilder.append(", ");
                            }
                            fieldStringBuilder.append(PrimitiveData.text(intIdList.get(j)));
                        }
                    }
                } else if (field instanceof IntIdSet intIdSet) {
                    if (intIdSet.size() == 0) {
                        fieldStringBuilder.append("ø");
                    } else {
                        int[] idSetArray = intIdSet.toArray();
                        for (int j = 0; j < idSetArray.length; j++) {
                            if (j > 0) {
                                fieldStringBuilder.append(", ");
                            }
                            fieldStringBuilder.append(PrimitiveData.text(idSetArray[j]));
                        }
                    }
                } else {
                    fieldStringBuilder.append(field);
                }
                String fieldString = fieldStringBuilder.toString();
                if (fieldString.contains("\n")) {
                    sb.append("\n");
                    sb.append(fieldString);
                } else {
                    sb.append(fieldString);
                }
                sb.append("]\n");

            }
        } else {
            sb.append("Bad pattern: ");
            sb.append(PrimitiveData.text(pattern.nid()));
            sb.append("; ");
            for (int i = 0; i < fields.size(); i++) {
                Object field = fields.get(i);
                if (i > 0) {
                    sb.append("; ");
                }
                if (field instanceof EntityFacade entity) {
                    sb.append("Entity: ");
                    sb.append(PrimitiveData.text(entity.nid()));
                } else if (field instanceof String string) {
                    sb.append("String: ");
                    sb.append(string);
                } else if (field instanceof Instant instant) {
                    sb.append("Instant: ");
                    sb.append(DateTimeUtil.format(instant));
                } else if (field instanceof Long aLong) {
                    sb.append("Long: ");
                    sb.append(DateTimeUtil.format(aLong));
                } else if (field instanceof IntIdList intIdList) {
                    sb.append(field.getClass().getSimpleName());
                    sb.append(": ");
                    if (intIdList.size() == 0) {
                        sb.append("ø, ");
                    } else {
                        for (int j = 0; j < intIdList.size(); j++) {
                            if (j > 0) {
                                sb.append(", ");
                            }
                            sb.append(PrimitiveData.text(intIdList.get(j)));
                        }
                    }
                } else if (field instanceof IntIdSet intIdSet) {
                    sb.append(field.getClass().getSimpleName());
                    sb.append(": ");
                    if (intIdSet.size() == 0) {
                        sb.append("ø, ");
                    } else {
                        int[] idSetArray = intIdSet.toArray();
                        for (int j = 0; j < idSetArray.length; j++) {
                            if (j > 0) {
                                sb.append(", ");
                            }
                            sb.append(PrimitiveData.text(idSetArray[j]));
                        }
                    }
                } else {
                    sb.append(field.getClass().getSimpleName());
                    sb.append(": ");
                    sb.append(field);
                }
            }
        }

        sb.append("]}");

        return sb.toString();
    }

}
