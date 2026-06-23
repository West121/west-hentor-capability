package com.sgs.capability.controller;

import com.sgs.capability.security.RequirePermission;
import com.sgs.capability.service.BinaryFileService;
import com.sgs.capability.service.TempFileService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** File download endpoint copied from ABP FileController. */
@RestController
@RequestMapping("/File")
@RequirePermission
public class FileController {
    private final TempFileService tempFiles;
    private final BinaryFileService binaryFiles;

    public FileController(TempFileService tempFiles, BinaryFileService binaryFiles) {
        this.tempFiles = tempFiles;
        this.binaryFiles = binaryFiles;
    }

    @GetMapping("/DownloadTempFile")
    public ResponseEntity<byte[]> downloadTempFile(@RequestParam(required = false) String fileToken,
                                                   @RequestParam(required = false) String fileName,
                                                   @RequestParam(required = false) String fileType) {
        if (!hasText(fileToken) || !hasText(fileName)) {
            // 原 FileDto 要求 FileName 和 FileToken 必填，FileType 可为空。
            return ResponseEntity.badRequest().build();
        }
        TempFileService.StoredFile file = tempFiles.get(fileToken).orElse(null);
        if (file == null) {
            return ResponseEntity.notFound().build();
        }
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(fileName, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(hasText(fileType) ? fileType : file.fileType()))
                .body(file.content());
    }

    @GetMapping("/DownloadBinaryFile")
    public ResponseEntity<byte[]> downloadBinaryFile(@RequestParam UUID id,
                                                     @RequestParam String contentType,
                                                     @RequestParam String fileName) {
        BinaryFileService.StoredBinaryFile file = binaryFiles.get(id).orElse(null);
        if (file == null) {
            return ResponseEntity.notFound().build();
        }
        return download(fileName, contentType, file.content());
    }

    static ResponseEntity<byte[]> download(String fileName, String contentType, byte[] content) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(content);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
