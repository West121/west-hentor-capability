package com.sgs.capability.controller;

import com.sgs.capability.dto.AbpResponse;
import com.sgs.capability.dto.AbilityTableUploadOutput;
import com.sgs.capability.dto.FileDto;
import com.sgs.capability.dto.UploadStandardOutput;
import com.sgs.capability.dto.UploadSubcontractAbilityOutput;
import com.sgs.capability.security.AuthService;
import com.sgs.capability.security.RequirePermission;
import com.sgs.capability.service.ExcelTransferService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.io.IOException;
import java.util.List;

/** Mirrors StandardAppService and MVC upload routes. */
@RestController
public class StandardController {
    private final ExcelTransferService excel;
    private final AuthService auth;

    public StandardController(ExcelTransferService excel, AuthService auth) {
        this.excel = excel;
        this.auth = auth;
    }

    @PostMapping("/api/services/app/Standard/UploadNewStandard")
    @RequirePermission("Pages.Administration.StandardUpdate")
    public AbpResponse<Void> uploadNewStandard(@RequestBody(required = false) UploadStandardOutput input,
                                               HttpServletRequest request) {
        excel.applyStandardUpdate(input, currentUserName(request), currentUserId(request));
        return AbpResponse.ok(null);
    }

    @PostMapping("/AbilityTable/UploadNewStandard")
    @RequirePermission("Pages.Administration.StandardUpdate")
    public UploadStandardOutput uploadNewStandardFile(MultipartHttpServletRequest request) throws IOException {
        MultipartFile file = firstUploadedFile(request);
        FileDto uploaded = excel.storeUpload(file);
        return excel.parseStandardUpload(uploaded);
    }

    @PostMapping("/AbilityTable/UploadSubcontractAbility")
    @RequirePermission("Pages.AbilityManagement")
    public UploadSubcontractAbilityOutput uploadSubcontractAbility(MultipartHttpServletRequest request) throws IOException {
        MultipartFile file = firstUploadedFile(request);
        FileDto uploaded = excel.storeUpload(file);
        return excel.parseSubcontractUpload(uploaded);
    }

    @PostMapping("/AbilityTable/UploadAbilityTable")
    @RequirePermission("Pages.AbilityManagement")
    public ResponseEntity<?> uploadAbilityTable(
            MultipartHttpServletRequest request,
            @RequestParam(value = "orgId", required = false) Long orgId) throws IOException {
        MultipartFile file = firstUploadedFile(request);
        String contextError = excel.abilityUploadContextError(orgId);
        if (contextError != null) {
            return ResponseEntity.internalServerError().body(AbpResponse.failed(contextError));
        }
        FileDto uploaded = excel.storeUpload(file);
        return ResponseEntity.ok(excel.parseAbilityUpload(uploaded, orgId));
    }

    private MultipartFile firstUploadedFile(MultipartHttpServletRequest request) {
        return request.getMultiFileMap().values().stream()
                .flatMap(List::stream)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("没有上传文件"));
    }

    private String currentUserName(HttpServletRequest request) {
        return auth.currentUser(request.getHeader("Authorization"))
                .map(context -> context.user().userName)
                .orElse("Admin");
    }

    private Long currentUserId(HttpServletRequest request) {
        return auth.currentUser(request.getHeader("Authorization"))
                .map(context -> context.user().id)
                .orElse(1L);
    }
}
