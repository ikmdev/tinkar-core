import dev.ikm.tinkar.reasoner.service.ReasonerService;

open module dev.ikm.tinkar.reasoner.elksnomed.test {
	requires dev.ikm.jpms.eclipse.collections;
	requires dev.ikm.jpms.eclipse.collections.api;
	requires transitive org.junit.jupiter.api;
	requires org.slf4j;

	requires dev.ikm.tinkar.collection;
	requires dev.ikm.tinkar.coordinate;
	requires dev.ikm.tinkar.entity;
	requires dev.ikm.tinkar.ext.lang.owl;
	requires dev.ikm.tinkar.reasoner.service;

	requires dev.ikm.elk.snomed;
	requires dev.ikm.elk.snomed.owlel;
	requires dev.ikm.elk.snomed.test;
	requires dev.ikm.tinkar.reasoner.elksnomed;

	exports dev.ikm.tinkar.reasoner.elksnomed.test;

	// TODO needed for unit test
	uses ReasonerService;

}
