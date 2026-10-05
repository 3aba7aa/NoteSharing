package org.example.notesharing.repository;

import org.example.notesharing.entity.UserRelationship;
import org.example.notesharing.enums.RelationshipType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRelationshipRepository
    extends JpaRepository<UserRelationship, Long> {

    /** The row for one directed relationship, if it exists. */
    Optional<UserRelationship> findByFirstUserIdAndSecondUserIdAndType(
        Long firstUserId, Long secondUserId, RelationshipType type);

    /** Everything pointing away from the given user: their friends, blocks, sent requests. */
    List<UserRelationship> findByFirstUserIdAndType(Long firstUserId, RelationshipType type);

    Page<UserRelationship> findByFirstUserIdAndType(
        Long firstUserId, RelationshipType type, Pageable pageable);

    /** Everything pointing at the given user: requests awaiting their answer. */
    List<UserRelationship> findBySecondUserIdAndType(Long secondUserId, RelationshipType type);

    Page<UserRelationship> findBySecondUserIdAndType(
        Long secondUserId, RelationshipType type, Pageable pageable);

    /**
     * True if the viewer has blocked the target. Deliberately one-directional: being blocked
     * by someone is not the same as blocking them, and the blocker's list must not leak into
     * the target's view.
     */
    @Query("""
        select count(r) > 0 from UserRelationship r
        where r.firstUser.id = :blockerId
          and r.secondUser.id = :blockedId
          and r.type = org.example.notesharing.enums.RelationshipType.BLOCKED
        """)
    boolean existsBlock(@Param("blockerId") Long blockerId, @Param("blockedId") Long blockedId);

    /**
     * Every relationship between the two users in either direction, for the cleanup that
     * blocking has to perform in one shot.
     */
    @Query("""
        select r from UserRelationship r
        where (r.firstUser.id = :userId and r.secondUser.id = :otherId)
           or (r.firstUser.id = :otherId and r.secondUser.id = :userId)
        """)
    List<UserRelationship> findBetween(@Param("userId") Long userId,
                                       @Param("otherId") Long otherId);

    /** Same as {@link #findBetween} but counted, so the caller can avoid deleting a no-op. */
    @Query("""
        select count(r) from UserRelationship r
        where (r.firstUser.id = :userId and r.secondUser.id = :otherId)
           or (r.firstUser.id = :otherId and r.secondUser.id = :userId)
        """)
    long countBetween(@Param("userId") Long userId, @Param("otherId") Long otherId);
}
