package org.hl7.tinkar.collection;

import java.io.File;
import java.io.IOException;

/**
 *
 * @author kec
 */
public class SpineFileUtil {
    public static final String SPINE_COUNT_PREFIX = "spineCount-";
    public static final String SPINE_PREFIX = "spine-";

    public static int readSpineCount(File directory) {
        if (directory.exists()) {
            File[] spineCountFiles = directory.listFiles((pathname) -> {
                return pathname.getName().startsWith(SPINE_COUNT_PREFIX);
            });

            switch (spineCountFiles.length) {
                case 0:
                    //throw new IllegalStateException("spineCount == 0 for: " + directory.getName());
                    return 0;
                case 1:
                    String countStr = spineCountFiles[0].getName().substring(SPINE_COUNT_PREFIX.length());
                    return Integer.parseInt(countStr);
                default:
                    throw new IllegalStateException("spineCount- > 1 for: " + directory.getName());
            }
        }
        return 0;
    }
    public static void writeSpineCount(File directory, int count) throws IOException {
        File[] spineCountFiles = directory.listFiles((pathname) -> {
            return pathname.getName().startsWith(SPINE_COUNT_PREFIX);
        });
        for (File countFile: spineCountFiles) {
            countFile.delete();
        }
        String newCountFileName = SPINE_COUNT_PREFIX + count;
        File newCountFile = new File(directory, newCountFileName);
        newCountFile.createNewFile();
    }


    public static File getSpineDirectory(File parentDirectory, int assemblageNid) {
        File spinedMapDirectory = new File(parentDirectory, Integer.toUnsignedString(assemblageNid));

        spinedMapDirectory.mkdirs();
        return spinedMapDirectory;
    }
}
