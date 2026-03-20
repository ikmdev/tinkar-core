package dev.ikm.tinkar.service.controller.admin;

import dev.ikm.tinkar.service.dto.EntityCountSummaryResponse;
import dev.ikm.tinkar.service.dto.ReasonerResultsResponse;
import dev.ikm.tinkar.service.service.TinkarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

/**
 * Tier 3: Admin / Data Management — REST controller.
 *
 * Operations for importing changesets, exporting entity data,
 * and running the reasoner classification pipeline.
 * Target audience: platform operators, DevOps, CI/CD pipelines.
 */
@RestController
@RequestMapping("/api/ike/admin")
@Tag(name = "IKE Admin (Tier 3)", description = "Data management operations: import changesets, export entities, and reasoner classification.")
public class AdminRestController {

    private final TinkarService tinkarService;

    public AdminRestController(TinkarService tinkarService) {
        this.tinkarService = tinkarService;
    }

    @Operation(summary = "Import a changeset",
            description = "Imports a Tinkar changeset from a protobuf ZIP file. " +
                    "Uses multi-pass import by default to handle forward references.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Import completed",
                    content = @Content(schema = @Schema(implementation = EntityCountSummaryResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid file or parameters")
    })
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EntityCountSummaryResponse> importChangeset(
            @Parameter(description = "Protobuf ZIP file to import", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Use multi-pass import to resolve forward references (default: true)")
            @RequestParam(required = false, defaultValue = "true") boolean useMultiPass) {

        File tempFile = null;
        try {
            tempFile = Files.createTempFile("tinkar-import-", ".zip").toFile();
            file.transferTo(tempFile);

            EntityCountSummaryResponse result = tinkarService.importChangeset(tempFile, useMultiPass);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.ok(EntityCountSummaryResponse.error(e.getMessage()));
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    @Operation(summary = "Run the reasoner",
            description = "Runs the full reasoner classification pipeline: " +
                    "init -> extractData -> loadData -> computeInferences -> writeInferredResults. " +
                    "This may take several minutes for large datasets.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Reasoner completed",
                    content = @Content(schema = @Schema(implementation = ReasonerResultsResponse.class))),
            @ApiResponse(responseCode = "500", description = "Reasoner failed")
    })
    @PostMapping("/reasoner")
    public ResponseEntity<ReasonerResultsResponse> runReasoner() {
        return ResponseEntity.ok(tinkarService.runReasoner());
    }
}
