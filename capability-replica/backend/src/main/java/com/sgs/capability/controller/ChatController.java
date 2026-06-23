package com.sgs.capability.controller;

import com.sgs.capability.dto.AbpResponse;
import com.sgs.capability.dto.ListResult;
import com.sgs.capability.model.ChatMessageItem;
import com.sgs.capability.model.FriendItem;
import com.sgs.capability.security.AuthContext;
import com.sgs.capability.security.AuthService;
import com.sgs.capability.security.RequirePermission;
import com.sgs.capability.service.CapabilityStore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/** Mirrors ChatAppService routes with local REST message sending. */
@RestController
@RequestMapping("/api/services/app/Chat")
@RequirePermission
public class ChatController {
    private final AuthService auth;
    private final CapabilityStore store;

    public ChatController(AuthService auth, CapabilityStore store) {
        this.auth = auth;
        this.store = store;
    }

    @GetMapping("/GetUserChatFriendsWithSettings")
    public AbpResponse<GetUserChatFriendsWithSettingsOutput> friends(HttpServletRequest request) {
        AuthContext context = current(request);
        if (context == null) {
            return AbpResponse.denied("未登录或登录已过期");
        }
        return AbpResponse.ok(new GetUserChatFriendsWithSettingsOutput(
                LocalDateTime.now().toString(),
                store.chatFriends(context.user().id)
        ));
    }

    @PostMapping("/GetUserChatFriendsWithSettings")
    public AbpResponse<GetUserChatFriendsWithSettingsOutput> postFriends(HttpServletRequest request) {
        return friends(request);
    }

    @PostMapping("/GetUserChatMessages")
    public AbpResponse<ListResult<ChatMessageItem>> messages(@RequestBody GetUserChatMessagesInput input,
                                                             HttpServletRequest request) {
        GetUserChatMessagesInput safeInput = input == null ? new GetUserChatMessagesInput() : input;
        return messagesResponse(safeInput, request);
    }

    @GetMapping("/GetUserChatMessages")
    public AbpResponse<ListResult<ChatMessageItem>> messagesByQuery(@RequestParam(name = "TenantId", required = false) Integer tenantId,
                                                                    @RequestParam(name = "UserId", required = false) Long userId,
                                                                    @RequestParam(name = "MinMessageId", required = false) Long minMessageId,
                                                                    HttpServletRequest request) {
        // Original proxy sends chat message filters as GET query parameters.
        GetUserChatMessagesInput input = new GetUserChatMessagesInput();
        input.tenantId = tenantId;
        input.userId = userId;
        input.minMessageId = minMessageId;
        return messagesResponse(input, request);
    }

    private AbpResponse<ListResult<ChatMessageItem>> messagesResponse(GetUserChatMessagesInput input,
                                                                      HttpServletRequest request) {
        AuthContext context = current(request);
        if (context == null) {
            return AbpResponse.denied("未登录或登录已过期");
        }
        if (!isPositive(input.userId)) {
            // 原 GetUserChatMessagesInput.UserId 要求大于 0。
            return AbpResponse.failed("Validation failed");
        }
        return AbpResponse.ok(new ListResult<>(store.chatMessages(context.user().id, input.userId,
                input.tenantId, input.minMessageId)));
    }

    @PostMapping("/MarkAllUnreadMessagesOfUserAsRead")
    public AbpResponse<Void> markAllUnreadMessagesOfUserAsRead(@RequestBody MarkAllUnreadMessagesOfUserAsReadInput input,
                                                               HttpServletRequest request) {
        AuthContext context = current(request);
        if (context == null) {
            return AbpResponse.denied("未登录或登录已过期");
        }
        MarkAllUnreadMessagesOfUserAsReadInput safeInput = input == null ? new MarkAllUnreadMessagesOfUserAsReadInput() : input;
        store.markChatMessagesRead(context.user().id, safeInput.userId, safeInput.tenantId);
        return AbpResponse.ok(null);
    }

    @PostMapping("/SendMessage")
    public AbpResponse<ChatMessageItem> sendMessage(@RequestBody SendMessageInput input, HttpServletRequest request) {
        AuthContext context = current(request);
        if (context == null) {
            return AbpResponse.denied("未登录或登录已过期");
        }
        SendMessageInput safeInput = input == null ? new SendMessageInput() : input;
        if (safeInput.userId != null && store.user(safeInput.userId).isEmpty()) {
            return AbpResponse.failed("Target user could not be found. It's probably deleted.");
        }
        // Original ChatMessageManager rejects sending to a user blocked by the sender.
        if (store.isFriendBlocked(context.user().id, safeInput.userId, safeInput.tenantId)) {
            return AbpResponse.failed("User is blocked.");
        }
        ChatMessageItem item = store.sendChatMessage(context.user().id, safeInput.userId, safeInput.message);
        if (item == null) {
            return AbpResponse.failed("请选择有效好友并输入消息");
        }
        return AbpResponse.ok(item);
    }

    private AuthContext current(HttpServletRequest request) {
        return auth.currentUser(request.getHeader("Authorization")).orElse(null);
    }

    private boolean isPositive(Long value) {
        return value != null && value > 0;
    }

    public record GetUserChatFriendsWithSettingsOutput(String serverTime, List<FriendItem> friends) {
    }

    public static class GetUserChatMessagesInput {
        public Integer tenantId;
        public Long userId;
        public Long minMessageId;
    }

    public static class MarkAllUnreadMessagesOfUserAsReadInput {
        public Integer tenantId;
        public Long userId;
    }

    public static class SendMessageInput {
        public Integer tenantId;
        public Long userId;
        public String message;
    }
}
