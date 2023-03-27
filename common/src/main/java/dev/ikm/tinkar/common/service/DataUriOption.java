package dev.ikm.tinkar.common.service;


import java.io.File;
import java.net.URI;
import java.nio.file.Paths;

public record DataUriOption(String name, URI uri) {

    @Override
    public String toString() {
        return name;
    }

    public File toFile() {
        return Paths.get(uri).toFile();
    }
}
