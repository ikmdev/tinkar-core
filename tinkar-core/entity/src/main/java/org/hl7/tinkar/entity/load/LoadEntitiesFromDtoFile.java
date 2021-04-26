package org.hl7.tinkar.entity.load;

import org.hl7.tinkar.common.service.TrackingCallable;
import org.hl7.tinkar.common.util.io.CountingInputStream;
import org.hl7.tinkar.entity.internal.Get;
import org.hl7.tinkar.dto.ConceptChronologyDTO;
import org.hl7.tinkar.component.FieldDataType;
import org.hl7.tinkar.dto.PatternChronologyDTO;
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

public class LoadEntitiesFromDtoFile extends TrackingCallable<Integer> {
    protected static final Logger LOG = Logger.getLogger(LoadEntitiesFromDtoFile.class.getName());
    final File importFile;
    final AtomicInteger importCount = new AtomicInteger();


    public LoadEntitiesFromDtoFile(File importFile) {
        this.importFile = importFile;
        LOG.info("Loading entities from: " + importFile.getAbsolutePath());
        this.setRetainWhenComplete(true);
    }

    public Integer compute() throws IOException {
        updateTitle("Loading " + importFile.getName());
        double sizeForAll = 0;
        try (ZipFile zipFile = new ZipFile(importFile, Charset.forName("UTF-8"))) {
            ZipEntry tinkZipEntry = zipFile.getEntry("export.tink");
            double totalSize = tinkZipEntry.getSize();
            sizeForAll += totalSize;
            CountingInputStream countingInputStream = new CountingInputStream(zipFile.getInputStream(tinkZipEntry));
            TinkarInput tinkIn = new TinkarInput(countingInputStream);
            LOG.info(":LoadEntitiesFromDTO: begin processing");


            while (!isCanceled()) {
                if (updateIntervalElapsed()) {
                    updateProgress(countingInputStream.getBytesRead(), totalSize);
                    updateMessage(String.format("Count: %,d   " + estimateTimeRemainingString(), importCount.get()));
                    updateTitle("Loading " + importFile.getName());
                }

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
                    case PATTERN_CHRONOLOGY: {
                        PatternChronologyDTO dsDTO = PatternChronologyDTO.make(tinkIn);
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
        LOG.info(report());
        updateProgress(sizeForAll, sizeForAll);
        updateMessage(String.format("Imported %,d items in " + durationString(), importCount.get()));
        updateTitle("Loaded from " + importFile.getName());

        return importCount.get();
    }

    public String report() {
        return "Imported: " + importCount + " items in: " + durationString();
    }
}
