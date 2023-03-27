package dev.ikm.tinkar.integration;

/**
 * Class to read stuff in the module-info.java file, that the tests will use, since
 * IntelliJ colors them read and says things like:
 * <p>
 * "Package 'dev.ikm.tinkar.common.service' is declared in module 'dev.ikm.tinkar.common', but module 'dev.ikm.tinkar.integration' does not read it"
 * <p>
 *
 * @TODO Invalidating the IntelliJ cache and restarting may have fixed need for this class, but leaving it in for now...
 */

import dev.ikm.tinkar.common.service.DataServiceController;
import dev.ikm.tinkar.common.service.DefaultDescriptionForNidService;
import dev.ikm.tinkar.common.service.PublicIdService;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.StampService;

public class IntellijHacks {
    DataServiceController dataServiceController = null;
    DefaultDescriptionForNidService defaultDescriptionForNidService = null;
    PublicIdService publicIdService = null;
    EntityService entityService = null;
    StampService stampService = null;

}
