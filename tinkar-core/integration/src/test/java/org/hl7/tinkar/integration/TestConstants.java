package org.hl7.tinkar.integration;

import org.hl7.tinkar.common.service.ServiceKeys;
import org.hl7.tinkar.common.service.ServiceProperties;

import java.io.File;

public class TestConstants {
    public static final File TINK_TEST_FILE = new File(System.getProperty("user.dir"), "/src/test/resources/tinkar-export.zip");

    public static final File MVSTORE_ROOT = new File(System.getProperty("user.dir"), "/target/mvstore");
    public static final File SAP_ROOT = new File(System.getProperty("user.dir"), "/target/spinedarrays");

    public static final String MV_STORE_NAME = "MVStore";
    public static final String EPHEMERAL_STORE_NAME = "EphemeralStore";
    public static final String SAP_STORE_NAME = "SpinedArrayStore";
}
