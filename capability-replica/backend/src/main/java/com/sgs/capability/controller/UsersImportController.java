package com.sgs.capability.controller;

import com.sgs.capability.dto.AbpResponse;
import com.sgs.capability.dto.FileDto;
import com.sgs.capability.dto.UserImportOutput;
import com.sgs.capability.security.AuthContext;
import com.sgs.capability.security.AuthService;
import com.sgs.capability.security.RequirePermission;
import com.sgs.capability.service.ExcelTransferService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.io.IOException;

/** MVC user Excel import route copied from UsersControllerBase. */
@RestController
@RequestMapping("/Users")
public class UsersImportController {
    private static final long MAX_IMPORT_BYTES = 100L * 1024L * 1024L;

    private final AuthService auth;
    private final ExcelTransferService excel;

    public UsersImportController(AuthService auth, ExcelTransferService excel) {
        this.auth = auth;
        this.excel = excel;
    }

    @PostMapping("/ImportFromExcel")
    @RequirePermission("Pages.Administration.Users.Create")
    public AbpResponse<UserImportOutput> importFromExcel(MultipartHttpServletRequest multipartRequest,
                                                         HttpServletRequest request) {
        try {
            AuthContext context = auth.currentUser(request.getHeader("Authorization")).orElse(null);
            if (context == null) {
                return AbpResponse.denied("未登录或登录已过期");
            }
            MultipartFile file = firstUploadedFile(multipartRequest);
            if (file == null || file.isEmpty()) {
                return AbpResponse.failed("File_Empty_Error");
            }
            if (file.getSize() > MAX_IMPORT_BYTES) {
                return AbpResponse.failed("File_SizeLimit_Error");
            }
            FileDto uploaded = excel.storeUpload(file);
            return AbpResponse.ok(excel.importUsers(uploaded, context.user().id));
        } catch (IOException ex) {
            return AbpResponse.failed(ex.getMessage());
        }
    }

    private MultipartFile firstUploadedFile(MultipartHttpServletRequest request) {
        // Original MVC route reads Request.Form.Files.First(), independent of form field name.
        return request.getFileMap().values().stream().findFirst().orElse(null);
    }
}
