package org.hl7.tinkar.entity.util;

import org.eclipse.collections.api.block.procedure.Procedure2;
import org.hl7.tinkar.component.FieldDataType;
import org.hl7.tinkar.common.util.time.Stopwatch;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ObjIntConsumer;

public abstract class EntityProcessor implements ObjIntConsumer<byte[]> {

    AtomicInteger conceptCount = new AtomicInteger();
    AtomicInteger semanticCount = new AtomicInteger();
    AtomicInteger definitionForSemanticCount = new AtomicInteger();
    AtomicInteger stampCount = new AtomicInteger();
    AtomicInteger other = new AtomicInteger();
    Stopwatch stopwatch = new Stopwatch();

    @Override
    public void accept(byte[] bytes, int value) {
        // bytes starts with number of arrays (int = 4 bytes), then size of first array (int = 4 bytes), then entity format version then type token, -1 since index starts at 0...
        FieldDataType componentType = FieldDataType.fromToken(bytes[9]);
        switch (componentType) {
            case PATTERN_FOR_SEMANTIC_CHRONOLOGY:
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
        processBytesForType(componentType, bytes);
    }


    public abstract void processBytesForType(FieldDataType componentType, byte[] bytes);

    public void finish() {
        this.stopwatch.end();
    }

    public String report() {
        this.stopwatch.end();
        StringBuilder sb = new StringBuilder();
        sb.append("Duration: ").append(stopwatch.elapsedTime());
        if (conceptCount.get() > 0) {
            sb.append("\nConcepts: ").append(conceptCount);
        }
        if (semanticCount.get() > 0) {
            sb.append("\nSemantics: ").append(semanticCount);
        }
        if (definitionForSemanticCount.get() > 0) {
            sb.append("\nDefinitions for Semantics: ").append(definitionForSemanticCount);
        }
        if (stampCount.get() > 0) {
            sb.append("\nStamps: ").append(stampCount);
        }
        if (other.get() > 0) {
            sb.append("\nOthers: ").append(other);
        }
        sb.append("\n");
        return sb.toString();
    }

}
