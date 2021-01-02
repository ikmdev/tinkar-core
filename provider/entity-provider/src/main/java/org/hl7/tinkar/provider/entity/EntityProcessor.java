package org.hl7.tinkar.provider.entity;

import org.eclipse.collections.api.block.procedure.Procedure2;
import org.hl7.tinkar.lombok.dto.FieldDataType;
import org.hl7.tinkar.util.time.Stopwatch;

import java.util.concurrent.atomic.AtomicInteger;

public abstract class EntityProcessor implements Procedure2<Integer, byte[]> {

    AtomicInteger conceptCount = new AtomicInteger();
    AtomicInteger semanticCount = new AtomicInteger();
    AtomicInteger definitionForSemanticCount = new AtomicInteger();
    AtomicInteger stampCount = new AtomicInteger();
    AtomicInteger other = new AtomicInteger();
    Stopwatch stopwatch = new Stopwatch();


    @Override
    public void value(Integer nid, byte[] bytes) {
        // bytes starts with number of arrays (int = 4 bytes), then size of first array (int = 4 bytes), then type token, -1 since index starts at 0...
        FieldDataType componentType = FieldDataType.fromToken(bytes[8]);
        switch (componentType) {
            case DEFINITION_FOR_SEMANTIC_CHRONOLOGY:
                definitionForSemanticCount.incrementAndGet();
                break;
            case CONCEPT_CHRONOLOGY:
                conceptCount.incrementAndGet();
                break;
            case SEMANTIC_CHRONOLOGY:
                semanticCount.incrementAndGet();
                break;
            case STAMP:
                stampCount.incrementAndGet();
                break;
            default:
                other.incrementAndGet();
        }

    }

    public abstract void processBytesForType(FieldDataType componentType, byte[] bytes);

    public void finish() {
        this.stopwatch.end();
    }

    public String report() {
        this.stopwatch.end();
        StringBuilder sb = new StringBuilder();
        sb.append("Duration: ").append(stopwatch.elapsedTime());
        sb.append("\nConcepts: ").append(conceptCount);
        sb.append("\nSemantics: ").append(semanticCount);
        sb.append("\nDefinitions for Semantics: ").append(definitionForSemanticCount);
        sb.append("\nStamps: ").append(stampCount);
        sb.append("\nOthers: ").append(other).append("\n");
        return sb.toString();
    }

}
