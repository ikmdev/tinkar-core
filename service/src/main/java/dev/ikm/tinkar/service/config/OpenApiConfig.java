package dev.ikm.tinkar.service.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	@Bean
	public OpenAPI customOpenAPI() {
		return new OpenAPI()
				.info(new Info()
						.title("Tinkar Service API")
						.version("0.0.1-SNAPSHOT")
						.description("API documentation for Tinkar Service"))
				.addTagsItem(new Tag()
						.name("IKE Graph RAG (Tier 1)")
						.description("Simple, opinionated API for ML/RAG engineers. Pre-resolved human-readable responses."))
				.addTagsItem(new Tag()
						.name("IKE Knowledge Graph (Tier 2)")
						.description("Concept-aware API exposing STAMP coordinates, semantic patterns, and version history."))
				.addTagsItem(new Tag()
						.name("IKE Admin (Tier 3)")
						.description("Data management operations: import changesets, export entities, and reasoner classification."))
				.addTagsItem(new Tag()
						.name("Tinkar Search (Deprecated)")
						.description("DEPRECATED — Use Tier 1 or Tier 2 endpoints instead."));
	}
}

