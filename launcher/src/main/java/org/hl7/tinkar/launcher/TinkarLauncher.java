package org.hl7.tinkar.launcher;

import io.activej.inject.module.Module;
import io.activej.inject.module.ModuleBuilder;
import io.activej.launcher.Launcher;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hl7.tinkar.component.Chronology;
import org.hl7.tinkar.component.ChronologyService;

import java.util.Optional;
import java.util.ServiceLoader;
import java.util.UUID;

/**
 * TODO: https://vividcode.io/package-java-applications-using-jpackage-in-jdk-14/
 */
public class TinkarLauncher extends Launcher {
    public static final Logger LOG = LogManager.getLogger();

    @Override
    protected void run() throws Exception {
        LOG.info("Tinkar");
        System.out.println("Tinkar System.out");
    }

    // this method can be overridden by subclasses which extend Launcher,
    // provides business logic modules (for example, ConfigModule)
    @Override
    protected Module getModule() {
        return  ModuleBuilder.create().bind(ChronologyService.class).build();
    }

    public static void main(String[] args) throws Exception {
        // Example using ServiceLoader...
        ServiceLoader<ChronologyService> chronologyServiceLoader = ServiceLoader.load(ChronologyService.class);
        chronologyServiceLoader.forEach(chronologyService -> {
            System.out.println("chronologyService: " + chronologyService);
        });
        Optional<ChronologyService> chronologyServiceOptional = chronologyServiceLoader.findFirst();
        System.out.println("chronologyServiceOptional: " + chronologyServiceOptional);
        chronologyServiceOptional.ifPresent((chronologyService) -> {
            Optional<Chronology> optionalC = chronologyService.getChronology(UUID.randomUUID());
            System.out.println("Service Loaded: chronologyOptional: " + optionalC + "\n\n");
        });
        
    }
}
