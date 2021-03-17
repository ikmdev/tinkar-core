package org.hl7.tinkar.entity;

import org.hl7.tinkar.entity.internal.Get;
import org.hl7.tinkar.lombok.dto.ConceptChronologyDTO;
import org.hl7.tinkar.component.FieldDataType;
import org.hl7.tinkar.lombok.dto.TypePatternChronologyDTO;
import org.hl7.tinkar.lombok.dto.binary.TinkarInput;
import org.hl7.tinkar.common.util.time.Stopwatch;
import org.hl7.tinkar.lombok.dto.SemanticChronologyDTO;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class LoadEntitiesFromDTO {
    protected static final Logger LOG = Logger.getLogger(LoadEntitiesFromDTO.class.getName());
    final File importFile;
    final AtomicInteger importCount = new AtomicInteger();
    final Stopwatch stopwatch = new Stopwatch();


    public LoadEntitiesFromDTO(File importFile) {
        this.importFile = importFile;
    }

    public Integer call() throws IOException {
        try {

            try (ZipFile zipFile = new ZipFile(importFile, Charset.forName("UTF-8"))) {
                ZipEntry tinkZipEntry = zipFile.getEntry("export.tink");
                TinkarInput tinkIn = new TinkarInput(zipFile.getInputStream(tinkZipEntry));
                LOG.info(":LoadEntitiesFromDTO: begin processing");

                while (true) {
                    FieldDataType fieldDataType = FieldDataType.fromToken(tinkIn.readByte());
                    switch (fieldDataType) {
                        case CONCEPT_CHRONOLOGY: {
                            ConceptChronologyDTO ccDTO = ConceptChronologyDTO.make(tinkIn);
                            Get.entityService().putChronology(ccDTO);
                            importCount.incrementAndGet();
                        }
                        break;
                        case SEMANTIC_CHRONOLOGY: {
                            SemanticChronologyDTO scDTO = SemanticChronologyDTO.make(tinkIn);
                            Get.entityService().putChronology(scDTO);
                            importCount.incrementAndGet();
                        }
                        break;
                        case TYPE_PATTERN_CHRONOLOGY: {
                            TypePatternChronologyDTO dsDTO = TypePatternChronologyDTO.make(tinkIn);
                            Get.entityService().putChronology(dsDTO);
                            importCount.incrementAndGet();
                        }
                        break;

                        default:
                            throw new UnsupportedOperationException("Can't handle fieldDataType: " + fieldDataType);

                    }
                }

            } catch (EOFException eof) {
                // continue, will autoclose.
            }
            stopwatch.end();
            LOG.info(report());
            ConceptProxy PATH_ORIGINS_ASSEMBLAGE =
                    new ConceptProxy("Path origins assemblage (SOLOR)", UUID.fromString("1239b874-41b4-32a1-981f-88b448829b4b"));
            ConceptProxy DESCRIPTION_ASSEMBLAGE = new ConceptProxy("Description assemblage", UUID.fromString("c9b9a4ac-3a1c-516c-bbef-3a13e30df27d"));
            ConceptProxy ENGLISH_LANGUAGE = new ConceptProxy("English language", UUID.fromString("06d905ea-c647-3af9-bfe5-2514e135b558"));

            int[] originNids = Get.entityService().entityNidsOfType(PATH_ORIGINS_ASSEMBLAGE.nid());
            LOG.info("Origin nids: " + Arrays.toString(originNids));
            for (int originNid: originNids) {
                LOG.info("Origin semantic: " + Get.entityService().getEntityFast(originNid));
            }
            int[] referencingSemanticNids = Get.entityService().semanticNidsForComponent(PATH_ORIGINS_ASSEMBLAGE.nid());
            for (int referencingSemanticNid: referencingSemanticNids) {
                LOG.info("Referencing semantic: " + Get.entityService().getEntityFast(referencingSemanticNid));
            }


            LOG.info("Trying type: DESCRIPTION_ASSEMBLAGE");
            int[] referencingSemanticNidsOfType = Get.entityService().semanticNidsForComponentOfType(PATH_ORIGINS_ASSEMBLAGE.nid(), DESCRIPTION_ASSEMBLAGE.nid());
            for (int referencingSemanticNid: referencingSemanticNidsOfType) {
                LOG.info("Referencing semantic of type: " + Get.entityService().getEntityFast(referencingSemanticNid));
            }

            LOG.info("Trying type: ENGLISH_LANGUAGE");
            int[] referencingSemanticNidsOfType2 = Get.entityService().semanticNidsForComponentOfType(PATH_ORIGINS_ASSEMBLAGE.nid(), ENGLISH_LANGUAGE.nid());
            for (int referencingSemanticNid: referencingSemanticNidsOfType2) {
                LOG.info("Referencing semantic of type: " + Get.entityService().getEntityFast(referencingSemanticNid));
            }

            return importCount.get();
        } finally {
            //Get.activeTasks().remove(this);
        }
    }

    public String report() {
        return "Imported: " + importCount + " items in: " + stopwatch.elapsedTime();
    }
}
