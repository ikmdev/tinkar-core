/*
 * Copyright © 2015 Integrated Knowledge Management (support@ikm.dev)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.ikm.tinkar.entity.scope;

import dev.ikm.tinkar.common.service.DataUse;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.EntityRecordFactory;
import dev.ikm.tinkar.entity.EntityVersion;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.entity.boundary.DataKind;
import dev.ikm.tinkar.entity.boundary.EntityBoundaries;
import dev.ikm.tinkar.entity.boundary.ModuleKinds;
import dev.ikm.tinkar.entity.boundary.References;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongConsumer;
import java.util.function.ObjLongConsumer;

/**
 * The data-use index a store keeps for scope deletes (IKE-Network/ike-issues#1313; design
 * {@code design-2026-10-20-instance-data}, "The data-use index"): for every version written under
 * an instance or ephemeral STAMP, the STAMP's use, and each reference the version carries other
 * than its semantic's pattern and referenced component whose target is not knowledge. Knowledge
 * versions are not read for references: the commit boundary refuses knowledge referencing data.
 *
 * <p>The store calls {@link #merged(Object)} after each merge, {@link #loadPhase(boolean)} as a
 * load phase begins and ends, {@link #erased(Entity)} for each component it erases, and
 * {@link #save(long)} as it closes; {@link #invalidate(String)} for a write the index cannot
 * follow, such as a repair. A version is classified by its STAMP's module, cached per STAMP and
 * per module. A component whose STAMP the store does not hold yet, as in a parallel load, waits
 * on a pending list, read again when the load phase ends or before the index is next asked.
 * Every known module is resolved again then too, since a module's kind may be tentative mid-load
 * or be stated anew later, and a change marks the index for rebuilding.
 *
 * <p>The file is written at close with a fingerprint of the store; an absent file or one whose
 * fingerprint no longer matches makes the index absent, and {@link #current()} rebuilds it with
 * one pass over the store.
 */
public final class DataUseIndex implements DataUse {

    private static final Logger LOG = LoggerFactory.getLogger(DataUseIndex.class);

    /** The index's file in a store's root. */
    public static final String FILE_NAME = "dataUse.index";

    private static final long MAGIC = 0x494B455F44555345L;          // "IKE_DUSE"
    private static final int FORMAT = 1;

    /** How many components may wait for their STAMPs before the index is rebuilt instead: {@code ike.dataUse.pendingLimit}. */
    static final int PENDING_LIMIT = Integer.getInteger("ike.dataUse.pendingLimit", 1_000_000);

    /** The store's own parallel pass over every stored record, for a rebuild. */
    @FunctionalInterface
    public interface Scan {
        void forEachParallel(ObjLongConsumer<byte[]> action);
    }

    private final File file;
    private final Scan scan;
    private final ConcurrentHashMap<Long, Set<Long>> stampUse = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Set<Long>> referrers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> stampModules = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, DataKind> moduleKinds = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Long> pending = new ConcurrentLinkedQueue<>();
    private final AtomicInteger pendingCount = new AtomicInteger();
    private final AtomicLong rebuilds = new AtomicLong();
    private volatile boolean current;
    private volatile boolean loadPhase;

    private DataUseIndex(File file, Scan scan, boolean current) {
        this.file = file;
        this.scan = scan;
        this.current = current;
    }

    /**
     * The index of a store held in memory only: it starts empty and current, as the store does.
     *
     * @param scan the store's pass over every record
     * @return the index
     */
    public static DataUseIndex memoryOnly(Scan scan) {
        return new DataUseIndex(null, scan, true);
    }

    /**
     * Opens the index of a persistent store: read from its file when the fingerprint matches,
     * empty and current when the store is new, and otherwise absent, to be rebuilt when asked.
     *
     * @param root        the store's root, which holds {@value #FILE_NAME}
     * @param fingerprint the store's fingerprint as it opens, before anything is written
     * @param emptyStore  whether the store holds nothing yet
     * @param scan        the store's pass over every record
     * @return the index
     */
    public static DataUseIndex open(File root, long fingerprint, boolean emptyStore, Scan scan) {
        File file = new File(root, FILE_NAME);
        if (emptyStore) {
            deleteQuietly(file);
            return new DataUseIndex(file, scan, true);
        }
        DataUseIndex index = new DataUseIndex(file, scan, false);
        if (!file.isFile()) {
            LOG.info("{} keeps no data-use index; it is built when a scope delete first asks", root.getName());
            return index;
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file.toPath())))) {
            if (in.readLong() != MAGIC || in.readInt() != FORMAT) {
                LOG.info("{}'s data-use index is in a format this build does not read; it is rebuilt when asked", root.getName());
                return index;
            }
            long written = in.readLong();
            if (written != fingerprint) {
                LOG.info("{} changed since its data-use index was written (fingerprint {}, now {}); it is rebuilt when asked",
                        root.getName(), written, fingerprint);
                return index;
            }
            index.read(in);
            index.current = true;
            LOG.info("Read {}'s data-use index: {}", root.getName(), index.summary());
        } catch (IOException | RuntimeException e) {
            LOG.warn("Could not read the data-use index of {}; it is rebuilt when asked", root.getName(), e);
            index.clearEntries();
        }
        return index;
    }

    /**
     * A fingerprint of files on disk: each one's path, length and modification time, under the
     * given files and directories.
     *
     * @param files the files and directories
     * @return the fingerprint
     */
    public static long fingerprintOf(File... files) {
        long hash = 1125899906842597L;
        List<File> all = new ArrayList<>();
        for (File f : files) {
            collect(f, all);
        }
        all.sort(null);
        for (File f : all) {
            hash = 31 * hash + f.getPath().hashCode();
            hash = 31 * hash + f.length();
            hash = 31 * hash + f.lastModified();
        }
        return hash;
    }

    private static void collect(File f, List<File> into) {
        if (f == null || !f.exists()) {
            return;
        }
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File child : children) {
                    collect(child, into);
                }
            }
        } else {
            into.add(f);
        }
    }

    // ── maintenance ──────────────────────────────────────────────────────────

    /**
     * Records what a merged entity's versions use. A STAMP's own merge records nothing.
     *
     * @param source the entity the store merged
     */
    public void merged(Object source) {
        if (current && source instanceof Entity<?> entity && !(entity instanceof StampEntity<?>)) {
            index(entity, true);
        }
    }

    /**
     * A load phase begins or ends; at its end the waiting components are read again and every
     * known module is resolved again.
     *
     * @param loading whether a load phase is beginning
     */
    public void loadPhase(boolean loading) {
        this.loadPhase = loading;
        if (!loading) {
            synchronized (this) {
                settle();
            }
        }
    }

    /**
     * Removes what an erased component's versions recorded, and what referenced it.
     *
     * @param entity the component, as it was before the erase
     */
    public void erased(Entity<?> entity) {
        long nid = entity.nid();
        referrers.remove(nid);
        if (entity instanceof StampEntity<?>) {
            stampUse.remove(nid);
            stampModules.remove(nid);
            return;
        }
        for (Object o : entity.versions()) {
            EntityVersion version = (EntityVersion) o;
            remove(stampUse, version.stampNid(), nid);
            References.of(version, (where, target) -> remove(referrers, target, nid));
        }
    }

    /**
     * Marks the index absent, for a write it cannot follow; the next question rebuilds it.
     *
     * @param why what happened, for the log
     */
    public void invalidate(String why) {
        if (current) {
            LOG.info("The data-use index is rebuilt when next asked: {}", why);
        }
        current = false;
        pending.clear();
        pendingCount.set(0);
    }

    /**
     * Writes the index to its file with the store's fingerprint, as the store closes; an index
     * that is not current deletes the file instead. A memory-only index writes nothing.
     *
     * @param fingerprint the store's fingerprint, after its last write
     */
    public synchronized void save(long fingerprint) {
        if (file == null) {
            return;
        }
        if (!current) {
            deleteQuietly(file);
            return;
        }
        Path tmp = file.toPath().resolveSibling(FILE_NAME + ".tmp");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp)))) {
            out.writeLong(MAGIC);
            out.writeInt(FORMAT);
            out.writeLong(fingerprint);
            write(out);
        } catch (IOException e) {
            LOG.warn("Could not write the data-use index to {}", file, e);
            deleteQuietly(file);
            return;
        }
        try {
            Files.move(tmp, file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            LOG.info("Wrote the data-use index to {} (fingerprint {}): {}", file, fingerprint, summary());
        } catch (IOException e) {
            LOG.warn("Could not move the data-use index into place at {}", file, e);
            deleteQuietly(file);
        }
    }

    // ── questions ────────────────────────────────────────────────────────────

    @Override
    public synchronized boolean current() {
        settle();
        if (!current) {
            rebuild();
        }
        return current;
    }

    @Override
    public void forEachWrittenUnder(long stampNid, LongConsumer users) {
        Set<Long> set = stampUse.get(stampNid);
        if (set != null) {
            for (Long nid : set) {
                users.accept(nid);
            }
        }
    }

    @Override
    public void forEachReferrer(long targetNid, LongConsumer referrers) {
        Set<Long> set = this.referrers.get(targetNid);
        if (set != null) {
            for (Long nid : set) {
                referrers.accept(nid);
            }
        }
    }

    /** @return how many times the index was built by a pass over the store */
    public long rebuilds() {
        return rebuilds.get();
    }

    /** @return the index's size, in words */
    public String summary() {
        long uses = stampUse.values().stream().mapToLong(Set::size).sum();
        long references = referrers.values().stream().mapToLong(Set::size).sum();
        return String.format("%,d instance or ephemeral STAMP(s) with %,d use(s), %,d referenced component(s) with %,d reference(s), %,d module(s)",
                stampUse.size(), uses, referrers.size(), references, moduleKinds.size());
    }

    // ── internals ────────────────────────────────────────────────────────────

    private void index(Entity<?> entity, boolean deferUnresolved) {
        long nid = entity.nid();
        for (Object o : entity.versions()) {
            EntityVersion version = (EntityVersion) o;
            long stamp = version.stampNid();
            DataKind kind = stampKind(stamp);
            if (kind == null) {
                if (deferUnresolved) {
                    defer(nid);
                    return;
                }
                // A STAMP the store does not hold: its kind cannot be known, so the version counts as data.
            } else if (kind == DataKind.KNOWLEDGE) {
                continue;
            }
            add(stampUse, stamp, nid);
            References.of(version, (where, target) -> {
                if (target == 0 || target == nid || References.PATTERN.equals(where) || References.REFERENCED_COMPONENT.equals(where)) {
                    return;
                }
                if (!knowledge(target)) {
                    add(referrers, target, nid);
                }
            });
        }
    }

    /** The kind of a STAMP's module, or null when the store does not hold the STAMP. */
    private DataKind stampKind(long stamp) {
        Long module = stampModules.get(stamp);
        if (module == null) {
            long read = EntityBoundaries.moduleOf(stamp);
            if (read == 0) {
                return null;
            }
            stampModules.putIfAbsent(stamp, read);
            module = read;
        }
        DataKind kind = moduleKinds.get(module);
        if (kind == null) {
            kind = ModuleKinds.resolve(module).kind();
            moduleKinds.putIfAbsent(module, kind);
        }
        return kind;
    }

    /**
     * Whether a referenced component is knowledge: every version of it, and of its pattern when it
     * is a semantic, written under a knowledge STAMP. One the store does not hold yet is not.
     */
    private boolean knowledge(long nid) {
        Optional<Entity<?>> held = read(nid);
        if (held.isEmpty()) {
            return false;
        }
        if (held.get() instanceof SemanticEntity<?> semantic) {
            Optional<Entity<?>> pattern = read(semantic.patternNid());
            if (pattern.isEmpty() || !allKnowledge(pattern.get())) {
                return false;
            }
        }
        return allKnowledge(held.get());
    }

    private boolean allKnowledge(Entity<?> entity) {
        if (entity instanceof StampEntity<?>) {
            return true;
        }
        for (Object o : entity.versions()) {
            if (stampKind(((EntityVersion) o).stampNid()) != DataKind.KNOWLEDGE) {
                return false;
            }
        }
        return true;
    }

    private void defer(long nid) {
        if (pendingCount.incrementAndGet() > PENDING_LIMIT) {
            invalidate("more than " + PENDING_LIMIT + " components waited for their STAMPs");
            return;
        }
        pending.add(nid);
    }

    /** Resolves every known module again, then reads the waiting components; caller holds the lock. */
    private void settle() {
        if (!current) {
            return;
        }
        ModuleKinds.clear();
        EntityBoundaries.clear();
        for (Map.Entry<Long, DataKind> known : moduleKinds.entrySet()) {
            DataKind now = ModuleKinds.resolve(known.getKey()).kind();
            if (now != known.getValue()) {
                invalidate("module " + known.getKey() + " is now " + now.word() + ", was " + known.getValue().word());
                return;
            }
        }
        Long nid;
        while ((nid = pending.poll()) != null) {
            pendingCount.decrementAndGet();
            read(nid).ifPresent(entity -> index(entity, false));
        }
    }

    /** Builds the index again from every record; caller holds the lock. */
    private void rebuild() {
        long start = System.nanoTime();
        clearEntries();
        ModuleKinds.clear();
        EntityBoundaries.clear();
        current = true;
        scan.forEachParallel((bytes, nid) -> {
            if (bytes == null) {
                return;
            }
            Entity<?> entity = EntityRecordFactory.make(bytes);
            if (!(entity instanceof StampEntity<?>)) {
                index(entity, false);
            }
        });
        rebuilds.incrementAndGet();
        LOG.info("Built the data-use index in {} ms: {}", (System.nanoTime() - start) / 1_000_000, summary());
    }

    private void clearEntries() {
        stampUse.clear();
        referrers.clear();
        stampModules.clear();
        moduleKinds.clear();
        pending.clear();
        pendingCount.set(0);
    }

    private static Optional<Entity<?>> read(long nid) {
        try {
            return EntityHandle.get(nid).entity().map(e -> (Entity<?>) e);
        } catch (RuntimeException absent) {
            return Optional.empty();
        }
    }

    /** Adds under a key atomically with any removal of that key, so no addition lands in a set already dropped. */
    private static void add(ConcurrentHashMap<Long, Set<Long>> map, long key, long value) {
        map.compute(key, (k, set) -> {
            Set<Long> s = set == null ? ConcurrentHashMap.newKeySet() : set;
            s.add(value);
            return s;
        });
    }

    private static void remove(ConcurrentHashMap<Long, Set<Long>> map, long key, long value) {
        map.computeIfPresent(key, (k, set) -> {
            set.remove(value);
            return set.isEmpty() ? null : set;
        });
    }

    private void write(DataOutputStream out) throws IOException {
        List<Map.Entry<Long, DataKind>> modules = new ArrayList<>(moduleKinds.entrySet());
        out.writeInt(modules.size());
        for (Map.Entry<Long, DataKind> module : modules) {
            out.writeLong(module.getKey());
            out.writeByte(module.getValue().ordinal());
        }
        // Only the data STAMPs' modules: a knowledge STAMP is read again when next merged under.
        List<Map.Entry<Long, Long>> stamps = new ArrayList<>();
        for (Map.Entry<Long, Long> stamp : stampModules.entrySet()) {
            DataKind kind = moduleKinds.get(stamp.getValue());
            if (kind != null && kind != DataKind.KNOWLEDGE) {
                stamps.add(stamp);
            }
        }
        out.writeInt(stamps.size());
        for (Map.Entry<Long, Long> stamp : stamps) {
            out.writeLong(stamp.getKey());
            out.writeLong(stamp.getValue());
        }
        writeSets(out, stampUse);
        writeSets(out, referrers);
        List<Long> waiting = new ArrayList<>(pending);
        out.writeInt(waiting.size());
        for (long nid : waiting) {
            out.writeLong(nid);
        }
    }

    private static void writeSets(DataOutputStream out, ConcurrentHashMap<Long, Set<Long>> map) throws IOException {
        List<Map.Entry<Long, Set<Long>>> entries = new ArrayList<>(map.entrySet());
        out.writeInt(entries.size());
        for (Map.Entry<Long, Set<Long>> entry : entries) {
            List<Long> values = new ArrayList<>(entry.getValue());
            out.writeLong(entry.getKey());
            out.writeInt(values.size());
            for (long value : values) {
                out.writeLong(value);
            }
        }
    }

    private void read(DataInputStream in) throws IOException {
        DataKind[] kinds = DataKind.values();
        for (int i = in.readInt(); i > 0; i--) {
            moduleKinds.put(in.readLong(), kinds[in.readByte()]);
        }
        for (int i = in.readInt(); i > 0; i--) {
            stampModules.put(in.readLong(), in.readLong());
        }
        readSets(in, stampUse);
        readSets(in, referrers);
        for (int i = in.readInt(); i > 0; i--) {
            pending.add(in.readLong());
            pendingCount.incrementAndGet();
        }
    }

    private static void readSets(DataInputStream in, ConcurrentHashMap<Long, Set<Long>> map) throws IOException {
        for (int i = in.readInt(); i > 0; i--) {
            long key = in.readLong();
            Set<Long> set = ConcurrentHashMap.newKeySet();
            for (int j = in.readInt(); j > 0; j--) {
                set.add(in.readLong());
            }
            map.put(key, set);
        }
    }

    private static void deleteQuietly(File file) {
        try {
            Files.deleteIfExists(file.toPath());
        } catch (IOException e) {
            LOG.warn("Could not delete {}", file, e);
        }
    }
}
