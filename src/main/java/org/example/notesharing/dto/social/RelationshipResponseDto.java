package org.example.notesharing.dto.social;

import org.example.notesharing.dto.user.UserSummaryDto;
import org.example.notesharing.enums.RelationshipType;

import java.time.LocalDateTime;

/**
 * One relationship row as seen by one user.
 *
 * @param user        the other party, not the viewer
 * @param direction   {@code OUTGOING} when the viewer is {@code firstUser}, {@code INCOMING}
 *                    when the viewer is {@code secondUser}
 */
public record RelationshipResponseDto(
    Long id,
    UserSummaryDto user,
    RelationshipType type,
    Direction direction,
    LocalDateTime createdAt) {

    public enum Direction {
        OUTGOING,
        INCOMING
    }
}
