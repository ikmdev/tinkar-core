package org.hl7.tinkar.provider.spinedarray;

import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.map.mutable.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Set;

public class IntIntSetFileStore {
    private static final Logger LOG = LoggerFactory.getLogger(IntIntArrayFileStore.class);
    final File patternToElementNidsMapDirectory;
    final File patternToElementNidsMapData;
    ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, Integer>> patternElementNidsMap = ConcurrentHashMap.newMap();

    public IntIntSetFileStore(File patternToElementNidsMapDirectory) {
        this.patternToElementNidsMapDirectory = patternToElementNidsMapDirectory;
        this.patternToElementNidsMapData = new File(patternToElementNidsMapDirectory, "data");
    }

    public void read() throws IOException {
        if (patternToElementNidsMapData.exists()) {
            try (DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(patternToElementNidsMapData)))) {
                int patternKeyCount = dis.readInt();
                this.patternElementNidsMap = ConcurrentHashMap.newMap(patternKeyCount);
                for (int patternCount = 0; patternCount < patternKeyCount; patternCount++) {
                    int patternNid = dis.readInt();
                    int elementNidCount = dis.readInt();
                    ConcurrentHashMap<Integer, Integer> elementNidSet = ConcurrentHashMap.newMap(elementNidCount);
                    for (int elementCount = 0; elementCount < elementNidCount; elementCount++) {
                        int elementNid = dis.readInt();
                        elementNidSet.put(elementNid, elementNid);
                    }
                    this.patternElementNidsMap.put(patternNid, elementNidSet);
                }
            }
        }
    }

    public void write() throws IOException {
        patternToElementNidsMapData.getParentFile().mkdirs();
        try (DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(patternToElementNidsMapData)))) {
            dos.writeInt(patternElementNidsMap.size());
            for (Pair<Integer, ConcurrentHashMap<Integer, Integer>> keyValue : patternElementNidsMap.keyValuesView()) {
                dos.writeInt(keyValue.getOne());
                dos.writeInt(keyValue.getTwo().size());
                for (Integer elementNid : keyValue.getTwo().keySet()) {
                    dos.writeInt(elementNid);
                }
            }
        }
    }


    public boolean addToSet(int patternNid, int elementNid) {
        return null == patternElementNidsMap.getIfAbsentPut(patternNid, integer -> new ConcurrentHashMap<>())
                .put(elementNid, elementNid);
    }

    public Set<Integer> getElementNidsForPatternNid(int patternNid) {
        if (patternElementNidsMap.containsKey(patternNid)) {
            return patternElementNidsMap.get(patternNid).keySet();
        }
        return Set.of();
    }
}
