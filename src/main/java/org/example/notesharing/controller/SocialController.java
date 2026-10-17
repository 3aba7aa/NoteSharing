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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The caller's own social graph: friends, blocks and pending requests.
 *
 * <p>Everything is read from the token, so none of these routes take a user id in the path.
 * Actions on another user live in {@link SocialActionController}, addressed by that user.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SocialController {

    private final SocialService socialService;

    @GetMapping("/users/me/friends")
    public ApiResponse<List<RelationshipResponseDto>> friends(
            @AuthenticationPrincipal JwtPrincipal principal) {
        return ApiResponse.of(socialService.listFriends(principal.userId()), "Friends");
    }

    @GetMapping("/users/me/blocked")
    public ApiResponse<List<RelationshipResponseDto>> blocked(
            @AuthenticationPrincipal JwtPrincipal principal) {
        return ApiResponse.of(socialService.listBlocked(principal.userId()), "Blocked users");
    }

    /** Requests waiting for the caller's answer. */
    @GetMapping("/friend-requests")
    public ApiResponse<List<RelationshipResponseDto>> incoming(
            @AuthenticationPrincipal JwtPrincipal principal) {
        return ApiResponse.of(
            socialService.listIncomingRequests(principal.userId()),
            "Incoming friend requests");
    }

    /** Requests the caller sent that nobody has answered. */
    @GetMapping("/friend-requests/sent")
    public ApiResponse<List<RelationshipResponseDto>> outgoing(
            @AuthenticationPrincipal JwtPrincipal principal) {
        return ApiResponse.of(
            socialService.listOutgoingRequests(principal.userId()),
            "Sent friend requests");
    }

    /** Removes a friend. Distinct from blocking, which is also a DELETE but one-way. */
    @DeleteMapping("/users/me/friends/{userId}")
    public ResponseEntity<Void> unfriend(@PathVariable Long userId,
                                         @AuthenticationPrincipal JwtPrincipal principal) {
        socialService.unfriend(principal.userId(), userId);
        return ResponseEntity.noContent().build();
    }

    /** Lifts a block. */
    @DeleteMapping("/users/me/blocked/{userId}")
    public ResponseEntity<Void> unblock(@PathVariable Long userId,
                                        @AuthenticationPrincipal JwtPrincipal principal) {
        socialService.unblock(principal.userId(), userId);
        return ResponseEntity.noContent().build();
    }
}
