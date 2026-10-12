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
package dev.ikm.tinkar.entity;

public record StampAnalogueBuilder(StampRecord analogue,
                                   RecordListBuilder<StampVersionRecord> analogVersions) {
    /**
     * Builds the stamp, refusing a shape a stamp may not have: more than two versions, two
     * uncommitted placeholders, or two terminal versions. A stamp is born uncommitted and ends
     * committed or canceled, one more version; born with its time, it is committed with one
     * version and never changes. Anything else is a programming error caught here, at the one
     * place a version joins a stamp on the write path.
     *
     * @return the stamp
     * @throws IllegalStateException if the versions do not form a legal stamp
     */
    public StampRecord build() {
        analogVersions.build();
        int placeholders = 0;
        int terminals = 0;
        for (StampVersionRecord version : analogue.versions()) {
            if (version.time() == Long.MAX_VALUE) {
                placeholders++;
            } else {
                terminals++;
            }
        }
        if (placeholders > 1 || terminals > 1) {
            throw new IllegalStateException("Stamp " + analogue.publicId() + " would hold " + analogue.versions().size()
                    + " versions at times " + analogue.versions().collect(StampVersionRecord::time)
                    + ": a stamp has at most two, the uncommitted placeholder and one committed or canceled version");
        }
        return analogue;
    }

    public StampAnalogueBuilder with(StampVersionRecord versionRecord) {
        return add(versionRecord);
    }

    public StampAnalogueBuilder add(StampVersionRecord versionToAdd) {
        analogVersions.add(new StampVersionRecord(analogue, versionToAdd.stateNid(), versionToAdd.time(),
                versionToAdd.authorNid(), versionToAdd.moduleNid(), versionToAdd.pathNid()));
        return this;
    }

    public StampAnalogueBuilder without(StampVersionRecord versionRecord) {
        return remove(versionRecord);
    }

    public StampAnalogueBuilder remove(StampEntityVersion version) {
        analogVersions.removeIf(stampVersionRecord -> stampVersionRecord.stampNid() == version.stampNid());
        return this;
    }
}
