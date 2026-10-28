package de.homelabs.projects.api;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import de.homelabs.projects.domain.Attachment;
import de.homelabs.projects.domain.AttachmentService;
import de.homelabs.projects.domain.StorageKeys;

/**
 * Attachments: metadata in the database, bytes on the filesystem volume. The upload is
 * one multipart call carrying the parent reference; {@code DELETE} is the single hard
 * delete of the whole domain, and the {@code detached} activity entry stays as the
 * trace.
 *
 * <p>Content downloads carry the stored content type and an {@code ETag} derived from
 * the SHA-256 of the bytes. Browser {@code <img>} inclusion cannot send a Bearer header
 * — that concern belongs to the later frontend project.
 */
@RestController
@RequestMapping("/api/attachments")
public class AttachmentController {

    private final AttachmentService attachments;

    public AttachmentController(AttachmentService attachments) {
        this.attachments = attachments;
    }

    /** One call: parent reference plus the file. Uploads above the cap answer 413. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Attachment upload(@RequestParam String parent,
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = ApiSupport.SOURCE_HEADER, required = false) String source,
            @AuthenticationPrincipal Jwt jwt) throws IOException {
        return attachments.store(ApiSupport.caller(jwt, source), parent, file.getOriginalFilename(),
                file.getContentType(), file.getBytes());
    }

    /** Metadata only, newest first. */
    @GetMapping
    public List<Attachment> list(@RequestParam String parent, @RequestParam(required = false) Integer limit) {
        return attachments.list(parent, ApiSupport.clamp(limit, ApiSupport.DEFAULT_ACTIVITY_LIMIT,
                ApiSupport.MAX_ACTIVITY_LIMIT));
    }

    @GetMapping("/{id}")
    public Attachment get(@PathVariable UUID id) {
        return attachments.get(id);
    }

    /** The bytes with their stored content type and a SHA-256 based {@code ETag}. */
    @GetMapping("/{id}/content")
    public ResponseEntity<Resource> content(@PathVariable UUID id) {
        AttachmentService.StoredContent stored = attachments.content(id);
        MediaType contentType;
        try {
            contentType = MediaType.parseMediaType(stored.metadata().mimeType());
        } catch (InvalidMediaTypeException unusable) {
            contentType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(contentType)
                .contentLength(stored.metadata().byteSize())
                .eTag(StorageKeys.etag(stored.metadata().sha256()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + stored.metadata().filename() + "\"")
                .body(new FileSystemResource(stored.path()));
    }

    /** The one hard delete: metadata and bytes go, the activity row stays. */
    @DeleteMapping("/{id}")
    public DeletedAttachment delete(@PathVariable UUID id,
            @RequestHeader(value = ApiSupport.SOURCE_HEADER, required = false) String source,
            @AuthenticationPrincipal Jwt jwt) {
        Attachment deleted = attachments.delete(ApiSupport.caller(jwt, source), id);
        return new DeletedAttachment("deleted: attachment " + deleted.id() + " " + deleted.filename());
    }

    /** Plain-string confirmation of the hard delete. */
    public record DeletedAttachment(String message) {
    }
}
