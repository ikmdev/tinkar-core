package org.hl7.tinkar.entity;

import io.soabase.recordbuilder.core.RecordBuilder;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.component.PatternVersion;

@RecordBuilder
public record PatternVersionRecord(PatternRecord chronology, int stampNid,
                                   int semanticPurposeNid, int semanticMeaningNid,
                                   MutableList<FieldDefinitionRecord> fieldDefinitionMutableList)
        implements PatternEntityVersion, PatternVersionRecordBuilder.With {

    public PatternVersionRecord(PatternRecord chronology, PatternVersion patternVersion) {
        this(chronology,
                EntityService.get().nidForComponent(patternVersion.stamp()),
                EntityService.get().nidForComponent(patternVersion.semanticPurpose()),
                EntityService.get().nidForComponent(patternVersion.semanticMeaning()),
                Lists.mutable.withInitialCapacity(patternVersion.fieldDefinitions().size()));

        patternVersion.fieldDefinitions().forEach(fieldDefinition -> fieldDefinitionMutableList.add(new FieldDefinitionRecord(fieldDefinition,
                this)));
    }

    @Override
    public PatternEntity entity() {
        return chronology;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PatternVersionRecord that = (PatternVersionRecord) o;
        return stampNid == that.stampNid && semanticPurposeNid == that.semanticPurposeNid && semanticMeaningNid == that.semanticMeaningNid && fieldDefinitionMutableList.equals(that.fieldDefinitionMutableList);
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(stampNid);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("≤");
        sb.append(Entity.getStamp(stampNid).describe());

        sb.append(" rcp: ");
        sb.append(PrimitiveData.text(semanticPurposeNid));
        sb.append(" rcm: ");
        sb.append(PrimitiveData.text(semanticMeaningNid));
        sb.append(" f: [‹]");
        // TODO get proper version after relative position computer available.
        // Maybe put stamp coordinate on thread, or relative position computer on thread
        for (int i = 0; i < fieldDefinitions().size(); i++) {
            if (i > 0) {
                sb.append("; ");
            }
            sb.append(i);
            sb.append(": ");
            FieldDefinitionRecord fieldDefinition = fieldDefinitions().get(i);
            sb.append(fieldDefinition);
        }
        sb.append("]≥");

        return sb.toString();
    }

    public ImmutableList<FieldDefinitionRecord> fieldDefinitions() {
        return fieldDefinitionMutableList.toImmutable();
    }
}
