package org.hl7.tinkar.common.validation;

public record ValidationRecord(ValidationSeverity severity, String message, Object target) {}
