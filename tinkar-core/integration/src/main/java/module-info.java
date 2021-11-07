import org.hl7.tinkar.common.service.DataServiceController;
import org.hl7.tinkar.common.service.DefaultDescriptionForNidService;
import org.hl7.tinkar.common.service.PublicIdService;
import org.hl7.tinkar.entity.EntityService;
import org.hl7.tinkar.entity.StampService;

open module org.hl7.tinkar.integration {
    requires org.hl7.tinkar.common;
    requires org.hl7.tinkar.entity;
    requires org.hl7.tinkar.provider.entity;
    requires org.hl7.tinkar.terms;
    requires org.hl7.tinkar.coordinate;
    requires org.hl7.tinkar.protobuf;

    uses DataServiceController;
    uses DefaultDescriptionForNidService;
    uses EntityService;
    uses PublicIdService;
    uses StampService;
}