package org.hl7.tinkar.integration;

import java.io.File;

public class TestConstants {
    public static final File TINK_TEST_FILE = new File(System.getProperty("user.dir"), "/target/data/tinkar-test-dto-1.1.0.zip");
    public static final File PB_TEST_FILE = new File(System.getProperty("user.dir"), "/target/data/tinkar-solor-us-export.pb-1.4..zip");
    public static final File PB_EXPORT_TEST_FILE = new File(System.getProperty("user.dir"), "/target/data/tinkar-export-test.pb.zip");

    public static final File MVSTORE_ROOT = new File(System.getProperty("user.dir"), "/target/mvstore");
    public static final File SAP_ROOT = new File(System.getProperty("user.dir"), "/target/spinedarrays");

    public static final String MV_STORE_OPEN_NAME = "Open MV Store";
    public static final String EPHEMERAL_STORE_NAME = "Load Ephemeral Store";
    public static final String SA_STORE_OPEN_NAME = "Open SpinedArrayStore";
}
