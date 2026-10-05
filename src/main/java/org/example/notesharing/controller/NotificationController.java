package org.example.notesharing.controller;

import lombok.RequiredArgsConstructor;
import org.example.notesharing.dto.common.ApiResponse;
import org.example.notesharing.dto.common.PageResponse;
import org.example.notesharing.dto.notification.NotificationResponseDto;
import org.example.notesharing.enums.NotificationType;
import org.example.notesharing.security.JwtPrincipal;
import org.example.notesharing.service.NotificationService;
import org.example.notesharing.web.SafePageable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * The caller's own notifications. There is no route that takes a recipient id: a
 * notification is only ever read or marked by the user it was addressed to.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * @param read {@code true} for unread only, {@code false} for read only, absent for all
     */
    @GetMapping
    public ApiResponse<PageResponse<NotificationResponseDto>> list(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam(required = false) Boolean read,
            @RequestParam(required = false) NotificationType type,
            @PageableDefault(size = 20, sort = "issueDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiResponse.of(
            notificationService.list(principal.userId(), read, type, bounded(pageable)),
            "Notifications");
    }

    /** Unread count, for a badge. Separate from the list so the client need not page to it. */
    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> unreadCount(
            @AuthenticationPrincipal JwtPrincipal principal) {
        return ApiResponse.of(
            Map.of("unread", notificationService.countUnread(principal.userId())),
            "Unread count");
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long id,
                                         @AuthenticationPrincipal JwtPrincipal principal) {
        notificationService.markRead(id, principal.userId());
        return ResponseEntity.noContent().build();
    }

    /** Returns how many rows changed, so a client can tell a no-op from real progress. */
    @PutMapping("/read-all")
    public ApiResponse<Map<String, Integer>> markAllRead(
            @AuthenticationPrincipal JwtPrincipal principal) {
        int updated = notificationService.markAllRead(principal.userId());
        return ApiResponse.of(Map.of("markedRead", updated), "Marked all read");
    }

    private static Pageable bounded(Pageable pageable) {
        return SafePageable.of(pageable, SafePageable.NOTIFICATION_SORT_FIELDS);
    }
}
