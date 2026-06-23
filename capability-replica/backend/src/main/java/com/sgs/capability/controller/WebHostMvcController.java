package com.sgs.capability.controller;

import com.sgs.capability.dto.AbpResponse;
import com.sgs.capability.security.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.Duration;
import java.util.UUID;

/** Copies lightweight Web.Host MVC endpoints around the React shell. */
@Controller
public class WebHostMvcController {
    private final AuthService auth;

    public WebHostMvcController(AuthService auth) {
        this.auth = auth;
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/Ui";
    }

    @GetMapping({"/Ui", "/Ui/Index"})
    public String uiIndex(HttpServletRequest request) {
        return auth.currentUser(request.getHeader("Authorization")).isPresent()
                ? "redirect:/dashboard/v1"
                : "redirect:/login";
    }

    @GetMapping("/Ui/Login")
    public String uiLogin(@RequestParam(name = "returnUrl", required = false) String returnUrl) {
        if (returnUrl == null || returnUrl.isBlank()) {
            return "redirect:/login";
        }
        // 原 MVC 登录页会保留 ReturnUrl，这里转交给 React 登录页继续处理。
        return "redirect:/login?returnUrl=" + returnUrl;
    }

    @PostMapping("/Ui/Login")
    public String postUiLogin(@RequestParam(name = "userNameOrEmailAddress", required = false) String userNameOrEmailAddress,
                              @RequestParam(name = "password", required = false) String password,
                              @RequestParam(name = "returnUrl", required = false) String returnUrl) {
        if (auth.authenticate(userNameOrEmailAddress, password).isEmpty()) {
            return "redirect:/login";
        }
        // 原 UiController.Login 成功后优先跳转 returnUrl，否则回 Index。
        return returnUrl == null || returnUrl.isBlank() ? "redirect:/Ui" : "redirect:" + returnUrl;
    }

    @RequestMapping("/Ui/Logout")
    public String uiLogout() {
        return "redirect:/login";
    }

    @GetMapping("/AntiForgery/GetToken")
    @ResponseBody
    public ResponseEntity<Void> antiForgeryToken() {
        String token = UUID.randomUUID().toString();
        ResponseCookie cookie = ResponseCookie.from("XSRF-TOKEN", token)
                .path("/")
                .maxAge(Duration.ofHours(8))
                .sameSite("Lax")
                .build();
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .header("X-XSRF-TOKEN", token)
                .build();
    }

    @GetMapping("/api/AbpUserConfiguration/GetAll")
    @ResponseBody
    public AbpResponse<Object> abpUserConfiguration(HttpServletRequest request) {
        return AbpResponse.ok(auth.currentUser(request.getHeader("Authorization")).orElse(null));
    }
}
