package dev.ikm.tinkar.reasoner.hybrid;

public interface SnomedVersionInternational extends SnomedVersion {

	@Override
	default String getEdition() {
		return "INT";
	}

	@Override
	default String getEditionDir() {
		return "intl";
	}

	@Override
	default String getInternationalVersion() {
		return getVersion();
	}

}
