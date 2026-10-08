package dev.ikm.tinkar.fixtures;

import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * JUnit 5 extension that loads the IKE starter set into SpinedArray store.
 * <p><b>Store Type:</b> SpinedArray (persistent, file-based)
 * <br>
 * <b>Data Loaded:</b> ike-starter-set-reasoned-pb.zip
 * <br>
 * <b>Storage Location:</b> target/spinedarrays
 */
public class StarterDataSpinedArrayProvider extends NewSpinedArrayKeyValueProvider {

    @Override
    protected Config resolveConfig(ExtensionContext context) {
        Config cfg = super.resolveConfig(context);
        // Ensure we are using NEW spined array and set defaults suitable for starter data
        cfg.controllerClass = TestConstants.NEW_SPINED_ARRAY_STORE;
        if (cfg.dataPath == null || cfg.dataPath.isBlank()) {
            String testClassName = context.getRequiredTestClass().getSimpleName();
            cfg.dataPath = "target/spinedarrays/" + testClassName;
        }
        if (cfg.importPath == null || cfg.importPath.isBlank()) {
            cfg.importPath = "target/data/ike-starter-set-reasoned-pb.zip";
        }
        return cfg;
    }
}