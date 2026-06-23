package com.sgs.capability.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Local BinaryObjectManager copy used by Chat and File MVC downloads. */
@Service
public class BinaryFileService {
    private final Map<UUID, StoredBinaryFile> files = new ConcurrentHashMap<>();

    public StoredBinaryFile put(String fileName, String contentType, byte[] content) {
        StoredBinaryFile file = new StoredBinaryFile(UUID.randomUUID(), fileName, normalize(contentType), content);
        files.put(file.id(), file);
        return file;
    }

    public Optional<StoredBinaryFile> get(UUID id) {
        return Optional.ofNullable(files.get(id));
    }

    private String normalize(String contentType) {
        return contentType == null || contentType.isBlank()
                ? "application/octet-stream"
                : contentType;
    }

    public record StoredBinaryFile(UUID id, String fileName, String contentType, byte[] content) {
    }
}
