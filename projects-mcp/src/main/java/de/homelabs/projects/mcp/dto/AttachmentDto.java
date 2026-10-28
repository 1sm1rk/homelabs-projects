package de.homelabs.projects.mcp.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Wire shape of attachment METADATA: file name, type, size, checksum and uploader. The
 * bytes live on the API's filesystem volume and are never part of this contract.
 */
public record AttachmentDto(
        UUID id,
        @JsonProperty("entity_type") EntityType entityType,
        @JsonProperty("entity_id") UUID entityId,
        String filename,
        @JsonProperty("mime_type") String mimeType,
        @JsonProperty("byte_size") long byteSize,
        String sha256,
        @JsonProperty("uploaded_by_sub") String uploadedBySub,
        @JsonProperty("uploaded_by_name") String uploadedByName,
        @JsonProperty("created_at") Instant createdAt) {
}
