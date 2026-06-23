package com.sgs.capability.controller;

import com.sgs.capability.dto.AbpResponse;
import com.sgs.capability.dto.FileDto;
import com.sgs.capability.model.WebLogOutput;
import com.sgs.capability.security.RequirePermission;
import com.sgs.capability.service.WebLogService;
import org.springframework.web.bind.annotation.*;

/** Mirrors WebLogAppService maintenance routes. */
@RestController
@RequestMapping("/api/services/app/WebLog")
@RequirePermission("Pages.Administration.Host.Maintenance")
public class WebLogController {
    private final WebLogService webLogs;

    public WebLogController(WebLogService webLogs) {
        this.webLogs = webLogs;
    }

    @RequestMapping(value = "/GetLatestWebLogs", method = {RequestMethod.GET, RequestMethod.POST})
    public AbpResponse<WebLogOutput> getLatestWebLogs() {
        return AbpResponse.ok(webLogs.latestWebLogs());
    }

    @RequestMapping(value = "/DownloadWebLogs", method = {RequestMethod.GET, RequestMethod.POST})
    public AbpResponse<FileDto> downloadWebLogs() {
        return AbpResponse.ok(webLogs.downloadWebLogs());
    }
}
