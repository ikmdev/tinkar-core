package org.hl7.tinkar.entity.load;

import org.hl7.tinkar.common.service.Executor;
import org.hl7.tinkar.common.service.TrackingCallable;
import org.hl7.tinkar.common.util.io.CountingInputStream;
import org.hl7.tinkar.component.Chronology;
import org.hl7.tinkar.component.FieldDataType;
import org.hl7.tinkar.dto.ConceptChronologyDTO;
import org.hl7.tinkar.dto.PatternChronologyDTO;
import org.hl7.tinkar.dto.SemanticChronologyDTO;
import org.hl7.tinkar.dto.binary.TinkarInput;
import org.hl7.tinkar.entity.Entity;
import org.hl7.tinkar.entity.EntityService;

import java.io.*;
import java.nio.charset.Charset;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class LoadEntitiesFromDtoFile extends TrackingCallable<Integer> {
    protected static final Logger LOG = Logger.getLogger(LoadEntitiesFromDtoFile.class.getName());
    private static final int MAX_TASK_COUNT = 100;
    final File importFile;
    final AtomicInteger importCount = new AtomicInteger();
    Semaphore runningTasks = new Semaphore(MAX_TASK_COUNT, false);
    ConcurrentSkipListSet<ExceptionRecord> exceptionRecords = new ConcurrentSkipListSet<>();

    public LoadEntitiesFromDtoFile(File importFile) {
        super(false, true);
        this.importFile = importFile;
        LOG.info("Loading entities from: " + importFile.getAbsolutePath());
    }

    public Integer compute() throws IOException {
        updateTitle("Loading " + importFile.getName());
        LOG.info(getTitle());

        double sizeForAll = 0;
        try (ZipFile zipFile = new ZipFile(importFile, Charset.forName("UTF-8"))) {
            ZipEntry tinkZipEntry = zipFile.getEntry("export.tink");
            double totalSize = tinkZipEntry.getSize();
            sizeForAll += totalSize;
            CountingInputStream countingInputStream = new CountingInputStream(zipFile.getInputStream(tinkZipEntry));
            TinkarInput tinkIn = new TinkarInput(countingInputStream);
            LOG.info(":LoadEntitiesFromDTO: begin processing");

            while (!isCancelled()) {
                if (updateIntervalElapsed()) {
                    updateTitle("Loading " + importFile.getName());
                    updateProgress(countingInputStream.getBytesRead(), totalSize);
                    updateMessage(String.format("Count: %,d   " + estimateTimeRemainingString(), importCount.get()));
                }

                FieldDataType fieldDataType = FieldDataType.fromToken(tinkIn.readByte());
                runningTasks.acquireUninterruptibly();
                switch (fieldDataType) {
                    case CONCEPT_CHRONOLOGY: {
                        Executor.threadPool().execute(new PutChronology(ConceptChronologyDTO.make(tinkIn)));
                        importCount.incrementAndGet();
                    }
                    break;
                    case SEMANTIC_CHRONOLOGY: {
                        Executor.threadPool().execute(new PutChronology(SemanticChronologyDTO.make(tinkIn)));
                        importCount.incrementAndGet();
                    }
                    break;
                    case PATTERN_CHRONOLOGY: {
                        Executor.threadPool().execute(new PutChronology(PatternChronologyDTO.make(tinkIn)));
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
        runningTasks.acquireUninterruptibly(MAX_TASK_COUNT);
        LOG.info(report());
        updateProgress(sizeForAll, sizeForAll);
        updateMessage(String.format("Imported %,d items in " + durationString(), importCount.get()));
        updateTitle("Loaded from " + importFile.getName());

        return importCount.get();
    }

    public String report() {
        StringBuilder sb = new StringBuilder();
        sb.append("Imported: " + importCount + " items in: " + durationString() + " with " + exceptionRecords.size() + " exceptions.");
        sb.append("\n");
        exceptionRecords.forEach(exceptionRecord -> {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            exceptionRecord.t.printStackTrace(pw);
            sb.append(exceptionRecord.t.getLocalizedMessage()).append(" for ").append(exceptionRecord.chronology).append("\n").append(sw.toString()).append("\n");
        });
        return sb.toString();
    }

    private static record ExceptionRecord(Chronology chronology, Throwable t) implements Comparable<ExceptionRecord> {
        @Override
        public int compareTo(LoadEntitiesFromDtoFile.ExceptionRecord o) {
            return chronology.publicId().compareTo(o.chronology().publicId());
        }
    }

    private class PutChronology implements Runnable {
        final Chronology chronology;

        public PutChronology(Chronology chronology) {
            this.chronology = chronology;
        }

        @Override
        public void run() {
            try {
                EntityService.get().putChronology(chronology);
            } catch (Throwable e) {
                e.printStackTrace();
                exceptionRecords.add(new ExceptionRecord(chronology, e));
            } finally {
                runningTasks.release();
            }
        }
    }
}
