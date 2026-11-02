package org.example.notesharing.controller;

import lombok.RequiredArgsConstructor;
import org.example.notesharing.dto.common.ApiResponse;
import org.example.notesharing.dto.social.RelationshipResponseDto;
import org.example.notesharing.security.JwtPrincipal;
import org.example.notesharing.service.SocialService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Actions taken on another user: sending them a request, answering a request, blocking them.
 *
 * <p>Every route here names the other user or the request. The acting user always comes from
 * the token, so a caller cannot act as someone else by editing the path.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SocialActionController {

    private final SocialService socialService;

    @PostMapping("/users/{id}/friend-request")
    public ResponseEntity<ApiResponse<RelationshipResponseDto>> sendFriendRequest(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.of(
                socialService.sendFriendRequest(principal.userId(), id),
                "Friend request sent"));
    }

    @PutMapping("/friend-requests/{id}/accept")
    public ApiResponse<RelationshipResponseDto> accept(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtPrincipal principal) {
        return ApiResponse.of(
            socialService.acceptFriendRequest(principal.userId(), id), "Friend request accepted");
    }

    @DeleteMapping("/friend-requests/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable Long id,
                                       @AuthenticationPrincipal JwtPrincipal principal) {
        socialService.rejectFriendRequest(principal.userId(), id);
        return ResponseEntity.noContent().build();
    }

    /** Withdraws a request the caller sent and nobody has answered. */
    @DeleteMapping("/friend-requests/sent/{id}")
    public ResponseEntity<Void> cancel(@PathVariable Long id,
                                       @AuthenticationPrincipal JwtPrincipal principal) {
        socialService.cancelFriendRequest(principal.userId(), id);
        return ResponseEntity.noContent().build();
    }

    /** Withdraws a request the caller sent, addressed by the recipient instead of the id. */
    @DeleteMapping("/users/{id}/friend-request")
    public ResponseEntity<Void> cancelByUser(@PathVariable Long id,
                                             @AuthenticationPrincipal JwtPrincipal principal) {
        socialService.cancelFriendRequestTo(principal.userId(), id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Blocks a user.
     *
     * <p>The spec lists this as {@code DELETE /users/{id}/block} described as "Block user",
     * which cannot be right: DELETE removes something, so the same route would have to both
     * create and destroy the block. PUT is the idempotent create here, and DELETE on the same
     * path lifts it, which keeps the spec's path.
     */
    @PutMapping("/users/{id}/block")
    public ApiResponse<RelationshipResponseDto> block(@PathVariable Long id,
                                                      @AuthenticationPrincipal JwtPrincipal principal) {
        return ApiResponse.of(socialService.block(principal.userId(), id), "User blocked");
    }

    /**
     * Lifts a block. Same path as {@link #block}, which is what makes the pair idempotent:
     * a repeated PUT stays blocked, a DELETE lifts it.
     */
    @DeleteMapping("/users/{id}/block")
    public ResponseEntity<Void> unblock(@PathVariable Long id,
                                        @AuthenticationPrincipal JwtPrincipal principal) {
        socialService.unblock(principal.userId(), id);
        return ResponseEntity.noContent().build();
    }
}
