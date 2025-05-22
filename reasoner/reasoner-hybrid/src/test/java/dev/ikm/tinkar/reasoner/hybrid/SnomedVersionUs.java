package dev.ikm.tinkar.reasoner.hybrid;

public interface SnomedVersionUs extends SnomedVersion {

	@Override
	default String getEdition() {
		return "US1000124";
	}

	@Override
	default String getEditionDir() {
		return "us";
	}

}
