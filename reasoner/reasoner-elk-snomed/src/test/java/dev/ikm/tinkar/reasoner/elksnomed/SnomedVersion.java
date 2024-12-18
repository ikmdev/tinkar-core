package dev.ikm.tinkar.reasoner.elksnomed;

public interface SnomedVersion {

	default String getDir() {
		return "target/data/snomed-test-data-" + getEditionDir() + "-" + getVersion();
	}

	String getEdition();

	String getEditionDir();

	String getVersion();

	String getInternationalVersion();

}
