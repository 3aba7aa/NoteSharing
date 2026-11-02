package org.example.notesharing.repository;

import org.example.notesharing.entity.NoteSharing;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NoteSharingRepository extends JpaRepository<NoteSharing, Long> {

    List<NoteSharing> findByNoteId(Long noteId);

    Optional<NoteSharing> findByNoteIdAndUserId(Long noteId, Long userId);

    void deleteByNoteIdAndUserId(Long noteId, Long userId);
}
