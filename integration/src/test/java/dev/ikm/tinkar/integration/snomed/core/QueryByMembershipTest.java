package dev.ikm.tinkar.integration.snomed.core;

import dev.ikm.tinkar.common.service.CachingService;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.ServiceKeys;
import dev.ikm.tinkar.common.service.ServiceProperties;
import dev.ikm.tinkar.coordinate.stamp.StampCoordinateRecord;
import dev.ikm.tinkar.coordinate.stamp.StateSet;
import dev.ikm.tinkar.coordinate.stamp.calculator.Latest;
import dev.ikm.tinkar.entity.*;
import dev.ikm.tinkar.entity.export.ExportEntitiesToProtobufFile;
import dev.ikm.tinkar.entity.transfom.EntityToTinkarSchemaTransformer;
import dev.ikm.tinkar.schema.TinkarMsg;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class QueryByMembershipTest {


    private static final Logger LOG =  Logger.getLogger(QueryByMembershipTest.class.getSimpleName());

    private static final String CONTROLLER_NAME =  TestConstants.OPEN_SPINED_ARRAY_STORE;

    private static final File DATA_STORE_FILE = new File(System.getProperty("user.home") + "/Solor/snomed-starter-data");

    private static final File DATA_STORE_FILE2 = new File(System.getProperty("user.home") + "/Solor/membership-filter");

    private final EntityToTinkarSchemaTransformer entityTransformer = EntityToTinkarSchemaTransformer.getInstance();

    final AtomicInteger exportPatternCount = new AtomicInteger();

    final AtomicInteger exportConceptCount = new AtomicInteger();
    final AtomicInteger exportSemanticCount = new AtomicInteger();

    final AtomicInteger exportStampCount = new AtomicInteger();

    final AtomicInteger nullEntities = new AtomicInteger(0);

    static final int VERBOSE_ERROR_COUNT = 10;

    public void initialize(File datastore) throws IOException {
        LOG.info("Starting database");
        LOG.info("Loading data from " + DATA_STORE_FILE.getAbsolutePath());
        CachingService.clearAll();
        ServiceProperties.set(ServiceKeys.DATA_STORE_ROOT, datastore);
        PrimitiveData.selectControllerByName(CONTROLLER_NAME);
        PrimitiveData.start();
    }

    @Test
    @DisplayName("Exporting filtered entities to  protobuf file")
  //  @Disabled("Enable the tests after figuring out the way to load the DataStores in Jenkins. or test directory")
    @Order(1)
   public void exportoProtobufTest() throws IOException {
       initialize(DATA_STORE_FILE);
        AtomicInteger verboseErrors = new AtomicInteger(0);
        int patternNidMembership = EntityService.get().nidForPublicId(TinkarTerm.PATHS_PATTERN);
        Set<Entity<? extends EntityVersion>> entitiesFromMembership = filterbyMembership(patternNidMembership);   //input pattern nid
     //   Set<PatternEntity<PatternEntityVersion>> membershipPatterns = filterbyMembership1();
        //  ExportEntitiesToProtobufFile exportEntitiesToProtobufFile = new ExportEntitiesToProtobufFile(new File(DATA_STORE_FILE.getPath()+".pb.zip"));
      //  exportedEntitiesCount = exportEntitiesToProtobufFile.compute();
        File protobufFile = new File(System.getProperty("user.home") + "/Solor/membership-filter.pb.zip");
        try(FileOutputStream fileOutputStream = new FileOutputStream(protobufFile);
            BufferedOutputStream bos = new BufferedOutputStream(fileOutputStream);
            ZipOutputStream zos = new ZipOutputStream(bos)) {
            ZipEntry zipEntry = new ZipEntry(protobufFile.getName().replace(".zip", ""));
            zos.putNextEntry(zipEntry);
            entitiesFromMembership.forEach(entity -> {
                try {
            //        Entity<? extends EntityVersion> patternEntity = EntityService.get().getEntityFast(entity);
                    if(entity != null){
                        TinkarMsg pbTinkarMsg = entityTransformer.transform(entity);
                        pbTinkarMsg.writeDelimitedTo(zos);
                        switch(entity)
                        {
                            case StampEntity stamp ->{ exportStampCount.incrementAndGet(); }
                            case PatternEntity pattern ->{exportPatternCount.incrementAndGet();}
                            case ConceptEntity concept->{exportConceptCount.incrementAndGet();}
                            case SemanticEntity semanticntic->{exportSemanticCount.incrementAndGet();}

                            default -> throw new IllegalStateException("Unexpected value: " + entity);
                        }
                  //      if(entity instanceof StampEntity<V>)
                 //       exportPatternCount.incrementAndGet();
                    } else {
                        nullEntities.incrementAndGet();
                        if (verboseErrors.get() < VERBOSE_ERROR_COUNT) {
                            LOG.warning("No pattern entity for: " + entity);
                            verboseErrors.incrementAndGet();
                        }
                    }
                }catch (UnsupportedOperationException | IllegalStateException exception){
                    LOG.info("Processing patternNid: " + entity);
                    exception.printStackTrace();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

            });

            LOG.info("Zip entry size: " + zipEntry.getSize());
            // finalize zip file
            zos.closeEntry();
            zos.flush();

        } catch (Throwable e) {
            e.printStackTrace();
        }
        LOG.info("Exported Semantic count: "+exportSemanticCount);
        LOG.info("Exported Stamp count: "+exportStampCount);
        LOG.info("Exported Pattern count: "+exportPatternCount);
        LOG.info("Exported Concept count: "+exportConceptCount);
        PrimitiveData.stop();

   }

    public Set<Entity<? extends EntityVersion>> filterbyMembership(int patternNid){
        Set<Entity<? extends EntityVersion>> entitySet = new HashSet<>();

        EntityService.get().forEachSemanticOfPattern(patternNid,semanticEntity -> {
            entitySet.add(semanticEntity.referencedComponent());
            LOG.info("Concepts: " + semanticEntity.referencedComponent());//Concepts
            semanticEntity.referencedComponent().stampNids().forEach(stampNid ->{        //Stamps for concepts
                entitySet.add(EntityService.get().getStampFast(stampNid));
                LOG.info("Stamps for concept: " + EntityService.get().getStampFast(stampNid));
            });


            System.out.println ("Print patterns:  " + semanticEntity.pattern());
            entitySet.add(semanticEntity.pattern());    //Adds patterns to the Hashset. Duplicates are avoided

            semanticEntity.stampNids().forEach(stampNid -> {                  //Stamps for Semantics
                StampEntity<? extends StampEntityVersion> stamp = EntityService.get().getStampFast(stampNid);
                entitySet.add(stamp);
                LOG.info("Stamps for semantics: " + stamp);});
            EntityService.get().forEachSemanticForComponent(semanticEntity.referencedComponent().nid(),entity->{
                entitySet.add(entity);
                entity.pattern().stampNids().forEach(     //Adds Stamps for pattern

                        stampNid->{ entitySet.add(EntityService.get().getStampFast(stampNid));
                            LOG.info("Stamps for patterns: " + EntityService.get().getStampFast(stampNid));});
                    });
            });


        return entitySet;
    }

}