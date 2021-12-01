package org.hl7.tinkar.entity;

public record SemanticAnalogueBuilder(SemanticRecord analogue,
                                      RecordListBuilder<SemanticVersionRecord> analogVersions) {
    /**
     * If there is a version with the same stamp as versionToAdd, it will be removed prior to adding the
     * new version so you don't get duplicate versions with the same stamp.
     *
     * @param versionToAdd
     * @return
     */
    public SemanticAnalogueBuilder with(SemanticVersionRecord versionToAdd) {
        return add(versionToAdd);
    }

    /**
     * If there is a version with the same stamp as versionToAdd, it will be removed prior to adding the
     * new version, so you don't get duplicate versions with the same stamp.
     *
     * @param versionToAdd
     * @return
     */
    public SemanticAnalogueBuilder add(SemanticVersionRecord versionToAdd) {
        remove(versionToAdd);
        analogVersions.add(new SemanticVersionRecord(analogue, versionToAdd.stampNid(), versionToAdd.fieldValues()));
        return this;
    }

    /**
     * Removal is based on equivalence of stampNid, not based on a deep equals.
     *
     * @param versionToRemove
     * @return
     */
    public SemanticAnalogueBuilder remove(SemanticVersionRecord versionToRemove) {
        analogVersions.removeIf(versionRecord -> versionRecord.stampNid() == versionToRemove.stampNid());
        return this;
    }

    /**
     * Removal is based on equivalence of stampNid, not based on a deep equals.
     *
     * @param versionToRemove
     * @return
     */
    public SemanticAnalogueBuilder without(SemanticVersionRecord versionToRemove) {
        return remove(versionToRemove);
    }

    public SemanticRecord build() {
        analogVersions.build();
        return analogue;
    }
}
