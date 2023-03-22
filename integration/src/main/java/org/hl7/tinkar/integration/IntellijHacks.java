package org.hl7.tinkar.integration;

/**
 * Class to read stuff in the module-info.java file, that the tests will use, since
 * IntelliJ colors them read and says things like:
 * <p>
 * "Package 'org.hl7.tinkar.common.service' is declared in module 'org.hl7.tinkar.common', but module 'org.hl7.tinkar.integration' does not read it"
 * <p>
 *
 * @TODO Invalidating the IntelliJ cache and restarting may have fixed need for this class, but leaving it in for now...
 */

import org.hl7.tinkar.common.service.DataServiceController;
import org.hl7.tinkar.common.service.DefaultDescriptionForNidService;
import org.hl7.tinkar.common.service.PublicIdService;
import org.hl7.tinkar.entity.EntityService;
import org.hl7.tinkar.entity.StampService;

public class IntellijHacks {
    DataServiceController dataServiceController = null;
    DefaultDescriptionForNidService defaultDescriptionForNidService = null;
    PublicIdService publicIdService = null;
    EntityService entityService = null;
    StampService stampService = null;

}
