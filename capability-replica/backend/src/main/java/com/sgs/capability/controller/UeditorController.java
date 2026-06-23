package com.sgs.capability.controller;

import com.sgs.capability.security.RequirePermission;
import com.sgs.capability.service.BinaryFileService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

/** UEditor-compatible image upload endpoints for ability-description rich text. */
@RestController
public class UeditorController {
    private static final long MAX_IMAGE_SIZE = 5L * 1024L * 1024L;

    private final BinaryFileService binaryFiles;

    public UeditorController(BinaryFileService binaryFiles) {
        this.binaryFiles = binaryFiles;
    }

    @PostMapping("/UEditor/UploadImage")
    @RequirePermission("Pages.AbilityManagement.EditDesc")
    public UeditorUploadOutput uploadImage(@RequestParam(value = "upfile", required = false) MultipartFile upfile,
                                           @RequestParam(value = "file", required = false) MultipartFile file) throws IOException {
        MultipartFile imageFile = upfile == null || upfile.isEmpty() ? file : upfile;
        if (imageFile == null || imageFile.isEmpty()) {
            return UeditorUploadOutput.failed("Image_Empty_Error");
        }
        if (imageFile.getSize() > MAX_IMAGE_SIZE) {
            return UeditorUploadOutput.failed("Image_SizeLimit_Error");
        }

        byte[] bytes = imageFile.getBytes();
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null) {
            return UeditorUploadOutput.failed("IncorrectImageFormat");
        }

        String fileName = fileName(imageFile);
        String contentType = contentType(imageFile);
        BinaryFileService.StoredBinaryFile stored = binaryFiles.put(fileName, contentType, bytes);
        String url = UriComponentsBuilder.fromPath("/UEditor/GetImage")
                .queryParam("id", stored.id())
                .queryParam("fileName", fileName)
                .queryParam("contentType", contentType)
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUriString();
        return UeditorUploadOutput.success(url, fileName, extension(fileName), bytes.length);
    }

    @GetMapping("/UEditor/GetImage")
    public ResponseEntity<byte[]> getImage(@RequestParam UUID id,
                                           @RequestParam(required = false) String fileName,
                                           @RequestParam(required = false) String contentType) {
        BinaryFileService.StoredBinaryFile file = binaryFiles.get(id)
                .orElseThrow(() -> new IllegalArgumentException("Binary file not found: " + id));
        String outputName = fileName == null || fileName.isBlank() ? file.fileName() : fileName;
        String outputType = contentType == null || contentType.isBlank() ? file.contentType() : contentType;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + outputName + "\"")
                .contentType(MediaType.parseMediaType(outputType))
                .body(file.content());
    }

    private String fileName(MultipartFile file) {
        String name = file.getOriginalFilename();
        return name == null || name.isBlank() ? "image.png" : name;
    }

    private String contentType(MultipartFile file) {
        String type = file.getContentType();
        return type == null || type.isBlank() ? "image/png" : type;
    }

    private String extension(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index >= 0 ? fileName.substring(index).toLowerCase(Locale.ROOT) : "";
    }

    public record UeditorUploadOutput(String state, String url, String title, String original, String type, long size) {
        static UeditorUploadOutput success(String url, String fileName, String type, long size) {
            return new UeditorUploadOutput("SUCCESS", url, fileName, fileName, type, size);
        }

        static UeditorUploadOutput failed(String state) {
            return new UeditorUploadOutput(state, "", "", "", "", 0);
        }
    }
}
