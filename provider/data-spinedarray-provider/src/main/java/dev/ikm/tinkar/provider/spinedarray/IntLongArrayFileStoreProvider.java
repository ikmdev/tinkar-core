package dev.ikm.tinkar.provider.spinedarray;


import dev.ikm.tinkar.collection.SpineFileUtil;
import dev.ikm.tinkar.collection.store.IntLongArrayStore;
import dev.ikm.tinkar.collection.store.IntLongArrayStoreProvider;
import dev.ikm.tinkar.common.service.ServiceKeys;
import dev.ikm.tinkar.common.service.ServiceProperties;

import java.io.File;

public class IntLongArrayFileStoreProvider implements IntLongArrayStoreProvider {
    public IntLongArrayStore get(String storeName) {
        File folderPath = ServiceProperties.get(ServiceKeys.DATA_STORE_ROOT, SpinedArrayProvider.defaultDataDirectory);
        IntLongArrayStore intLongArrayFileStore = new IntLongArrayFileStore(
                new File(new File(folderPath, "namedStores"), storeName));
        return intLongArrayFileStore;
    }
    public IntLongArrayStore get(int patternNid) {
        File folderPath = ServiceProperties.get(ServiceKeys.DATA_STORE_ROOT, SpinedArrayProvider.defaultDataDirectory);
        IntLongArrayStore intLongArrayFileStore = new IntLongArrayFileStore(
                SpineFileUtil.getSpineDirectory(new File(folderPath, "longArrayPatternStores"), patternNid));
        return intLongArrayFileStore;
    }
}
