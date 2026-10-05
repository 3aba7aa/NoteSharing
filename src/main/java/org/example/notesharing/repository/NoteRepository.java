package org.example.notesharing.repository;

import org.example.notesharing.entity.Note;
import org.example.notesharing.enums.NoteType;
import org.example.notesharing.enums.NoteVisibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface NoteRepository extends JpaRepository<Note, Long> {

    /**
     * Notes the caller owns, optionally narrowed by type, visibility and creation window.
     *
     * <p>Null-guarded predicates rather than a Specification: the filter set is fixed, so one
     * readable query beats dynamically assembled clauses. Each optional parameter is typed as
     * its enum in the method signature, which is what lets Hibernate bind a null without
     * having to guess a JDBC type.
     */
    @Query("""
        select n from Note n
        where n.user.id = :ownerId
          and (:type is null or n.type = :type)
          and (:visibility is null or n.visibility = :visibility)
          and (:from is null or n.createdAt >= :from)
          and (:to is null or n.createdAt <= :to)
        """)
    Page<Note> findOwned(@Param("ownerId") Long ownerId,
                         @Param("type") NoteType type,
                         @Param("visibility") NoteVisibility visibility,
                         @Param("from") LocalDateTime from,
                         @Param("to") LocalDateTime to,
                         Pageable pageable);

    /**
     * Notes shared with the caller. Shares are joined rather than fetched and filtered in
     * memory so paging and the total count stay correct.
     */
    @Query("""
        select n from Note n
        join NoteSharing s on s.note = n
        where s.user.id = :userId
          and (:type is null or n.type = :type)
          and (:from is null or n.createdAt >= :from)
          and (:to is null or n.createdAt <= :to)
        """)
    Page<Note> findSharedWithMe(@Param("userId") Long userId,
                                @Param("type") NoteType type,
                                @Param("from") LocalDateTime from,
                                @Param("to") LocalDateTime to,
                                Pageable pageable);

    /**
     * Public notes by other users, for a browse feed. Excludes the caller's own notes so the
     * feed does not repeat what the caller already sees as owned.
     */
    @Query("""
        select n from Note n
        where n.visibility = :visibility
          and n.user.id <> :userId
          and (:type is null or n.type = :type)
          and (:from is null or n.createdAt >= :from)
          and (:to is null or n.createdAt <= :to)
        """)
    Page<Note> findPublicByOthers(@Param("userId") Long userId,
                                  @Param("visibility") NoteVisibility visibility,
                                  @Param("type") NoteType type,
                                  @Param("from") LocalDateTime from,
                                  @Param("to") LocalDateTime to,
                                  Pageable pageable);

    Optional<Note> findByIdAndUserId(Long id, Long userId);
}
