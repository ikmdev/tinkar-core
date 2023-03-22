package org.hl7.tinkar.provider.spinedarray;


import org.hl7.tinkar.collection.SpineFileUtil;
import org.hl7.tinkar.collection.store.IntLongArrayStore;
import org.hl7.tinkar.collection.store.IntLongArrayStoreProvider;
import org.hl7.tinkar.common.service.ServiceKeys;
import org.hl7.tinkar.common.service.ServiceProperties;

import java.io.File;

import static org.hl7.tinkar.provider.spinedarray.SpinedArrayProvider.defaultDataDirectory;

public class IntLongArrayFileStoreProvider implements IntLongArrayStoreProvider {
    public IntLongArrayStore get(String storeName) {
        File folderPath = ServiceProperties.get(ServiceKeys.DATA_STORE_ROOT, defaultDataDirectory);
        IntLongArrayStore intLongArrayFileStore = new IntLongArrayFileStore(
                new File(new File(folderPath, "namedStores"), storeName));
        return intLongArrayFileStore;
    }
    public IntLongArrayStore get(int patternNid) {
        File folderPath = ServiceProperties.get(ServiceKeys.DATA_STORE_ROOT, defaultDataDirectory);
        IntLongArrayStore intLongArrayFileStore = new IntLongArrayFileStore(
                SpineFileUtil.getSpineDirectory(new File(folderPath, "longArrayPatternStores"), patternNid));
        return intLongArrayFileStore;
    }
}
