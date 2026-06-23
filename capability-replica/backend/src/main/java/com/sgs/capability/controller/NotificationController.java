package com.sgs.capability.controller;

import com.sgs.capability.dto.AbpResponse;
import com.sgs.capability.dto.IdRequest;
import com.sgs.capability.dto.PageResult;
import com.sgs.capability.model.NotificationItem;
import com.sgs.capability.model.NotificationSettings;
import com.sgs.capability.model.NotificationSubscription;
import com.sgs.capability.security.AuthContext;
import com.sgs.capability.security.AuthService;
import com.sgs.capability.security.RequirePermission;
import com.sgs.capability.service.CapabilityStore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Mirrors NotificationAppService user notification routes. */
@RestController
@RequestMapping("/api/services/app/Notification")
@RequirePermission
public class NotificationController {
    private final AuthService auth;
    private final CapabilityStore store;

    public NotificationController(AuthService auth, CapabilityStore store) {
        this.auth = auth;
        this.store = store;
    }

    @PostMapping("/GetUserNotifications")
    public AbpResponse<GetNotificationsOutput> userNotifications(@RequestBody(required = false) GetUserNotificationsInput input,
                                                                 HttpServletRequest request) {
        GetUserNotificationsInput safeInput = input == null ? new GetUserNotificationsInput() : input;
        return userNotificationsResponse(safeInput, request);
    }

    @GetMapping("/GetUserNotifications")
    public AbpResponse<GetNotificationsOutput> userNotificationsByQuery(@RequestParam(name = "State", required = false) String state,
                                                                        @RequestParam(name = "StartDate", required = false) String startDate,
                                                                        @RequestParam(name = "EndDate", required = false) String endDate,
                                                                        @RequestParam(name = "MaxResultCount", required = false) Integer maxResultCount,
                                                                        @RequestParam(name = "SkipCount", required = false) Integer skipCount,
                                                                        @RequestParam(name = "Filter", required = false) String filter,
                                                                        HttpServletRequest request) {
        // Original proxy sends notification list filters as GET query parameters.
        GetUserNotificationsInput input = new GetUserNotificationsInput();
        input.state = state == null ? "ALL" : state;
        input.startDate = startDate;
        input.endDate = endDate;
        input.skipCount = skipCount == null ? 0 : skipCount;
        input.maxResultCount = maxResultCount == null ? 10 : maxResultCount;
        input.filter = filter;
        return userNotificationsResponse(input, request);
    }

    private AbpResponse<GetNotificationsOutput> userNotificationsResponse(GetUserNotificationsInput input,
                                                                          HttpServletRequest request) {
        AuthContext context = current(request);
        if (context == null) {
            return AbpResponse.denied("未登录或登录已过期");
        }
        String validationError = validatePagedInput(input.skipCount, input.maxResultCount);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        PageResult<NotificationItem> page = store.userNotifications(context.user().id, input.filter,
                input.state, input.startDate, input.endDate, input.skipCount, input.maxResultCount);
        return AbpResponse.ok(new GetNotificationsOutput(page.totalCount, page.items,
                store.unreadNotificationCount(context.user().id, input.startDate, input.endDate)));
    }

    @PostMapping("/SetAllNotificationsAsRead")
    public AbpResponse<Void> setAllNotificationsAsRead(HttpServletRequest request) {
        AuthContext context = current(request);
        if (context == null) {
            return AbpResponse.denied("未登录或登录已过期");
        }
        store.setAllNotificationsAsRead(context.user().id);
        return AbpResponse.ok(null);
    }

    @PostMapping("/SetNotificationAsRead")
    public AbpResponse<Void> setNotificationAsRead(@RequestBody IdRequest input, HttpServletRequest request) {
        AuthContext context = current(request);
        if (context == null) {
            return AbpResponse.denied("未登录或登录已过期");
        }
        var error = store.setNotificationAsRead(context.user().id, input.id);
        if (error.isPresent()) {
            return AbpResponse.failed(error.get());
        }
        return AbpResponse.ok(null);
    }

    @GetMapping("/GetNotificationSettings")
    public AbpResponse<GetNotificationSettingsOutput> notificationSettings(HttpServletRequest request) {
        AuthContext context = current(request);
        if (context == null) {
            return AbpResponse.denied("未登录或登录已过期");
        }
        NotificationSettings settings = store.notificationSettings(context.user().id);
        return AbpResponse.ok(new GetNotificationSettingsOutput(settings.receiveNotifications, settings.notifications));
    }

    @PostMapping("/UpdateNotificationSettings")
    public AbpResponse<Void> updateNotificationSettings(@RequestBody UpdateNotificationSettingsInput input,
                                                        HttpServletRequest request) {
        return saveNotificationSettings(input, request);
    }

    @PutMapping("/UpdateNotificationSettings")
    public AbpResponse<Void> putUpdateNotificationSettings(@RequestBody UpdateNotificationSettingsInput input,
                                                           HttpServletRequest request) {
        return saveNotificationSettings(input, request);
    }

    private AbpResponse<Void> saveNotificationSettings(UpdateNotificationSettingsInput input, HttpServletRequest request) {
        AuthContext context = current(request);
        if (context == null) {
            return AbpResponse.denied("未登录或登录已过期");
        }
        String validationError = validateNotificationSettingsInput(input);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        NotificationSettings settings = store.notificationSettings(context.user().id);
        if (input != null) {
            settings.receiveNotifications = input.receiveNotifications;
            settings.notifications = input.notifications;
        }
        store.saveNotificationSettings(context.user().id, settings);
        return AbpResponse.ok(null);
    }

    @PostMapping("/DeleteNotification")
    public AbpResponse<Void> deleteNotification(@RequestBody IdRequest input, HttpServletRequest request) {
        AuthContext context = current(request);
        if (context == null) {
            return AbpResponse.denied("未登录或登录已过期");
        }
        var error = store.deleteNotification(context.user().id, input.id);
        if (error.isPresent()) {
            return AbpResponse.failed(error.get());
        }
        return AbpResponse.ok(null);
    }

    @DeleteMapping("/DeleteNotification")
    public AbpResponse<Void> deleteNotificationByQuery(@RequestParam(name = "Id", required = false) String id,
                                                       HttpServletRequest request) {
        AuthContext context = current(request);
        if (context == null) {
            return AbpResponse.denied("未登录或登录已过期");
        }
        var error = store.deleteNotification(context.user().id, id);
        if (error.isPresent()) {
            return AbpResponse.failed(error.get());
        }
        return AbpResponse.ok(null);
    }

    @PostMapping("/DeleteAllUserNotifications")
    public AbpResponse<Void> deleteAllUserNotifications(@RequestBody(required = false) DeleteAllUserNotificationsInput input,
                                                        HttpServletRequest request) {
        AuthContext context = current(request);
        if (context == null) {
            return AbpResponse.denied("未登录或登录已过期");
        }
        store.deleteAllUserNotifications(context.user().id, input == null ? null : input.state,
                input == null ? null : input.startDate, input == null ? null : input.endDate);
        return AbpResponse.ok(null);
    }

    @DeleteMapping("/DeleteAllUserNotifications")
    public AbpResponse<Void> deleteAllUserNotificationsByQuery(@RequestParam(name = "State", required = false) String state,
                                                               @RequestParam(name = "StartDate", required = false) String startDate,
                                                               @RequestParam(name = "EndDate", required = false) String endDate,
                                                               HttpServletRequest request) {
        AuthContext context = current(request);
        if (context == null) {
            return AbpResponse.denied("未登录或登录已过期");
        }
        store.deleteAllUserNotifications(context.user().id, state, startDate, endDate);
        return AbpResponse.ok(null);
    }

    private AuthContext current(HttpServletRequest request) {
        return auth.currentUser(request.getHeader("Authorization")).orElse(null);
    }

    private String validateNotificationSettingsInput(UpdateNotificationSettingsInput input) {
        if (input == null || input.notifications == null) {
            return null;
        }
        boolean invalidSubscription = input.notifications.stream()
                .anyMatch(item -> item == null || item.name == null || item.name.isBlank() || item.name.length() > 96);
        if (invalidSubscription) {
            // 原 NotificationSubscriptionDto 要求 Name 必填且最多 96。
            return "Validation failed";
        }
        return null;
    }

    private String validatePagedInput(int skipCount, int maxResultCount) {
        if (skipCount < 0 || maxResultCount < 1 || maxResultCount > 1000) {
            // 原 PagedInputDto 要求 MaxResultCount 为 1-1000，SkipCount 不能为负。
            return "Validation failed";
        }
        return null;
    }

    public record GetNotificationsOutput(long totalCount, List<NotificationItem> items, long unreadCount) {
    }

    public record GetNotificationSettingsOutput(boolean receiveNotifications, List<NotificationSubscription> notifications) {
    }

    public static class GetUserNotificationsInput {
        public String filter;
        public String state = "ALL";
        public String startDate;
        public String endDate;
        public int skipCount;
        public int maxResultCount = 10;
    }

    public static class DeleteAllUserNotificationsInput {
        public String state = "ALL";
        public String startDate;
        public String endDate;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UpdateNotificationSettingsInput {
        public boolean receiveNotifications;
        public List<NotificationSubscription> notifications;
    }
}
