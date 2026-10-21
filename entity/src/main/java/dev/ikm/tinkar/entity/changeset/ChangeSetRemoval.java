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
package dev.ikm.tinkar.entity.changeset;

import dev.ikm.tinkar.schema.TinkarMsg;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * A change set written again without some components' records (IKE-Network/ike-issues#1313):
 * how a deleted scope leaves the instance and ephemeral change sets, which otherwise keep its
 * records. A format-3 change set is expanded to the format-2 layout, its records are copied but
 * those of the removed components, and the rest is compacted to format 3 again; a format-2 one
 * is filtered and compacted. The file is replaced only once the new one is whole, by a move in
 * its own folder, and deleted when nothing is left in it. One untouched by the removal stays as
 * it is, byte for byte.
 *
 * <p>A record is removed when any UUID of its component's public id is among those given, so a
 * component known by several UUIDs goes whole. A kept record that references a removed
 * component would be left dangling; a scope delete refuses before that can happen.
 */
public final class ChangeSetRemoval {

    /**
     * What the rewrite did to one change set.
     *
     * @param file    the change set
     * @param kept    the records kept
     * @param removed the records removed
     * @param deleted whether the file was deleted, nothing being left in it
     */
    public record Result(File file, long kept, long removed, boolean deleted) {

        /** @return whether the file was written again without the removed records */
        public boolean rewritten() {
            return removed > 0 && !deleted;
        }
    }

    private ChangeSetRemoval() {
    }

    /**
     * Writes a change set again without the records of the given components.
     *
     * @param changeSet  the change set, format 2 or 3
     * @param components the UUIDs of the components to remove
     * @return what was done
     * @throws IOException when the change set cannot be read or written
     */
    public static Result remove(File changeSet, Set<UUID> components) throws IOException {
        Path folder = changeSet.getAbsoluteFile().toPath().getParent();
        Path work = Files.createTempDirectory(folder, ".removal-");
        try {
            File records = changeSet;
            int version;
            try (ZipFile zip = new ZipFile(changeSet)) {
                Manifest manifest = ChangeSetFormat.manifest(zip)
                        .orElseThrow(() -> new IllegalArgumentException(changeSet.getName() + " has no manifest"));
                version = ChangeSetFormat.version(manifest.getMainAttributes(), changeSet.getName());
            }
            if (version < ChangeSetFormat.IDENTITY_INDEX_VERSION) {
                throw new IllegalArgumentException(changeSet.getName() + " is in format version " + version
                        + ", whose UUIDs are text; it is not rewritten");
            }
            if (version >= ChangeSetFormat.CURRENT_VERSION) {
                records = work.resolve("expanded.zip").toFile();
                try {
                    new ChangeSetExpansion(changeSet, records).call();
                } catch (Exception e) {
                    throw new IOException("Expanding " + changeSet.getName() + " failed", e);
                }
            }
            File filtered = work.resolve("filtered.zip").toFile();
            long kept = 0;
            long removed = 0;
            long[] byKind = new long[4];
            try (ZipFile zip = new ZipFile(records);
                 ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(filtered.toPath()), 1 << 20));
                 IdentityIndex.Writer identities = new IdentityIndex.Writer(false)) {
                Manifest manifest = ChangeSetFormat.manifest(zip).orElseThrow();
                ZipEntry entry = zip.getEntry(ChangeSetFormat.IDENTITY_INDEX_RECORDS);
                zos.putNextEntry(new ZipEntry(ChangeSetFormat.IDENTITY_INDEX_RECORDS));
                if (entry != null) {
                    try (InputStream in = ChangeSetFormat.openRecords(zip, entry)) {
                        TinkarMsg record;
                        while ((record = TinkarMsg.parseDelimitedFrom(in)) != null) {
                            if (removes(record, components)) {
                                removed++;
                                continue;
                            }
                            record.writeDelimitedTo(zos);
                            identities.add(record);
                            byKind[ChangeSetWriter.kindIndex(record)]++;
                            kept++;
                        }
                    }
                }
                zos.closeEntry();
                identities.writeTo(zos);
                zos.putNextEntry(new ZipEntry(ChangeSetFormat.MANIFEST));
                zos.write(ChangeSetWriter.identityIndexManifestContent(ChangeSetWriter.counts(byKind), ChangeSetWriter.sections(manifest),
                        ChangeSetFormat.vertexIdentityDerived(manifest.getMainAttributes())).getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
            if (removed == 0) {
                return new Result(changeSet, kept, 0, false);
            }
            if (kept == 0) {
                Files.delete(changeSet.toPath());
                return new Result(changeSet, 0, removed, true);
            }
            File compacted = work.resolve("compacted.zip").toFile();
            try {
                new ChangeSetCompaction(filtered, compacted).call();
            } catch (Exception e) {
                throw new IOException("Compacting " + changeSet.getName() + " without the removed records failed", e);
            }
            Files.move(compacted.toPath(), changeSet.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return new Result(changeSet, kept, removed, false);
        } finally {
            try (Stream<Path> paths = Files.walk(work)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
            }
        }
    }

    /** Whether a record is of a removed component: any UUID of its public id is among them. */
    static boolean removes(TinkarMsg record, Set<UUID> components) {
        for (UUID uuid : SchemaIds.uuids(ChangeSetFormat.componentOf(record))) {
            if (components.contains(uuid)) {
                return true;
            }
        }
        return false;
    }
}
