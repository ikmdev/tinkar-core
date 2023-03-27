package dev.ikm.tinkar.common.service;

import java.io.File;
import java.util.concurrent.Future;

public interface LoadDataFromFileController {
    Future<?> load(File file);
}
