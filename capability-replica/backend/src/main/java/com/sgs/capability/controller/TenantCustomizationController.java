package com.sgs.capability.controller;

import com.sgs.capability.dto.AbpResponse;
import com.sgs.capability.model.TenantItem;
import com.sgs.capability.security.AuthContext;
import com.sgs.capability.security.AuthService;
import com.sgs.capability.security.RequirePermission;
import com.sgs.capability.service.CapabilityStore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** MVC tenant branding routes copied from TenantCustomizationController. */
@RestController
@RequestMapping("/TenantCustomization")
public class TenantCustomizationController {
    private static final int MAX_LOGO_BYTES = 30 * 1024;
    private static final int MAX_CSS_BYTES = 1024 * 1024;

    private final CapabilityStore store;
    private final AuthService auth;

    public TenantCustomizationController(CapabilityStore store, AuthService auth) {
        this.store = store;
        this.auth = auth;
    }

    @PostMapping("/UploadLogo")
    @RequirePermission("Pages.Administration.Tenant.Settings")
    public AbpResponse<UploadTenantBrandingResult> uploadLogo(MultipartHttpServletRequest multipartRequest,
                                                              HttpServletRequest request) {
        try {
            MultipartFile file = firstUploadedFile(multipartRequest);
            if (file == null || file.isEmpty()) {
                return AbpResponse.failed("File_Empty_Error");
            }
            if (file.getSize() > MAX_LOGO_BYTES) {
                return AbpResponse.failed("File_SizeLimit_Error");
            }
            byte[] content = file.getBytes();
            if (!isSupportedImage(content)) {
                return AbpResponse.failed("File_Invalid_Type_Error");
            }
            TenantItem tenant = store.saveTenantLogo(currentTenantId(request), imageContentType(file.getContentType(), content), content);
            return AbpResponse.ok(new UploadTenantBrandingResult(tenant.logoId, tenant.id, tenant.logoFileType));
        } catch (IOException ex) {
            return AbpResponse.failed(ex.getMessage());
        }
    }

    @PostMapping("/UploadCustomCss")
    @RequirePermission("Pages.Administration.Tenant.Settings")
    public AbpResponse<UploadTenantBrandingResult> uploadCustomCss(MultipartHttpServletRequest multipartRequest,
                                                                   HttpServletRequest request) {
        try {
            MultipartFile file = firstUploadedFile(multipartRequest);
            if (file == null || file.isEmpty()) {
                return AbpResponse.failed("File_Empty_Error");
            }
            if (file.getSize() > MAX_CSS_BYTES) {
                return AbpResponse.failed("File_SizeLimit_Error");
            }
            TenantItem tenant = store.saveTenantCustomCss(currentTenantId(request), file.getBytes());
            return AbpResponse.ok(new UploadTenantBrandingResult(tenant.customCssId, tenant.id, "text/css"));
        } catch (IOException ex) {
            return AbpResponse.failed(ex.getMessage());
        }
    }

    @GetMapping("/GetLogo")
    public ResponseEntity<byte[]> getLogo(@RequestParam(required = false) Integer tenantId,
                                          HttpServletRequest request) {
        return store.tenantLogo(resolveTenantId(tenantId, request))
                .map(file -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(file.fileType()))
                        .body(file.content()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/GetTenantLogo")
    public ResponseEntity<byte[]> getTenantLogo(@RequestParam(defaultValue = "light") String skin,
                                                @RequestParam(required = false) Integer tenantId) {
        return store.tenantLogo(tenantId)
                .map(file -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(file.fileType()))
                        .body(file.content()))
                .orElseGet(() -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType("image/svg+xml"))
                        .body(defaultLogo(skin)));
    }

    @GetMapping("/GetCustomCss")
    public ResponseEntity<byte[]> getCustomCss(@RequestParam(required = false) Integer tenantId,
                                               HttpServletRequest request) {
        return store.tenantCustomCss(resolveTenantId(tenantId, request))
                .map(file -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType("text/css"))
                        .body(file.content()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private int currentTenantId(HttpServletRequest request) {
        return auth.currentUser(request.getHeader("Authorization"))
                .map(AuthContext::tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
    }

    private Integer resolveTenantId(Integer tenantId, HttpServletRequest request) {
        if (tenantId != null) {
            return tenantId;
        }
        return auth.currentUser(request.getHeader("Authorization"))
                .map(AuthContext::tenantId)
                .orElse(null);
    }

    private MultipartFile firstUploadedFile(MultipartHttpServletRequest request) {
        // Original MVC routes read Request.Form.Files.First(), independent of form field name.
        return request.getFileMap().values().stream().findFirst().orElse(null);
    }

    private boolean isSupportedImage(byte[] content) {
        return isPng(content) || isJpeg(content) || isGif(content);
    }

    private String imageContentType(String provided, byte[] content) {
        if (provided != null && !provided.isBlank() && !provided.equals("application/octet-stream")) {
            return provided;
        }
        if (isPng(content)) {
            return "image/png";
        }
        if (isJpeg(content)) {
            return "image/jpeg";
        }
        return "image/gif";
    }

    private boolean isPng(byte[] value) {
        return value.length > 8 && value[0] == (byte) 0x89 && value[1] == 0x50 && value[2] == 0x4E
                && value[3] == 0x47;
    }

    private boolean isJpeg(byte[] value) {
        return value.length > 3 && value[0] == (byte) 0xFF && value[1] == (byte) 0xD8 && value[2] == (byte) 0xFF;
    }

    private boolean isGif(byte[] value) {
        return value.length > 6 && value[0] == 0x47 && value[1] == 0x49 && value[2] == 0x46;
    }

    private byte[] defaultLogo(String skin) {
        String fill = "dark".equalsIgnoreCase(skin) ? "#ffffff" : "#111827";
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"180\" height=\"42\" viewBox=\"0 0 180 42\">"
                + "<rect width=\"42\" height=\"42\" rx=\"8\" fill=\"#16a34a\"/>"
                + "<text x=\"21\" y=\"27\" text-anchor=\"middle\" font-family=\"Arial\" font-size=\"16\" font-weight=\"700\" fill=\"white\">SGS</text>"
                + "<text x=\"54\" y=\"27\" font-family=\"Arial\" font-size=\"18\" font-weight=\"700\" fill=\"" + fill + "\">Capability</text>"
                + "</svg>";
        return svg.getBytes(StandardCharsets.UTF_8);
    }

    public record UploadTenantBrandingResult(String id, Integer tenantId, String fileType) {
    }
}
