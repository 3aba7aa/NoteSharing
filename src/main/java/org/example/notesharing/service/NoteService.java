package org.example.notesharing.service;

import lombok.RequiredArgsConstructor;
import org.example.notesharing.dto.note.CreateNoteRequestDto;
import org.example.notesharing.dto.note.NoteResponseDto;
import org.example.notesharing.dto.note.NoteShareResponseDto;
import org.example.notesharing.dto.note.NoteSummaryDto;
import org.example.notesharing.dto.note.ShareNoteRequestDto;
import org.example.notesharing.dto.note.UpdateNoteRequestDto;
import org.example.notesharing.dto.common.PageResponse;
import org.example.notesharing.dto.user.UserSummaryDto;
import org.example.notesharing.entity.Note;
import org.example.notesharing.entity.NoteSharing;
import org.example.notesharing.entity.User;
import org.example.notesharing.enums.NotePermission;
import org.example.notesharing.enums.NoteType;
import org.example.notesharing.enums.NoteVisibility;
import org.example.notesharing.exception.BusinessRuleException;
import org.example.notesharing.exception.ForbiddenException;
import org.example.notesharing.exception.ResourceNotFoundException;
import org.example.notesharing.repository.NoteRepository;
import org.example.notesharing.repository.NoteSharingRepository;
import org.example.notesharing.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Notes, and the access rules that decide who may touch them.
 *
 * <p>Access resolves in one place, {@link #permissionFor}, and every read and write goes
 * through it. Centralising it is what keeps "who can edit this" from being answered
 * differently by three endpoints.
 */
@Service
@RequiredArgsConstructor
public class NoteService {

    private final NoteRepository notes;
    private final NoteSharingRepository shares;
    private final UserRepository users;

    @Transactional
    public NoteResponseDto create(Long userId, CreateNoteRequestDto request) {
        validateDates(request.startDate(), request.endDate());

        Note note = new Note();
        note.setUser(requireUser(userId));
        note.setTitle(request.title());
        note.setContent(request.content());
        note.setType(request.type());
        note.setVisibility(request.visibility());
        note.setStartDate(request.startDate());
        note.setEndDate(request.endDate());
        note.setNotifyThreshold(request.notifyThreshold());
        note.setCreatedAt(LocalDateTime.now());

        return toResponse(notes.save(note), NotePermission.EDIT);
    }

    @Transactional(readOnly = true)
    public PageResponse<NoteSummaryDto> listOwned(Long userId, NoteType type, NoteVisibility visibility,
                                                 LocalDateTime from, LocalDateTime to,
                                                 Pageable pageable) {
        return PageResponse.from(
            notes.findOwned(userId, type, visibility, from, to, pageable),
            note -> toSummary(note, NotePermission.EDIT));
    }

    @Transactional(readOnly = true)
    public PageResponse<NoteSummaryDto> listSharedWithMe(Long userId, NoteType type,
                                                         LocalDateTime from, LocalDateTime to,
                                                         Pageable pageable) {
        Page<Note> page = notes.findSharedWithMe(userId, type, from, to, pageable);
        return PageResponse.from(page, note -> toSummary(note, permissionFor(note, userId)));
    }

    @Transactional(readOnly = true)
    public PageResponse<NoteSummaryDto> listPublic(Long userId, NoteType type,
                                                   LocalDateTime from, LocalDateTime to,
                                                   Pageable pageable) {
        return PageResponse.from(
            notes.findPublicByOthers(userId, NoteVisibility.PUBLIC, type, from, to, pageable),
            note -> toSummary(note, NotePermission.VIEW));
    }

    @Transactional(readOnly = true)
    public NoteResponseDto getById(Long noteId, Long userId) {
        Note note = notes.findById(noteId)
            .orElseThrow(() -> new ResourceNotFoundException("Note", noteId));

        // A caller with no access gets 404 rather than 403: confirming the note exists would
        // leak the private note's existence to someone not allowed to see it.
        if (permissionFor(note, userId) == null) {
            throw new ResourceNotFoundException("Note", noteId);
        }
        return toResponse(note, permissionFor(note, userId));
    }

    @Transactional
    public NoteResponseDto update(Long noteId, Long userId, UpdateNoteRequestDto request) {
        Note note = require(noteId);
        requirePermission(note, userId, NotePermission.EDIT);

        if (request.title() != null) {
            note.setTitle(request.title());
        }
        if (request.content() != null) {
            note.setContent(request.content());
        }
        if (request.type() != null) {
            note.setType(request.type());
        }
        if (request.visibility() != null) {
            note.setVisibility(request.visibility());
        }
        if (request.startDate() != null) {
            note.setStartDate(request.startDate());
        }
        if (request.endDate() != null) {
            note.setEndDate(request.endDate());
        }
        if (request.notifyThreshold() != null) {
            note.setNotifyThreshold(request.notifyThreshold());
        }

        // Validate the resulting pair, not just the incoming one: an update that moves only
        // startDate can otherwise leave the note ending before it starts.
        validateDates(note.getStartDate(), note.getEndDate());
        note.setModifiedAt(LocalDateTime.now());

        return toResponse(notes.save(note), NotePermission.EDIT);
    }

    @Transactional
    public void delete(Long noteId, Long userId) {
        Note note = require(noteId);
        requirePermission(note, userId, NotePermission.EDIT);

        // Only the owner may delete. An EDIT share grants editing of content, not destruction
        // of the note, so ownership is required here rather than merely edit permission.
        if (!note.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Only the note owner can delete it.");
        }

        shares.deleteAll(shares.findByNoteId(noteId));
        shares.flush();
        notes.delete(note);
    }

    @Transactional
    public NoteShareResponseDto share(Long noteId, Long userId, ShareNoteRequestDto request) {
        Note note = require(noteId);

        if (!note.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Only the note owner can share it.");
        }
        if (request.userId().equals(userId)) {
            throw new BusinessRuleException("You cannot share a note with yourself.");
        }

        User recipient = requireUser(request.userId());

        NoteSharing sharing = shares.findByNoteIdAndUserId(noteId, request.userId())
            .orElseGet(NoteSharing::new);
        sharing.setNote(note);
        sharing.setUser(recipient);
        sharing.setPermission(request.permission());

        return toShareResponse(shares.save(sharing));
    }

    @Transactional
    public void unshare(Long noteId, Long userId, Long targetUserId) {
        Note note = require(noteId);
        if (!note.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Only the note owner can change sharing.");
        }
        // deleteBy...Derived silently does nothing when absent, so confirm the share existed
        // rather than reporting success for a removal that never happened.
        if (shares.findByNoteIdAndUserId(noteId, targetUserId).isEmpty()) {
            throw new ResourceNotFoundException("Share", targetUserId);
        }
        shares.deleteByNoteIdAndUserId(noteId, targetUserId);
    }

    @Transactional(readOnly = true)
    public List<NoteShareResponseDto> listShares(Long noteId, Long userId) {
        Note note = require(noteId);
        // Recipients can see that a note is shared with them, but the full recipient list is
        // the owner's to see: it would otherwise disclose who else has access.
        if (!note.getUser().getId().equals(userId)
                && shares.findByNoteIdAndUserId(noteId, userId).isEmpty()) {
            throw new ForbiddenException("You do not have access to this note.");
        }
        return shares.findByNoteId(noteId).stream()
            .map(NoteService::toShareResponse)
            .toList();
    }

    /**
     * What the caller may do with this note, or null if nothing.
     *
     * <p>Owner outranks any share, so an owner's own EDIT share entry cannot downgrade them.
     */
    private NotePermission permissionFor(Note note, Long userId) {
        if (note.getUser().getId().equals(userId)) {
            return NotePermission.EDIT;
        }
        NotePermission shared = shares.findByNoteIdAndUserId(note.getId(), userId)
            .map(NoteSharing::getPermission)
            .orElse(null);
        if (shared != null) {
            return shared;
        }
        // Only PUBLIC is readable by anyone signed in. PRIVATE and LINK_ONLY are owner-and-
        // invitee only; LINK_ONLY would additionally need a token, which this API does not issue.
        return note.getVisibility() == NoteVisibility.PUBLIC ? NotePermission.VIEW : null;
    }

    private void requirePermission(Note note, Long userId, NotePermission required) {
        NotePermission actual = permissionFor(note, userId);
        if (actual == null) {
            throw new ResourceNotFoundException("Note", note.getId());
        }
        if (actual != required) {
            throw new ForbiddenException("You do not have permission to modify this note.");
        }
    }

    private static void validateDates(LocalDateTime start, LocalDateTime end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw new BusinessRuleException("endDate must not be before startDate.");
        }
    }

    private Note require(Long noteId) {
        return notes.findById(noteId)
            .orElseThrow(() -> new ResourceNotFoundException("Note", noteId));
    }

    private User requireUser(Long userId) {
        return users.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    private static NoteResponseDto toResponse(Note note, NotePermission permission) {
        return new NoteResponseDto(
            note.getId(),
            note.getTitle(),
            note.getContent(),
            note.getType(),
            note.getVisibility(),
            note.getStartDate(),
            note.getEndDate(),
            note.getNotifyThreshold(),
            note.getCreatedAt(),
            note.getModifiedAt(),
            new UserSummaryDto(note.getUser().getId(), note.getUser().getUsername()),
            permission == null ? null : permission.name());
    }

    private static NoteSummaryDto toSummary(Note note, NotePermission permission) {
        return new NoteSummaryDto(
            note.getId(),
            note.getTitle(),
            note.getType(),
            note.getVisibility(),
            note.getStartDate(),
            note.getEndDate(),
            note.getCreatedAt(),
            note.getModifiedAt(),
            new UserSummaryDto(note.getUser().getId(), note.getUser().getUsername()),
            permission == null ? null : permission.name());
    }

    private static NoteShareResponseDto toShareResponse(NoteSharing sharing) {
        return new NoteShareResponseDto(
            sharing.getId(),
            sharing.getNote().getId(),
            new UserSummaryDto(sharing.getUser().getId(), sharing.getUser().getUsername()),
            sharing.getPermission());
    }
}
