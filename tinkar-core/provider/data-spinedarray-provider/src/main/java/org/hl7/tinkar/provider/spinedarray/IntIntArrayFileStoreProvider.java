package org.hl7.tinkar.provider.spinedarray;

import org.hl7.tinkar.collection.SpineFileUtil;
import org.hl7.tinkar.collection.store.IntIntArrayStore;
import org.hl7.tinkar.collection.store.IntIntArrayStoreProvider;
import org.hl7.tinkar.common.service.ServiceKeys;
import org.hl7.tinkar.common.service.ServiceProperties;

import java.io.File;

import static org.hl7.tinkar.provider.spinedarray.SpinedArrayProvider.defaultDataDirectory;

public class IntIntArrayFileStoreProvider implements IntIntArrayStoreProvider {
    @Override
    public IntIntArrayStore get(String storeName) {
        File folderPath = ServiceProperties.get(ServiceKeys.DATA_STORE_ROOT, defaultDataDirectory);
        IntIntArrayFileStore intIntArrayFileStore = new IntIntArrayFileStore(
                new File(new File(folderPath, "namedStores"), storeName));
        return intIntArrayFileStore;
    }

    @Override
    public IntIntArrayStore get(int patternNid) {
        File folderPath = ServiceProperties.get(ServiceKeys.DATA_STORE_ROOT, defaultDataDirectory);
        IntIntArrayFileStore intIntArrayFileStore = new IntIntArrayFileStore(
                SpineFileUtil.getSpineDirectory(new File(folderPath, "intArrayPatternStores"), patternNid));
        return intIntArrayFileStore;
    }
}
