package com.sgs.capability.controller;

import com.sgs.capability.dto.AbpResponse;
import com.sgs.capability.security.RequirePermission;
import com.sgs.capability.service.BinaryFileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.io.IOException;
import java.util.UUID;

/** MVC chat file endpoints copied from ChatControllerBase and ChatController. */
@RestController
public class ChatFileController {
    private static final long MAX_CHAT_FILE_SIZE = 10L * 1000L * 1000L;

    private final BinaryFileService binaryFiles;

    public ChatFileController(BinaryFileService binaryFiles) {
        this.binaryFiles = binaryFiles;
    }

    @PostMapping("/Chat/UploadFile")
    @RequirePermission
    public AbpResponse<ChatUploadFileOutput> uploadFile(MultipartHttpServletRequest request) throws IOException {
        MultipartFile file = firstUploadedFile(request);
        if (file == null || file.isEmpty()) {
            return AbpResponse.failed("File_Empty_Error");
        }
        if (file.getSize() > MAX_CHAT_FILE_SIZE) {
            return AbpResponse.failed("File_SizeLimit_Error");
        }
        BinaryFileService.StoredBinaryFile stored = binaryFiles.put(file.getOriginalFilename(), file.getContentType(), file.getBytes());
        return AbpResponse.ok(new ChatUploadFileOutput(stored.id(), stored.fileName(), stored.contentType()));
    }

    @GetMapping("/Chat/GetUploadedObject")
    public ResponseEntity<byte[]> getUploadedObject(@RequestParam UUID fileId,
                                                    @RequestParam String fileName,
                                                    @RequestParam String contentType) {
        BinaryFileService.StoredBinaryFile file = binaryFiles.get(fileId)
                .orElseThrow(() -> new IllegalArgumentException("Binary file not found: " + fileId));
        return FileController.download(fileName, contentType, file.content());
    }

    private MultipartFile firstUploadedFile(MultipartHttpServletRequest request) {
        // Original MVC route reads Request.Form.Files.First(), independent of form field name.
        return request.getFileMap().values().stream().findFirst().orElse(null);
    }

    public record ChatUploadFileOutput(UUID id, String name, String contentType) {
    }
}
