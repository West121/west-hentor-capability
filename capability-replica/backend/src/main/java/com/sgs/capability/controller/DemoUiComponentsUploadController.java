package com.sgs.capability.controller;

import com.sgs.capability.dto.AbpResponse;
import com.sgs.capability.dto.UploadFileOutput;
import com.sgs.capability.security.RequirePermission;
import com.sgs.capability.service.BinaryFileService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** MVC upload route copied from DemoUiComponentsController. */
@RestController
@RequestMapping("/DemoUiComponents")
@RequirePermission("Pages.DemoUiComponents")
public class DemoUiComponentsUploadController {
    private static final long MAX_FILE_BYTES = 1024L * 1024L;

    private final BinaryFileService binaryFiles;

    public DemoUiComponentsUploadController(BinaryFileService binaryFiles) {
        this.binaryFiles = binaryFiles;
    }

    @PostMapping("/UploadFiles")
    public AbpResponse<List<UploadFileOutput>> uploadFiles(HttpServletRequest request) {
        if (!(request instanceof MultipartHttpServletRequest multipart)) {
            return AbpResponse.failed("File_Empty_Error");
        }
        List<MultipartFile> files = multipart.getMultiFileMap().values().stream()
                .flatMap(List::stream)
                .filter(file -> file != null && !file.isEmpty())
                .toList();
        if (files.isEmpty()) {
            return AbpResponse.failed("File_Empty_Error");
        }
        List<UploadFileOutput> output = new ArrayList<>();
        try {
            for (MultipartFile file : files) {
                if (file.getSize() > MAX_FILE_BYTES) {
                    return AbpResponse.failed("File_SizeLimit_Error");
                }
                BinaryFileService.StoredBinaryFile saved = binaryFiles.put(file.getOriginalFilename(), file.getContentType(), file.getBytes());
                output.add(new UploadFileOutput(saved.id().toString(), saved.fileName()));
            }
            return AbpResponse.ok(output);
        } catch (IOException ex) {
            return AbpResponse.failed(ex.getMessage());
        }
    }
}
