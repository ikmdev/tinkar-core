package org.hl7.tinkar.common.service;

import java.util.List;
import java.util.Optional;

public interface DefaultDescriptionService {
    Optional<String> getTextOptional(int nid);
    String getTextFast(int nid);
    List<Optional<String>> getList(int... nids);
}
