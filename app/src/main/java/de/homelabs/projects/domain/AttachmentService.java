package de.homelabs.projects.domain;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.homelabs.projects.persistence.AttachmentRepository;

/**
 * Attachments: metadata in the database, bytes on the filesystem volume.
 *
 * <p>The database stores a RELATIVE storage key only, so the volume can be relocated
 * without touching rows; the filename is sanitized before it becomes part of the path.
 * Uploads above {@code projects.attachments.max-size-mb} are rejected with 413, and
 * {@code DELETE} is the one hard delete of the whole domain — the {@code detached}
 * activity row stays as the trace.
 */
@Service
public class AttachmentService {

    private final AttachmentRepository attachments;
    private final AttachmentProperties properties;
    private final EntityLookup lookup;
    private final ProjectService projects;
    private final ActivityService activities;

    public AttachmentService(AttachmentRepository attachments, AttachmentProperties properties,
            EntityLookup lookup, ProjectService projects, ActivityService activities) {
        this.attachments = attachments;
        this.properties = properties;
        this.lookup = lookup;
        this.projects = projects;
        this.activities = activities;
    }

    /** Metadata plus the resolved file of a content download. */
    public record StoredContent(Attachment metadata, Path path) {
    }

    @Transactional
    public Attachment store(Caller caller, String parent, String filename, String contentType, byte[] bytes) {
        EntityLookup.ResolvedParent resolved = lookup.requireParent(parent);
        projects.requireWritable(resolved.projectId());
        if (bytes.length > properties.maxSizeBytes()) {
            throw new PayloadTooLargeException("attachment is " + bytes.length + " bytes; the limit is "
                    + properties.maxSizeMb() + " MB (projects.attachments.max-size-mb)");
        }

        String safeName = StorageKeys.sanitizeFilename(filename);
        UUID id = UUID.randomUUID();
        String storageKey = StorageKeys.key(id, safeName, Instant.now());
        Path target = properties.rootPath().resolve(storageKey);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException failure) {
            throw new UncheckedIOException("could not store attachment under " + properties.root(), failure);
        }

        attachments.insert(id, resolved.type(), resolved.id(), safeName, StorageKeys.contentType(contentType),
                bytes.length, StorageKeys.sha256Hex(bytes), storageKey, caller.sub(), caller.username());
        activities.record(caller, resolved.type(), resolved.id(), ActivityService.ATTACHED, safeName);
        return attachments.findById(id).orElseThrow();
    }

    public Attachment get(UUID id) {
        return attachments.findById(id).orElseThrow(() -> new NotFoundException("no attachment " + id));
    }

    /** Metadata only, newest first. */
    public List<Attachment> list(String parent, int limit) {
        EntityLookup.ResolvedParent resolved = lookup.requireParent(parent);
        return attachments.findForEntity(resolved.type(), resolved.id(), limit);
    }

    /** Bytes for the content endpoint; a row without a readable file is a 404, not a 500. */
    public StoredContent content(UUID id) {
        Attachment metadata = get(id);
        String storageKey = attachments.findStorageKey(id)
                .orElseThrow(() -> new NotFoundException("no attachment " + id));
        Path path = properties.rootPath().resolve(storageKey);
        if (!Files.isReadable(path)) {
            throw new NotFoundException("attachment " + id + " has no content on disk");
        }
        return new StoredContent(metadata, path);
    }

    /** The one hard delete: row and bytes go, the activity trace stays. */
    @Transactional
    public Attachment delete(Caller caller, UUID id) {
        Attachment metadata = get(id);
        String storageKey = attachments.findStorageKey(id)
                .orElseThrow(() -> new NotFoundException("no attachment " + id));
        EntityLookup.ResolvedParent resolved = lookup.requireParent(
                metadata.entityType().value() + ":" + metadata.entityId());
        projects.requireWritable(resolved.projectId());

        attachments.delete(id);
        try {
            Files.deleteIfExists(properties.rootPath().resolve(storageKey));
        } catch (IOException ignored) {
            // The metadata is gone either way; a leftover file is an ops cleanup, not a request failure.
        }
        activities.record(caller, resolved.type(), resolved.id(), ActivityService.DETACHED, metadata.filename());
        return metadata;
    }
}
