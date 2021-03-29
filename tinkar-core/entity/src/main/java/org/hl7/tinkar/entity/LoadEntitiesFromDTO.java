package org.hl7.tinkar.entity;

import org.hl7.tinkar.entity.internal.Get;
import org.hl7.tinkar.dto.ConceptChronologyDTO;
import org.hl7.tinkar.component.FieldDataType;
import org.hl7.tinkar.dto.TypePatternChronologyDTO;
import org.hl7.tinkar.dto.binary.TinkarInput;
import org.hl7.tinkar.common.util.time.Stopwatch;
import org.hl7.tinkar.dto.SemanticChronologyDTO;
import org.hl7.tinkar.terms.ConceptProxy;
import org.hl7.tinkar.terms.TinkarTerm;

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
        LOG.info("Loading entities from: " + importFile.getAbsolutePath());
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


            ConceptProxy DESCRIPTION_PATTERN =
                    ConceptProxy.make("Description pattern", UUID.fromString("a4de0039-2625-5842-8a4c-d1ce6aebf021"));
            ConceptProxy PATH_ORIGINS_PATTERN =
                    ConceptProxy.make("Path Origin pattern", UUID.fromString("70f89dd5-2cdb-59bb-bbaa-98527513547c"));

            int[] originNids = Get.entityService().entityNidsOfType(PATH_ORIGINS_PATTERN.nid());
            LOG.info("Origin nids: " + Arrays.toString(originNids));
            for (int originNid: originNids) {
                LOG.info("Origin semantic: \n    " + Get.entityService().getEntityFast(originNid));
            }
            int[] referencingSemanticNids = Get.entityService().semanticNidsForComponent(PATH_ORIGINS_PATTERN.nid());
            for (int referencingSemanticNid: referencingSemanticNids) {
                LOG.info("Semantic referencing PATH_ORIGINS_PATTERN: \n    " + Get.entityService().getEntityFast(referencingSemanticNid));
            }

            LOG.info("Trying type: DESCRIPTION_PATTERN");
            int[] referencingSemanticDescriptions = Get.entityService().semanticNidsForComponentOfType(PATH_ORIGINS_PATTERN.nid(), DESCRIPTION_PATTERN.nid());
            for (int descriptionNid: referencingSemanticDescriptions) {
                LOG.info("Semantic of type DESCRIPTION_PATTERN referencing PATH_ORIGINS_PATTERN: \n    " + Get.entityService().getEntityFast(descriptionNid));
                for (int acceptibilityNid: Get.entityService().semanticNidsForComponentOfType(descriptionNid, TinkarTerm.US_ENGLISH_DIALECT.nid())) {
                    LOG.info("  Acceptability US: \n    " + Get.entityService().getEntityFast(acceptibilityNid));
                }
                for (int acceptibilityNid: Get.entityService().semanticNidsForComponentOfType(descriptionNid, TinkarTerm.GB_ENGLISH_DIALECT.nid())) {
                    LOG.info("  Acceptability GB: \n    " + Get.entityService().getEntityFast(acceptibilityNid));
                }
            }

            LOG.info("Description pattern: \n    " + Get.entityService().getEntityFast(DESCRIPTION_PATTERN));



            return importCount.get();
        } finally {
            //Get.activeTasks().remove(this);
        }
    }

    public String report() {
        return "Imported: " + importCount + " items in: " + stopwatch.elapsedTime();
    }
}
