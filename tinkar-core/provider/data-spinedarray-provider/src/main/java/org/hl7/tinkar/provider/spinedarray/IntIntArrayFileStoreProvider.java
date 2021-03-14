package org.hl7.tinkar.provider.spinedarray;

import org.hl7.tinkar.collection.SpineFileUtil;
import org.hl7.tinkar.collection.store.IntIntArrayStore;
import org.hl7.tinkar.collection.store.IntIntArrayStoreProvider;

import java.io.File;
import java.nio.file.Path;

public class IntIntArrayFileStoreProvider implements IntIntArrayStoreProvider {

    @Override
    public IntIntArrayStore get(int assemblageNid) {
        throw new UnsupportedOperationException();
//        Path folderPath = Get.configurationService().getDataStoreFolderPath();
//        IntIntArrayFileStore intIntArrayFileStore = new IntIntArrayFileStore(
//                SpineFileUtil.getSpineDirectory(new File(folderPath.toFile(), "taxonomyMap"), assemblageNid));
//        return intIntArrayFileStore;
    }
}
