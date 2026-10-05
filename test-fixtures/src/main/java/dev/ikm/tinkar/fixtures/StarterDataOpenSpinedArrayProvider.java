package dev.ikm.tinkar.fixtures;

import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * JUnit 5 extension that opens an existing SpinedArray store and ensures
 * the IKE starter set is loaded (useful when a store exists but needs data).
 * <p>Store Type: SpinedArray (persistent, file-based)
 * Data Loaded: ike-starter-set-reasoned-pb.zip
 * Storage Location (default): target/spinedarrays/{TestClassName}
 * <p>You can still override behavior on a per-test basis using {@link WithKeyValueProvider}
 * for custom {@code dataPath}, {@code cleanOnStart}, or {@code importPath}.
 */
public class StarterDataOpenSpinedArrayProvider extends OpenSpinedArrayKeyValueProvider {

    @Override
    protected Config resolveConfig(ExtensionContext context) {
        Config cfg = super.resolveConfig(context);
        // Ensure we are using OPEN spined array and set friendly defaults for starter data
        cfg.controllerClass = TestConstants.OPEN_SPINED_ARRAY_STORE;
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
