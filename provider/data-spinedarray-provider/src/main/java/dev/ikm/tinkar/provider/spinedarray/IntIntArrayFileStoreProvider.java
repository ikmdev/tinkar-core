package dev.ikm.tinkar.provider.spinedarray;

import dev.ikm.tinkar.collection.SpineFileUtil;
import dev.ikm.tinkar.collection.store.IntIntArrayStore;
import dev.ikm.tinkar.collection.store.IntIntArrayStoreProvider;
import dev.ikm.tinkar.common.service.ServiceKeys;
import dev.ikm.tinkar.common.service.ServiceProperties;

import java.io.File;

public class IntIntArrayFileStoreProvider implements IntIntArrayStoreProvider {
    @Override
    public IntIntArrayStore get(String storeName) {
        File folderPath = ServiceProperties.get(ServiceKeys.DATA_STORE_ROOT, SpinedArrayProvider.defaultDataDirectory);
        IntIntArrayFileStore intIntArrayFileStore = new IntIntArrayFileStore(
                new File(new File(folderPath, "namedStores"), storeName));
        return intIntArrayFileStore;
    }

    @Override
    public IntIntArrayStore get(int patternNid) {
        File folderPath = ServiceProperties.get(ServiceKeys.DATA_STORE_ROOT, SpinedArrayProvider.defaultDataDirectory);
        IntIntArrayFileStore intIntArrayFileStore = new IntIntArrayFileStore(
                SpineFileUtil.getSpineDirectory(new File(folderPath, "intArrayPatternStores"), patternNid));
        return intIntArrayFileStore;
    }
}
