package com.sgs.capability.service;

import com.sgs.capability.dto.FileDto;
import com.sgs.capability.model.ChatMessageItem;
import com.sgs.capability.model.UserItem;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Builds the local copy of the original GDPR collected-data zip. */
@Service
public class GdprCollectedDataService {
    private final CapabilityStore store;
    private final ExcelTransferService excel;
    private final TempFileService tempFiles;
    private final BinaryFileService binaryFiles;

    public GdprCollectedDataService(CapabilityStore store, ExcelTransferService excel,
                                    TempFileService tempFiles, BinaryFileService binaryFiles) {
        this.store = store;
        this.excel = excel;
        this.tempFiles = tempFiles;
        this.binaryFiles = binaryFiles;
    }

    public BinaryFileService.StoredBinaryFile prepare(Long userId) {
        List<FileDto> files = new ArrayList<>();
        profileInfo(userId).ifPresent(files::add);
        profilePicture(userId).ifPresent(files::add);
        files.addAll(chatFiles(userId));
        BinaryFileService.StoredBinaryFile zip = binaryFiles.put("CollectedData.zip", "application/zip", zip(files));
        store.prepareCollectedData(userId, zip.id());
        return zip;
    }

    private Optional<FileDto> profileInfo(Long userId) {
        return store.user(userId).map(user -> {
            String content = String.join("\n\r", List.of(
                    "Tenancy name: .",
                    "User name: " + safe(user.userName),
                    "Name: " + safe(user.name),
                    "Surname: " + safe(user.surname),
                    "Email address: " + safe(user.emailAddress),
                    "Phone number: " + safe(user.phoneNumber)
            ));
            return tempFiles.put("ProfileInfo.txt", "text/plain", content.getBytes(StandardCharsets.UTF_8));
        });
    }

    private Optional<FileDto> profilePicture(Long userId) {
        return profilePictureBytes(store.profilePicture(userId))
                .map(bytes -> tempFiles.put("ProfilePicture.png", "image/png", bytes));
    }

    private List<FileDto> chatFiles(Long userId) {
        Map<String, List<ChatMessageItem>> conversations = new LinkedHashMap<>();
        store.chatMessages().stream()
                .filter(item -> Objects.equals(item.userId, userId))
                .sorted(Comparator.comparing(item -> safe(item.creationTime)))
                .forEach(item -> conversations
                        .computeIfAbsent(conversationKey(item.targetUserId, item.targetTenantId), ignored -> new ArrayList<>())
                        .add(item));
        return conversations.values().stream()
                .filter(items -> !items.isEmpty())
                .map(items -> {
                    ChatMessageItem first = items.get(0);
                    return excel.chatMessagesExport(userId, first.targetUserId, first.targetTenantId, items);
                })
                .toList();
    }

    private byte[] zip(List<FileDto> files) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (FileDto file : files) {
                ZipEntry entry = new ZipEntry(file.fileName);
                zip.putNextEntry(entry);
                zip.write(tempFiles.requireContent(file));
                zip.closeEntry();
            }
            zip.finish();
            return bytes.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to create collected data zip", ex);
        }
    }

    private String conversationKey(Long targetUserId, Integer targetTenantId) {
        return "%s:%s".formatted(targetTenantId == null ? "host" : targetTenantId, targetUserId);
    }

    private Optional<byte[]> profilePictureBytes(String value) {
        String normalized = safe(value).trim();
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        int comma = normalized.indexOf(',');
        if (normalized.startsWith("data:image") && comma >= 0) {
            normalized = normalized.substring(comma + 1);
        }
        try {
            return Optional.of(Base64.getDecoder().decode(normalized.replaceAll("\\s+", "")));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
