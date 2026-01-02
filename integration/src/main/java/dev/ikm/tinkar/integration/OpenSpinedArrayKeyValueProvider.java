package dev.ikm.tinkar.integration;

import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Type-safe JUnit 5 extension for opening an existing SpinedArray store.
 * <p>
 * Prefer this class when you want discoverable, IDE-friendly configuration via
 * {@code @ExtendWith(OpenSpinedArrayKeyValueProvider.class)}. You can still
 * refine behavior with {@link WithKeyValueProvider} on the test class
 * (e.g., to set {@code dataPath}, {@code cleanOnStart}, or {@code importPath}).
 * <p>
 * Defaults:
 * - Forces controller {@code TestConstants.OPEN_SPINED_ARRAY_STORE}
 * - Defaults {@code dataPath} to {@code target/spinedarrays/{TestClassName}} if not specified
 */
public class OpenSpinedArrayKeyValueProvider extends KeyValueProviderExtension {

    @Override
    protected Config resolveConfig(ExtensionContext context) {
        Config cfg = super.resolveConfig(context);
        // Use type-safe controller class; keep any test-level overrides for dataPath/cleanOnStart/importPath
        cfg.controllerClass = TestConstants.OPEN_SPINED_ARRAY_STORE;
        if (cfg.dataPath == null || cfg.dataPath.isBlank()) {
            String testClassName = context.getRequiredTestClass().getSimpleName();
            cfg.dataPath = "target/spinedarrays/" + testClassName;
        }
        return cfg;
    }
}