package org.hl7.tinkar.entity;

import io.soabase.recordbuilder.core.RecordBuilder;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.component.FieldDefinition;
import org.hl7.tinkar.component.PatternVersion;

@RecordBuilder
public record PatternVersionRecord(PatternEntity<PatternEntityVersion> chronology, int stampNid,
                                   int semanticPurposeNid, int semanticMeaningNid,
                                   ImmutableList<FieldDefinitionForEntity> fieldDefinitions)
        implements PatternEntityVersion, PatternVersionRecordBuilder.With {

    public PatternVersionRecord(PatternEntity<PatternEntityVersion> chronology, PatternVersion<FieldDefinition> patternVersion) {
        this(chronology,
                EntityService.get().nidForComponent(patternVersion.stamp()),
                EntityService.get().nidForComponent(patternVersion.semanticPurpose()),
                EntityService.get().nidForComponent(patternVersion.semanticMeaning()),
                Lists.immutable.fromStream(patternVersion.fieldDefinitions().stream().map(fieldDefinition -> new FieldDefinitionForEntity(fieldDefinition))));
    }

    @Override
    public PatternEntity entity() {
        return chronology;
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

        sb.append(" rcp: ");
        sb.append(PrimitiveData.text(semanticPurposeNid));
        sb.append(" rcm: ");
        sb.append(PrimitiveData.text(semanticMeaningNid));
        sb.append(" f: [");
        // TODO get proper version after relative position computer available.
        // Maybe put stamp coordinate on thread, or relative position computer on thread
        for (int i = 0; i < fieldDefinitions().size(); i++) {
            if (i > 0) {
                sb.append("; ");
            }
            sb.append(i);
            sb.append(": ");
            FieldDefinitionForEntity fieldDefinitionForEntity = fieldDefinitions().get(i);
            sb.append(fieldDefinitionForEntity);
        }
        sb.append("]}");

        return sb.toString();
    }
}
