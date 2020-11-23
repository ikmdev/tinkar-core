package org.hl7.tinkar.dto;

import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.component.DefinitionForSemantic;

import java.util.UUID;

public record DefinitionForSemanticDTO(ImmutableList<UUID> componentUuids) implements DefinitionForSemantic {
    @Override
    public ImmutableList<UUID> getComponentUuids() {
        return componentUuids;
    }
}
