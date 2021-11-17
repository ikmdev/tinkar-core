package org.hl7.tinkar.integration.provider.mvstore;


import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.common.service.ServiceKeys;
import org.hl7.tinkar.common.service.ServiceProperties;
import org.hl7.tinkar.common.util.time.Stopwatch;
import org.hl7.tinkar.entity.load.LoadEntitiesFromDtoFile;
import org.hl7.tinkar.entity.util.EntityCounter;
import org.hl7.tinkar.entity.util.EntityProcessor;
import org.hl7.tinkar.entity.util.EntityRealizer;
import org.hl7.tinkar.integration.TestConstants;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Concerned that MVStore may not be "bullet proof" based on this exception. Will watch, and save for posterity.
 * <p>
 * Mar 28, 2021 9:41:28 AM org.hl7.tinkar.integration.provider.mvstore.MVStoreProviderTest teardownSuite
 * INFO: teardownSuite
 * <p>
 * java.lang.IllegalStateException: Chunk 77 not found [1.4.200/9]
 * <p>
 * at org.h2.mvstore.DataUtils.newIllegalStateException(DataUtils.java:950)
 * at org.h2.mvstore.MVStore.getChunk(MVStore.java:1230)
 * at org.h2.mvstore.MVStore.readBufferForPage(MVStore.java:1214)
 * at org.h2.mvstore.MVStore.readPage(MVStore.java:2209)
 * at org.h2.mvstore.MVMap.readPage(MVMap.java:672)
 * at org.h2.mvstore.Page$NonLeaf.getChildPage(Page.java:1043)
 * at org.h2.mvstore.Cursor.hasNext(Cursor.java:53)
 * at org.h2.mvstore.MVMap$2$1.hasNext(MVMap.java:802)
 * at java.base/java.util.concurrent.ConcurrentMap.forEach(ConcurrentMap.java:112)
 * at org.hl7.tinkar.provider.mvstore.MVStoreProvider.forEachSemanticNidForComponentOfType(MVStoreProvider.java:98)
 * <p>
 * ALSO an additional intermittent error:
 * <p>
 * [ERROR] org.hl7.tinkar.integration.coordinate.TestCoordinates.computeLatest  Time elapsed: 0.033 s  <<< ERROR!
 * java.lang.ClassCastException: class org.hl7.tinkar.entity.ConceptRecord cannot be cast to class org.hl7.tinkar.entity.SemanticEntity (org.hl7.tinkar.entity.ConceptRecord and org.hl7.tinkar.entity.SemanticEntity are in module org.hl7.tinkar.entity@1.0-SNAPSHOT of loader 'app')
 * at org.hl7.tinkar.provider.entity@1.0-SNAPSHOT/org.hl7.tinkar.provider.entity.EntityProvider.lambda$textFast$0(EntityProvider.java:68)
 * at org.hl7.tinkar.caffeine@3.0.2/com.github.benmanes.caffeine.cache.BoundedLocalCache.lambda$doComputeIfAbsent$13(BoundedLocalCache.java:2439)
 * at java.base/java.util.concurrent.ConcurrentHashMap.compute(ConcurrentHashMap.java:1955)
 * at org.hl7.tinkar.caffeine@3.0.2/com.github.benmanes.caffeine.cache.BoundedLocalCache.doComputeIfAbsent(BoundedLocalCache.java:2437)
 * at org.hl7.tinkar.caffeine@3.0.2/com.github.benmanes.caffeine.cache.BoundedLocalCache.computeIfAbsent(BoundedLocalCache.java:2420)
 * at org.hl7.tinkar.caffeine@3.0.2/com.github.benmanes.caffeine.cache.LocalCache.computeIfAbsent(LocalCache.java:104)
 * at org.hl7.tinkar.caffeine@3.0.2/com.github.benmanes.caffeine.cache.LocalManualCache.get(LocalManualCache.java:62)
 * at org.hl7.tinkar.provider.entity@1.0-SNAPSHOT/org.hl7.tinkar.provider.entity.EntityProvider.textFast(EntityProvider.java:63)
 * at org.hl7.tinkar.common@1.0-SNAPSHOT/org.hl7.tinkar.common.service.DefaultDescriptionForNidService.textOptional(DefaultDescriptionForNidService.java:27)
 * at org.hl7.tinkar.common@1.0-SNAPSHOT/org.hl7.tinkar.common.service.PrimitiveData.textOptional(PrimitiveData.java:127)
 * at org.hl7.tinkar.common@1.0-SNAPSHOT/org.hl7.tinkar.common.service.PrimitiveData.text(PrimitiveData.java:119)
 * at org.hl7.tinkar.entity@1.0-SNAPSHOT/org.hl7.tinkar.entity.SemanticRecord.entityToStringExtras(SemanticRecord.java:58)
 * at org.hl7.tinkar.entity@1.0-SNAPSHOT/org.hl7.tinkar.entity.Entity.entityToString(Entity.java:104)
 * at org.hl7.tinkar.entity@1.0-SNAPSHOT/org.hl7.tinkar.entity.SemanticRecord.toString(SemanticRecord.java:93)
 * at org.hl7.tinkar.integration@1.0-SNAPSHOT/org.hl7.tinkar.integration.coordinate.TestCoordinates.lambda$computeLatest$0(TestCoordinates.java:69)
 * at org.hl7.tinkar.provider.entity@1.0-SNAPSHOT/org.hl7.tinkar.provider.entity.EntityProvider.lambda$forEachSemanticForComponent$11b2ea8e$1(EntityProvider.java:221)
 * at org.hl7.tinkar.eclipse.collections@11.0.0.M1/org.eclipse.collections.api.block.procedure.primitive.IntProcedure.accept(IntProcedure.java:30)
 * at org.hl7.tinkar.provider.mvstore.MVStoreProvider.forEachSemanticNidForComponent(MVStoreProvider.java:250)
 * at org.hl7.tinkar.provider.entity@1.0-SNAPSHOT/org.hl7.tinkar.provider.entity.EntityProvider.forEachSemanticForComponent(EntityProvider.java:221)
 * at org.hl7.tinkar.integration@1.0-SNAPSHOT/org.hl7.tinkar.integration.coordinate.TestCoordinates.computeLatest(TestCoordinates.java:68)
 * at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
 * at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
 * at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
 * at java.base/java.lang.reflect.Method.invoke(Method.java:568)
 * at org.junit.platform.commons.util.ReflectionUtils.invokeMethod(ReflectionUtils.java:686)
 * at org.junit.jupiter.engine.execution.MethodInvocation.proceed(MethodInvocation.java:60)
 * at org.junit.jupiter.engine.execution.InvocationInterceptorChain$ValidatingInvocation.proceed(InvocationInterceptorChain.java:131)
 * at org.junit.jupiter.engine.extension.TimeoutExtension.intercept(TimeoutExtension.java:149)
 * at org.junit.jupiter.engine.extension.TimeoutExtension.interceptTestableMethod(TimeoutExtension.java:140)
 * at org.junit.jupiter.engine.extension.TimeoutExtension.interceptTestMethod(TimeoutExtension.java:84)
 * at org.junit.jupiter.engine.execution.ExecutableInvoker$ReflectiveInterceptorCall.lambda$ofVoidMethod$0(ExecutableInvoker.java:115)
 * at org.junit.jupiter.engine.execution.ExecutableInvoker.lambda$invoke$0(ExecutableInvoker.java:105)
 * at org.junit.jupiter.engine.execution.InvocationInterceptorChain$InterceptedInvocation.proceed(InvocationInterceptorChain.java:106)
 * at org.junit.jupiter.engine.execution.InvocationInterceptorChain.proceed(InvocationInterceptorChain.java:64)
 * at org.junit.jupiter.engine.execution.InvocationInterceptorChain.chainAndInvoke(InvocationInterceptorChain.java:45)
 * at org.junit.jupiter.engine.execution.InvocationInterceptorChain.invoke(InvocationInterceptorChain.java:37)
 * at org.junit.jupiter.engine.execution.ExecutableInvoker.invoke(ExecutableInvoker.java:104)
 * at org.junit.jupiter.engine.execution.ExecutableInvoker.invoke(ExecutableInvoker.java:98)
 * at org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.lambda$invokeTestMethod$6(TestMethodTestDescriptor.java:212)
 * at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:73)
 * at org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.invokeTestMethod(TestMethodTestDescriptor.java:208)
 * at org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.execute(TestMethodTestDescriptor.java:137)
 * at org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.execute(TestMethodTestDescriptor.java:71)
 * at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$5(NodeTestTask.java:135)
 * at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:73)
 * at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$7(NodeTestTask.java:125)
 * at org.junit.platform.engine.support.hierarchical.Node.around(Node.java:135)
 * at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$8(NodeTestTask.java:123)
 * at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:73)
 * at org.junit.platform.engine.support.hierarchical.NodeTestTask.executeRecursively(NodeTestTask.java:122)
 * at org.junit.platform.engine.support.hierarchical.NodeTestTask.execute(NodeTestTask.java:80)
 * at java.base/java.util.ArrayList.forEach(ArrayList.java:1511)
 * at org.junit.platform.engine.support.hierarchical.SameThreadHierarchicalTestExecutorService.invokeAll(SameThreadHierarchicalTestExecutorService.java:38)
 * at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$5(NodeTestTask.java:139)
 * at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:73)
 * at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$7(NodeTestTask.java:125)
 * at org.junit.platform.engine.support.hierarchical.Node.around(Node.java:135)
 * at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$8(NodeTestTask.java:123)
 * at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:73)
 * at org.junit.platform.engine.support.hierarchical.NodeTestTask.executeRecursively(NodeTestTask.java:122)
 * at org.junit.platform.engine.support.hierarchical.NodeTestTask.execute(NodeTestTask.java:80)
 * at java.base/java.util.ArrayList.forEach(ArrayList.java:1511)
 * at org.junit.platform.engine.support.hierarchical.SameThreadHierarchicalTestExecutorService.invokeAll(SameThreadHierarchicalTestExecutorService.java:38)
 * at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$5(NodeTestTask.java:139)
 * at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:73)
 * at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$7(NodeTestTask.java:125)
 * at org.junit.platform.engine.support.hierarchical.Node.around(Node.java:135)
 * at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$8(NodeTestTask.java:123)
 * at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:73)
 * at org.junit.platform.engine.support.hierarchical.NodeTestTask.executeRecursively(NodeTestTask.java:122)
 * at org.junit.platform.engine.support.hierarchical.NodeTestTask.execute(NodeTestTask.java:80)
 * at org.junit.platform.engine.support.hierarchical.SameThreadHierarchicalTestExecutorService.submit(SameThreadHierarchicalTestExecutorService.java:32)
 * at org.junit.platform.engine.support.hierarchical.HierarchicalTestExecutor.execute(HierarchicalTestExecutor.java:57)
 * at org.junit.platform.engine.support.hierarchical.HierarchicalTestEngine.execute(HierarchicalTestEngine.java:51)
 * at org.junit.platform.launcher.core.DefaultLauncher.execute(DefaultLauncher.java:248)
 * at org.junit.platform.launcher.core.DefaultLauncher.lambda$execute$5(DefaultLauncher.java:211)
 * at org.junit.platform.launcher.core.DefaultLauncher.withInterceptedStreams(DefaultLauncher.java:226)
 * at org.junit.platform.launcher.core.DefaultLauncher.execute(DefaultLauncher.java:199)
 * at org.junit.platform.launcher.core.DefaultLauncher.execute(DefaultLauncher.java:132)
 * at org.apache.maven.surefire.junitplatform.JUnitPlatformProvider.execute(JUnitPlatformProvider.java:188)
 * at org.apache.maven.surefire.junitplatform.JUnitPlatformProvider.invokeAllTests(JUnitPlatformProvider.java:154)
 * at org.apache.maven.surefire.junitplatform.JUnitPlatformProvider.invoke(JUnitPlatformProvider.java:128)
 * at org.apache.maven.surefire.booter.ForkedBooter.runSuitesInProcess(ForkedBooter.java:428)
 * at org.apache.maven.surefire.booter.ForkedBooter.execute(ForkedBooter.java:162)
 * at org.apache.maven.surefire.booter.ForkedBooter.run(ForkedBooter.java:562)
 * at org.apache.maven.surefire.booter.ForkedBooter.main(ForkedBooter.java:548)
 * [INFO]
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestMVStoreProvider {

    private static final Logger LOG = LoggerFactory.getLogger(TestMVStoreProvider.class);

    @BeforeAll
    static void setupSuite() {
        LOG.info("Setup suite: " + LOG.getName());
        LOG.info(ServiceProperties.jvmUuid());
        ServiceProperties.set(ServiceKeys.DATA_STORE_ROOT, TestConstants.MVSTORE_ROOT);
        PrimitiveData.selectControllerByName(TestConstants.MV_STORE_OPEN_NAME);
        PrimitiveData.start();
    }


    @AfterAll
    static void teardownSuite() {
        LOG.info("Teardown suite: " + LOG.getName());
        PrimitiveData.stop();
    }

    @Test
    @Order(1)
    public void loadChronologies() throws IOException {
        LoadEntitiesFromDtoFile loadTink = new LoadEntitiesFromDtoFile(TestConstants.TINK_TEST_FILE);
        int count = loadTink.compute();
        LOG.info("Loaded. " + loadTink.report() + "\n\n");
    }

    @Test
    public void count() {
        if (!PrimitiveData.running()) {
            LOG.info("Reloading MVStoreProvider");
            Stopwatch reloadStopwatch = new Stopwatch();
            PrimitiveData.start();
            LOG.info("Reloading in: " + reloadStopwatch.durationString() + "\n\n");
        }
        EntityProcessor processor = new EntityCounter();
        PrimitiveData.get().forEach(processor);
        LOG.info("MVS Sequential count: \n" + processor.report() + "\n\n");
        processor = new EntityCounter();
        PrimitiveData.get().forEachParallel(processor);
        LOG.info("MVS Parallel count: \n" + processor.report() + "\n\n");
        processor = new EntityRealizer();
        PrimitiveData.get().forEach(processor);
        LOG.info("MVS Sequential realization: \n" + processor.report() + "\n\n");
        processor = new EntityRealizer();
        PrimitiveData.get().forEachParallel(processor);
        LOG.info("MVS Parallel realization: \n" + processor.report() + "\n\n");
        processor = new EntityRealizer();
        PrimitiveData.get().forEach(processor);
        LOG.info("MVS Sequential realization: \n" + processor.report() + "\n\n");
        processor = new EntityRealizer();
        PrimitiveData.get().forEachParallel(processor);
        LOG.info("MVS Parallel realization: \n" + processor.report() + "\n\n");
    }

    @Test
    public void openAndClose() {
        if (PrimitiveData.running()) {
            Stopwatch closingStopwatch = new Stopwatch();
            PrimitiveData.stop();
            closingStopwatch.end();
            LOG.info("MVS Closed in: " + closingStopwatch.durationString() + "\n\n");
        }
        LOG.info("Reloading MVStoreProvider");
        Stopwatch reloadStopwatch = new Stopwatch();
        PrimitiveData.start();
        LOG.info("MVS Reloading in: " + reloadStopwatch.durationString() + "\n\n");
        EntityProcessor processor = new EntityCounter();
        PrimitiveData.get().forEach(processor);
        LOG.info("MVS Sequential count: \n" + processor.report() + "\n\n");
        processor = new EntityCounter();
        PrimitiveData.get().forEachParallel(processor);
        LOG.info("MVS Parallel count: \n" + processor.report() + "\n\n");
        processor = new EntityRealizer();
        PrimitiveData.get().forEach(processor);
        LOG.info("MVS Sequential realization: \n" + processor.report() + "\n\n");
        processor = new EntityRealizer();
        PrimitiveData.get().forEachParallel(processor);
        LOG.info("MVS Parallel realization: \n" + processor.report() + "\n\n");
        processor = new EntityRealizer();
        PrimitiveData.get().forEach(processor);
        LOG.info("MVS Sequential realization: \n" + processor.report() + "\n\n");
        processor = new EntityRealizer();
        PrimitiveData.get().forEachParallel(processor);
        LOG.info("MVS Parallel realization: \n" + processor.report() + "\n\n");
    }
}
