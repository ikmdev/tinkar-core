package org.hl7.tinkar.provider.spinedarray;

import org.eclipse.collections.impl.map.mutable.ConcurrentHashMap;
import org.hl7.tinkar.common.sets.ConcurrentHashSet;
import org.hl7.tinkar.common.util.time.Stopwatch;
import org.hl7.tinkar.component.FieldDataType;
import org.hl7.tinkar.entity.*;
import org.hl7.tinkar.entity.transaction.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ObjIntConsumer;

public class UuidNidCollector implements ObjIntConsumer<byte[]> {
    private static final Logger LOG = LoggerFactory.getLogger(UuidNidCollector.class);
    final ConcurrentHashMap<UUID, Integer> uuidToNidMap;
    final ConcurrentHashSet<Integer> patternNids;
    final ConcurrentHashSet<Integer> conceptNids;
    final ConcurrentHashSet<Integer> semanticNids;
    final ConcurrentHashSet<Integer> stampNids;
    final ConcurrentHashMap<Integer, ConcurrentHashSet<Integer>> patternElementNidsMap;


    AtomicInteger totalCount = new AtomicInteger();
    AtomicInteger conceptCount = new AtomicInteger();
    AtomicInteger semanticCount = new AtomicInteger();
    AtomicInteger patternCount = new AtomicInteger();
    AtomicInteger stampCount = new AtomicInteger();
    AtomicInteger other = new AtomicInteger();
    Stopwatch stopwatch = new Stopwatch();

    public UuidNidCollector(ConcurrentHashMap<UUID, Integer> uuidToNidMap,
                            ConcurrentHashSet<Integer> patternNids,
                            ConcurrentHashSet<Integer> conceptNids,
                            ConcurrentHashSet<Integer> semanticNids,
                            ConcurrentHashSet<Integer> stampNids,
                            ConcurrentHashMap<Integer, ConcurrentHashSet<Integer>> patternElementNidsMap) {
        this.uuidToNidMap = uuidToNidMap;
        this.patternNids = patternNids;
        this.conceptNids = conceptNids;
        this.semanticNids = semanticNids;
        this.stampNids = stampNids;
        this.patternElementNidsMap = patternElementNidsMap;
    }

    @Override
    public void accept(byte[] bytes, int value) {
        // bytes starts with number of arrays (int = 4 bytes), then size of first array (int = 4 bytes), then entity format version then type token, -1 since index starts at 0...
        FieldDataType componentType = FieldDataType.fromToken(bytes[9]);
        if (value == Integer.MIN_VALUE) {
            LOG.error("value of Integer.MIN_VALUE should not happen. ");
        }
        boolean typeToProcess = false;
        switch (componentType) {
            case PATTERN_CHRONOLOGY:
                patternCount.incrementAndGet();
                totalCount.incrementAndGet();
                typeToProcess = true;
                this.patternNids.add(value);
                break;
            case CONCEPT_CHRONOLOGY:
                conceptCount.incrementAndGet();
                totalCount.incrementAndGet();
                this.conceptNids.add(value);
                typeToProcess = true;
                break;
            case SEMANTIC_CHRONOLOGY:
                semanticCount.incrementAndGet();
                totalCount.incrementAndGet();
                typeToProcess = true;
                semanticNids.add(value);
                break;
            case STAMP:
                stampCount.incrementAndGet();
                totalCount.incrementAndGet();
                typeToProcess = true;
                stampNids.add(value);
                break;
            default:
                other.incrementAndGet();
                totalCount.incrementAndGet();
        }
        if (typeToProcess == true) {
            Entity<?> entity = EntityRecordFactory.make(bytes);
            if (entity instanceof SemanticEntity semanticEntity) {
                patternElementNidsMap.getIfAbsentPut(semanticEntity.patternNid(), integer -> new ConcurrentHashSet())
                        .add(semanticEntity.nid());
            }
            if (entity instanceof StampRecord stampRecord) {
                if (stampRecord.time() == Long.MAX_VALUE && Transaction.forStamp(stampRecord).isEmpty()) {
                    // Uncommmitted stamp outside of a transaction on restart. Set to canceled.
                    LOG.warn("Canceling uncommitted stamp: " + stampRecord.publicId().asUuidList());
                    StampVersionRecord lastVersion = stampRecord.lastVersion();
                    StampVersionRecord canceledVersion = lastVersion.withTime(Long.MIN_VALUE);
                    stampRecord.versionRecords().clear();
                    stampRecord.versionRecords().add(canceledVersion);
                    Entity.provider().putStamp(stampRecord);
                }
            }
            for (UUID uuid : entity.asUuidArray()) {
                uuidToNidMap.put(uuid, entity.nid());
            }
        }
    }

    public void finish() {
        this.stopwatch.end();
    }

    public String report() {
        this.stopwatch.end();
        StringBuilder sb = new StringBuilder();
        sb.append("Finished: ").append(this.getClass().getSimpleName());
        sb.append("\nDuration: ").append(stopwatch.durationString());
        sb.append("\nAverage realization time: ").append(stopwatch.averageDurationForElementString(totalCount.get()));
        sb.append("\nUUIDs: ").append(uuidToNidMap.size());
        if (conceptCount.get() > 0) {
            sb.append("\nConcepts: ").append(conceptCount);
        }
        if (semanticCount.get() > 0) {
            sb.append("\nSemantics: ").append(semanticCount);
        }
        if (patternCount.get() > 0) {
            sb.append("\nType pattern: ").append(patternCount);
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
