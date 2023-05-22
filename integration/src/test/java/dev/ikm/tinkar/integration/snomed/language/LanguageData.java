package dev.ikm.tinkar.integration.snomed.language;

public class LanguageData {
    private String id;
    private String effectiveTime;
    private int active;

    private String moduleId;
    private String refsetId;
    private String referencedComponentId;
    private String acceptabilityId;


    public LanguageData(String input) {
        String[] row = input.split(("\t"));
        this.id = row[0];
        this.effectiveTime = row[1];
        this.active = Integer.parseInt(row[2]);
        this.moduleId = row[3];
        this.refsetId = row[4];
        this.referencedComponentId = row[5];
        this.acceptabilityId = row[6];
    }

    @Override
    public String toString() {
        return "LanguageData{" +
                "id='" + id + '\'' +
                ", effectiveTime='" + effectiveTime + '\'' +
                ", active=" + active +
                ", moduleId='" + moduleId + '\'' +
                ", refsetId='" + refsetId + '\'' +
                ", referencedComponentId='" + referencedComponentId + '\'' +
                ", acceptabilityId='" + acceptabilityId + '\'' +
                '}';
    }


    public String getId() {
        return id;
    }

    public String getEffectiveTime() {
        return effectiveTime;
    }

    public int getActive() {
        return active;
    }

    public String getModuleId() {
        return moduleId;
    }

    public String getRefsetId() {
        return refsetId;
    }

    public String getReferencedComponentId() {
        return referencedComponentId;
    }

    public String getAcceptabilityId() {
        return acceptabilityId;
    }
}


