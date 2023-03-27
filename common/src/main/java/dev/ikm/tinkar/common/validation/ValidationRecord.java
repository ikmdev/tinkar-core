package dev.ikm.tinkar.common.validation;

public record ValidationRecord(ValidationSeverity severity, String message, Object target) {}
