package dev.ikm.tinkar.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Navigation premise type for hierarchy traversal.
 */
@Schema(description = "Navigation premise type controlling whether hierarchy uses stated or inferred relationships")
public enum PremiseType {
    @Schema(description = "Use inferred (classified) navigation relationships")
    INFERRED,

    @Schema(description = "Use stated (authored) navigation relationships")
    STATED
}
