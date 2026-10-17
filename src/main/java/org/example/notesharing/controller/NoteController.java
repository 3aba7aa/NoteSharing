package org.example.notesharing.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.notesharing.dto.common.ApiResponse;
import org.example.notesharing.dto.note.CreateNoteRequestDto;
import org.example.notesharing.dto.note.NoteResponseDto;
import org.example.notesharing.dto.note.NoteShareResponseDto;
import org.example.notesharing.dto.note.NoteSummaryDto;
import org.example.notesharing.dto.note.ShareNoteRequestDto;
import org.example.notesharing.dto.note.UpdateNoteRequestDto;
import org.example.notesharing.dto.note.UpdateShareRequestDto;
import org.example.notesharing.enums.NoteType;
import org.example.notesharing.enums.NoteVisibility;
import org.example.notesharing.security.JwtPrincipal;
import org.example.notesharing.service.NoteService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Notes. All routes require a bearer token; the note's own access rules are applied per note
 * in {@link NoteService} rather than here.
 */
@RestController
@RequestMapping("/api/v1/notes")
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;

    /** The caller's own notes, newest first. */
    @GetMapping
    public ApiResponse<List<NoteSummaryDto>> listMine(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam(required = false) NoteType type,
            @RequestParam(required = false) NoteVisibility visibility,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        return ApiResponse.of(
            noteService.listOwned(principal.userId(), type, visibility, from, to),
            "Your notes");
    }

    /** Notes other users have shared with the caller, newest first. */
    @GetMapping("/shared")
    public ApiResponse<List<NoteSummaryDto>> listShared(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam(required = false) NoteType type,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        return ApiResponse.of(
            noteService.listSharedWithMe(principal.userId(), type, from, to),
            "Notes shared with you");
    }

    /** Public notes by other users, newest first. */
    @GetMapping("/public")
    public ApiResponse<List<NoteSummaryDto>> listPublic(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam(required = false) NoteType type,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        return ApiResponse.of(
            noteService.listPublic(principal.userId(), type, from, to),
            "Public notes");
    }

    @PostMapping
    public ResponseEntity<ApiResponse<NoteResponseDto>> create(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody CreateNoteRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.of(noteService.create(principal.userId(), request), "Note created"));
    }

    @GetMapping("/{id}")
    public ApiResponse<NoteResponseDto> get(@PathVariable Long id,
                                            @AuthenticationPrincipal JwtPrincipal principal) {
        return ApiResponse.of(noteService.getById(id, principal.userId()), "Note");
    }

    @PutMapping("/{id}")
    public ApiResponse<NoteResponseDto> update(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody UpdateNoteRequestDto request) {
        return ApiResponse.of(
            noteService.update(id, principal.userId(), request), "Note updated");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       @AuthenticationPrincipal JwtPrincipal principal) {
        noteService.delete(id, principal.userId());
        return ResponseEntity.noContent().build();
    }

    /** Grants or creates another user's permission on this note. */
    @PostMapping("/{id}/shares")
    public ResponseEntity<ApiResponse<NoteShareResponseDto>> share(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody ShareNoteRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.of(noteService.share(id, principal.userId(), request), "Shared"));
    }

    /** Revokes a recipient's access. Plural to match the other share sub-resource. */
    @DeleteMapping("/{id}/shares/{userId}")
    public ResponseEntity<Void> unshare(@PathVariable Long id,
                                        @PathVariable Long userId,
                                        @AuthenticationPrincipal JwtPrincipal principal) {
        noteService.unshare(id, principal.userId(), userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/shares")
    public ApiResponse<List<NoteShareResponseDto>> listShares(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtPrincipal principal) {
        return ApiResponse.of(noteService.listShares(id, principal.userId()), "Note shares");
    }

    /** Changes one recipient's permission on an existing share. */
    @PutMapping("/{id}/shares/{userId}")
    public ApiResponse<NoteShareResponseDto> updateShare(
            @PathVariable Long id,
            @PathVariable Long userId,
            @AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody UpdateShareRequestDto request) {
        return ApiResponse.of(
            noteService.updateSharePermission(id, principal.userId(), userId, request),
            "Share updated");
    }
}
