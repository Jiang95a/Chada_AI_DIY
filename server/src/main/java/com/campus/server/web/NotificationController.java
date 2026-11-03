package com.campus.server.web;

import com.campus.server.common.ApiResponse;
import com.campus.server.dto.Dtos;
import com.campus.server.service.NotificationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 站内通知。对应前端 {@code WorkspaceShell.vue} 的铃铛。
 *
 * <p>以前前端是两条写死假数据、已读状态塞 localStorage（键 {@code diy-campus:notifications-read:v1}），
 * 换台电脑已读全丢，且两个人看到的是同一份假数据。
 */
@RestController
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/notifications")
    public ApiResponse.Envelope<Map<String, Object>> list() {
        List<Dtos.NotificationDto> items = notificationService.list();
        long unread = items.stream().filter(n -> !n.read()).count();
        return ApiResponse.ok(Map.of("items", items, "unread", unread));
    }

    @PostMapping("/notifications/{id}/read")
    public ApiResponse.Envelope<Map<String, Object>> markRead(@PathVariable String id) {
        notificationService.markRead(id);
        return ApiResponse.ok(Map.of("read", true));
    }

    @PostMapping("/notifications/read-all")
    public ApiResponse.Envelope<Map<String, Object>> markAllRead() {
        notificationService.markAllRead();
        return ApiResponse.ok(Map.of("read", true));
    }
}
