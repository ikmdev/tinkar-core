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
import org.hl7.tinkar.entity.EntityService;
import org.hl7.tinkar.protobuf.PBConcept;
import org.hl7.tinkar.protobuf.PBConceptChronology;
import org.hl7.tinkar.protobuf.PBTinkarMsg;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class LoadEntitiesFromPBFile extends TrackingCallable<Integer> {
    protected static final Logger LOG = Logger.getLogger(LoadEntitiesFromPBFile.class.getName());
    private static final int MAX_TASK_COUNT = 100;
    final File importFile;
    final AtomicInteger importCount = new AtomicInteger();
    Semaphore runningTasks = new Semaphore(MAX_TASK_COUNT, false);
    ConcurrentSkipListSet<ExceptionRecord> exceptionRecords = new ConcurrentSkipListSet<>();

    public LoadEntitiesFromPBFile(File importFile) {
        super(false, true);
        this.importFile = importFile;
        LOG.info("Loading entities from: " + importFile.getAbsolutePath());
    }

    public Integer compute() throws IOException {
        updateTitle("Loading " + importFile.getName());
        LOG.info(getTitle());

        try (ZipFile zipFile = new ZipFile(importFile, StandardCharsets.UTF_8)) {
            ZipEntry exportPBEntry = zipFile.getEntry("export.pb");
            DataInputStream pbStream = new DataInputStream(zipFile.getInputStream(exportPBEntry));
            LOG.info(":LoadEntitiesFromDTO: begin processing");

            ByteBuffer byteBuffer;
            int pbMessageLength;
            int bytesReadCount;

            while(pbStream.available() > 0){
                bytesReadCount = 0;
                pbMessageLength = pbStream.readInt();

                if(pbMessageLength == -1){
                    break;
                }
                byteBuffer = ByteBuffer.allocate(pbMessageLength);

                while(bytesReadCount < pbMessageLength){
                    int sourceIndex = bytesReadCount;
                    byte[] bytesRead;

                    if(bytesReadCount == 0){
                        bytesRead = new byte[pbMessageLength];
                        bytesReadCount = pbStream.read(bytesRead, 0, pbMessageLength);
                    }else {
                        int lengthLeftToRead = pbMessageLength - bytesReadCount;
                        bytesRead = new byte[lengthLeftToRead];
                        bytesReadCount += pbStream.read(bytesRead, 0, lengthLeftToRead);
                    }
                    byteBuffer.put(sourceIndex, bytesRead);
                }

                PBTinkarMsg pbTinkarMsg = PBTinkarMsg.parseFrom(byteBuffer.array());

                switch (pbTinkarMsg.getValueCase().getNumber()){
                    case 10: //PBConcept ConceptValue = 10;
                        PBConcept pbConcept = pbTinkarMsg.getConceptValue();


                        break;
                    case 11: //PBConceptChronology ConceptChronologyValue = 11;
                        PBConceptChronology pbConceptChronology = pbTinkarMsg.getConceptChronologyValue();
                        break;
                    case 12: //PBConceptVersion ConceptVersionValue = 12;
                        break;
                    case 20: //PBSemantic SemanticValue = 20;
                        break;
                    case 21: //PBSemanticChronology SemanticChronologyValue = 21;
                        break;
                    case 22: //PBSemanticVersion SemanticVersionValue = 22;
                        break;
                    case 23: //PBPattern PatternValue = 23;
                        break;
                    case 24: //PBPatternChronology PatternChronologyValue = 24;
                        break;
                    case 25: //PBPatternVersion PatternVersionValue = 25;
                        break;
                    default:
                        break;
                }
            }
        } catch (EOFException exception) {
        }

        runningTasks.acquireUninterruptibly(MAX_TASK_COUNT);
        LOG.info(report());

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
        public int compareTo(LoadEntitiesFromPBFile.ExceptionRecord o) {
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
                System.exit(0);
            } finally {
                runningTasks.release();
            }
        }
    }
}
