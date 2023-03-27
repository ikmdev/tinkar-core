package dev.ikm.tinkar.entity;

public record ConceptAnalogueBuilder(ConceptRecord analogue,
                                     RecordListBuilder<ConceptVersionRecord> analogVersions) {
    /**
     * If there is a version with the same stamp as versionToAdd, it will be removed prior to adding the
     * new version so you don't get duplicate versions with the same stamp.
     *
     * @param versionToAdd
     * @return
     */
    public ConceptAnalogueBuilder with(ConceptVersionRecord versionToAdd) {
        return add(versionToAdd);
    }

    /**
     * If there is a version with the same stamp as versionToAdd, it will be removed prior to adding the
     * new version, so you don't get duplicate versions with the same stamp.
     *
     * @param versionToAdd
     * @return
     */
    public ConceptAnalogueBuilder add(ConceptVersionRecord versionToAdd) {
        remove(versionToAdd);
        analogVersions.add(new ConceptVersionRecord(analogue, versionToAdd.stampNid()));
        return this;
    }

    /**
     * Removal is based on equivalence of stampNid, not based on a deep equals.
     *
     * @param versionToRemove
     * @return
     */
    public ConceptAnalogueBuilder remove(ConceptEntityVersion versionToRemove) {
        analogVersions.removeIf(conceptVersionRecord -> conceptVersionRecord.stampNid() == versionToRemove.stampNid());
        return this;
    }

    /**
     * Removal is based on equivalence of stampNid, not based on a deep equals.
     *
     * @param versionToRemove
     * @return
     */
    public ConceptAnalogueBuilder without(ConceptVersionRecord versionToRemove) {
        return remove(versionToRemove);
    }

    public ConceptRecord build() {
        analogVersions.build();
        return analogue;
    }
}
