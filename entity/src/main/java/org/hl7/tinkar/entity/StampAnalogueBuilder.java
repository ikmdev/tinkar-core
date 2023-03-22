package org.hl7.tinkar.entity;

public record StampAnalogueBuilder(StampRecord analogue,
                                   RecordListBuilder<StampVersionRecord> analogVersions) {
    public StampRecord build() {
        analogVersions.build();
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
