package com.sgs.capability.controller;

import com.sgs.capability.dto.AbpResponse;
import com.sgs.capability.dto.FileDto;
import com.sgs.capability.security.AllowAnonymous;
import com.sgs.capability.security.RequirePermission;
import com.sgs.capability.service.TempFileService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/** MVC profile picture upload route copied from ProfileControllerBase. */
@RestController
public class ProfileUploadController {
    private static final long MAX_PROFILE_PICTURE_SIZE = 5L * 1024L * 1024L;

    private final TempFileService tempFiles;

    public ProfileUploadController(TempFileService tempFiles) {
        this.tempFiles = tempFiles;
    }

    @GetMapping("/Profile/GetDefaultProfilePicture")
    @AllowAnonymous
    public ResponseEntity<byte[]> getDefaultProfilePicture() throws IOException {
        byte[] image;
        try (InputStream stream = new ClassPathResource("static/Common/Images/default-profile-picture.png").getInputStream()) {
            image = stream.readAllBytes();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(image);
    }

    @PostMapping("/Profile/UploadProfilePicture")
    @RequirePermission
    public AbpResponse<UploadProfilePictureOutput> uploadProfilePicture(MultipartHttpServletRequest request,
                                                                        @RequestParam(required = false) String fileToken,
                                                                        @RequestParam(required = false) String fileName,
                                                                        @RequestParam(required = false) String fileType) throws IOException {
        MultipartFile file = firstUploadedFile(request);
        if (file == null || file.isEmpty()) {
            return AbpResponse.failed("ProfilePicture_Change_Error");
        }
        if (file.getSize() > MAX_PROFILE_PICTURE_SIZE) {
            return AbpResponse.failed("ProfilePicture_Warn_SizeLimit");
        }
        byte[] bytes = file.getBytes();
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null) {
            return AbpResponse.failed("IncorrectImageFormat");
        }
        FileDto dto = new FileDto(
                stringOr(fileName, file.getOriginalFilename()),
                stringOr(fileType, file.getContentType()),
                stringOr(fileToken, UUID.randomUUID().toString().replace("-", ""))
        );
        tempFiles.put(dto, bytes);
        return AbpResponse.ok(new UploadProfilePictureOutput(dto.fileToken, dto.fileName, dto.fileType, image.getWidth(), image.getHeight()));
    }

    private String stringOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private MultipartFile firstUploadedFile(MultipartHttpServletRequest request) {
        // Original MVC route reads Request.Form.Files.First(), independent of form field name.
        return request.getFileMap().values().stream().findFirst().orElse(null);
    }

    public record UploadProfilePictureOutput(String fileToken, String fileName, String fileType, int width, int height) {
    }
}
