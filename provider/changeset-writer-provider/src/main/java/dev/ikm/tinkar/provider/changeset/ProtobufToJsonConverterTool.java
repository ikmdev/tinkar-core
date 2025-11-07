package dev.ikm.tinkar.provider.changeset;

import dev.ikm.tinkar.schema.TinkarMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Path;
import java.util.concurrent.atomic.LongAdder;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Tool for converting Tinkar protobuf change set files to JSON format.
 * Reads protobuf messages from zip files and writes them directly to JSON files
 * without requiring any database or EntityService dependencies.
 */
public class ProtobufToJsonConverterTool {
    private static final Logger LOG = LoggerFactory.getLogger(ProtobufToJsonConverterTool.class);

    private final TinkarMsgToJsonTransformer jsonTransformer;

    public ProtobufToJsonConverterTool() {
        this.jsonTransformer = TinkarMsgToJsonTransformer.getInstance();
    }

    /**
     * Converts a protobuf changeset file to JSON format.
     *
     * @param protobufZipFile the input protobuf zip file
     * @param outputJsonFile the output JSON file
     * @return the number of entities converted
     * @throws IOException if an I/O error occurs
     */
    public long convert(File protobufZipFile, File outputJsonFile) throws IOException {
        LOG.info("Converting {} to {}", protobufZipFile.getAbsolutePath(), outputJsonFile.getAbsolutePath());

        LongAdder entityCount = new LongAdder();

        try (FileInputStream fis = new FileInputStream(protobufZipFile);
             BufferedInputStream bis = new BufferedInputStream(fis);
             ZipInputStream zis = new ZipInputStream(bis);
             FileWriter fw = new FileWriter(outputJsonFile);
             BufferedWriter jsonWriter = new BufferedWriter(fw)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals("Entities")) {
                    LOG.debug("Processing Entities entry from zip file");

                    while (zis.available() > 0) {
                        try {
                            TinkarMsg tinkarMsg = TinkarMsg.parseDelimitedFrom(zis);
                            if (tinkarMsg == null) {
                                break;
                            }

                            // Convert protobuf message directly to JSON
                            jsonTransformer.writeMessage(tinkarMsg, jsonWriter);
                            entityCount.increment();

                            if (entityCount.sum() % 1000 == 0) {
                                LOG.debug("Converted {} entities", entityCount.sum());
                            }
                        } catch (IOException e) {
                            LOG.warn("Error reading protobuf message, may have reached end of stream", e);
                            break;
                        }
                    }
                }
                zis.closeEntry();
            }

            jsonWriter.flush();
        }

        LOG.info("Conversion complete. Converted {} entities", entityCount.sum());
        return entityCount.sum();
    }

    /**
     * Converts a protobuf changeset file to JSON format using Path objects.
     *
     * @param protobufZipPath the input protobuf zip file path
     * @param outputJsonPath the output JSON file path
     * @return the number of entities converted
     * @throws IOException if an I/O error occurs
     */
    @SuppressWarnings("unused")
    public long convert(Path protobufZipPath, Path outputJsonPath) throws IOException {
        return convert(protobufZipPath.toFile(), outputJsonPath.toFile());
    }

    /**
     * Converts all protobuf changeset files in a directory to JSON format.
     * Processes files ending with "pb.zip" or "ike-cs.zip" and creates
     * corresponding JSON files with the same base name but ".json" extension.
     *
     * @param directory the directory containing protobuf files
     * @return the total number of entities converted across all files
     * @throws IOException if an I/O error occurs
     */
    public long convertDirectory(File directory) throws IOException {
        if (!directory.exists()) {
            throw new IOException("Directory does not exist: " + directory.getAbsolutePath());
        }
        if (!directory.isDirectory()) {
            throw new IOException("Path is not a directory: " + directory.getAbsolutePath());
        }

        LOG.info("Scanning directory for protobuf files: {}", directory.getAbsolutePath());

        File[] protobufFiles = directory.listFiles((dir, name) ->
            name.endsWith("pb.zip") || name.endsWith("ike-cs.zip")
        );

        if (protobufFiles == null || protobufFiles.length == 0) {
            LOG.warn("No protobuf files found in directory: {}", directory.getAbsolutePath());
            return 0;
        }

        LOG.info("Found {} protobuf file(s) to convert", protobufFiles.length);

        long totalEntities = 0;
        int successCount = 0;
        int failureCount = 0;

        for (File protobufFile : protobufFiles) {
            File jsonFile = getJsonOutputFile(protobufFile);
            try {
                LOG.info("Processing file {}/{}: {}",
                    successCount + failureCount + 1, protobufFiles.length, protobufFile.getName());
                long entities = convert(protobufFile, jsonFile);
                totalEntities += entities;
                successCount++;
                LOG.info("Successfully converted {} ({} entities)", protobufFile.getName(), entities);
            } catch (IOException e) {
                failureCount++;
                LOG.error("Failed to convert {}: {}", protobufFile.getName(), e.getMessage(), e);
            }
        }

        LOG.info("Directory conversion complete. Processed {} files ({} successful, {} failed). Total entities: {}",
            protobufFiles.length, successCount, failureCount, totalEntities);

        return totalEntities;
    }

    /**
     * Converts all protobuf changeset files in a directory to JSON format using Path.
     *
     * @param directoryPath the directory path containing protobuf files
     * @return the total number of entities converted across all files
     * @throws IOException if an I/O error occurs
     */
    @SuppressWarnings("unused")
    public long convertDirectory(Path directoryPath) throws IOException {
        return convertDirectory(directoryPath.toFile());
    }

    /**
     * Generates the JSON output filename for a given protobuf file.
     * Replaces "pb.zip" or "ike-cs.zip" suffix with ".json".
     *
     * @param protobufFile the protobuf input file
     * @return the corresponding JSON output file
     */
    private File getJsonOutputFile(File protobufFile) {
        String fileName = protobufFile.getName();
        String jsonFileName;

        if (fileName.endsWith("pb.zip")) {
            jsonFileName = fileName.substring(0, fileName.length() - 6) + ".json";
        } else if (fileName.endsWith("ike-cs.zip")) {
            jsonFileName = fileName.substring(0, fileName.length() - 10) + ".json";
        } else {
            // Fallback - should not happen given the file filter
            jsonFileName = fileName + ".json";
        }

        return new File(protobufFile.getParent(), jsonFileName);
    }

    /**
     * Main method for command-line usage.
     *
     * @param args command line arguments: [input-protobuf-zip-or-directory] [output-json-file (optional if directory)]
     */
    public static void main(String[] args) {
        if (args.length == 0 || args.length > 2) {
            System.err.println("Usage:");
            System.err.println("  Single file: ProtobufToJsonConverterTool <input-protobuf-zip> <output-json-file>");
            System.err.println("  Directory:   ProtobufToJsonConverterTool <directory-path>");
            System.exit(1);
        }

        File inputFile = new File(args[0]);

        if (!inputFile.exists()) {
            System.err.println("Input file/directory does not exist: " + inputFile.getAbsolutePath());
            System.exit(1);
        }

        ProtobufToJsonConverterTool converter = new ProtobufToJsonConverterTool();

        try {
            if (inputFile.isDirectory()) {
                // Directory mode
                if (args.length == 2) {
                    System.err.println("Warning: Output file parameter ignored in directory mode");
                }
                long totalCount = converter.convertDirectory(inputFile);
                System.out.println("Successfully converted " + totalCount + " total entities from directory");
            } else {
                // Single file mode
                if (args.length != 2) {
                    System.err.println("Error: Output file required for single file conversion");
                    System.err.println("Usage: ProtobufToJsonConverterTool <input-protobuf-zip> <output-json-file>");
                    System.exit(1);
                }
                File outputFile = new File(args[1]);
                long count = converter.convert(inputFile, outputFile);
                System.out.println("Successfully converted " + count + " entities");
            }
        } catch (IOException e) {
            System.err.println("Error during conversion: " + e.getMessage());
            LOG.error("Error during conversion", e);
            System.exit(1);
        }
    }
}

