package com.sgs.capability.service;

import com.sgs.capability.dto.FileDto;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Local replacement for ABP temp file cache. */
@Service
public class TempFileService {
    private final Map<String, StoredFile> files = new ConcurrentHashMap<>();

    public FileDto put(String fileName, String fileType, byte[] content) {
        FileDto file = new FileDto(fileName, normalize(fileType));
        put(file, content);
        return file;
    }

    public void put(FileDto file, byte[] content) {
        files.put(file.fileToken, new StoredFile(file.fileName, file.fileType, content));
    }

    public Optional<StoredFile> get(String fileToken) {
        return Optional.ofNullable(files.get(fileToken));
    }

    public byte[] requireContent(FileDto file) {
        return get(file.fileToken)
                .map(StoredFile::content)
                .orElseThrow(() -> new IllegalArgumentException("File token not found: " + file.fileToken));
    }

    private String normalize(String fileType) {
        return fileType == null || fileType.isBlank()
                ? "application/octet-stream"
                : fileType;
    }

    public record StoredFile(String fileName, String fileType, byte[] content) {
    }
}
