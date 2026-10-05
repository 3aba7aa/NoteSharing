package org.example.notesharing.service;

import lombok.RequiredArgsConstructor;
import org.example.notesharing.dto.common.PageResponse;
import org.example.notesharing.dto.social.RelationshipResponseDto;
import org.example.notesharing.dto.social.RelationshipResponseDto.Direction;
import org.example.notesharing.dto.user.UserSummaryDto;
import org.example.notesharing.entity.User;
import org.example.notesharing.entity.UserRelationship;
import org.example.notesharing.enums.RelationshipType;
import org.example.notesharing.exception.BusinessRuleException;
import org.example.notesharing.exception.ConflictException;
import org.example.notesharing.exception.ForbiddenException;
import org.example.notesharing.exception.ResourceNotFoundException;
import org.example.notesharing.repository.UserRelationshipRepository;
import org.example.notesharing.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Friends, friend requests and blocks.
 *
 * <p>All three are rows in {@code user_relationship}, pointed from the acting user to the
 * other. That makes each of them one indexed lookup rather than a scan, and it means blocking
 * is one-way by construction instead of by convention.
 */
@Service
@RequiredArgsConstructor
public class SocialService {

    private final UserRelationshipRepository relationships;
    private final UserRepository users;

    @Transactional
    public RelationshipResponseDto sendFriendRequest(Long fromUserId, Long toUserId) {
        User from = requireUser(fromUserId);
        User to = requireUser(toUserId);

        if (fromUserId.equals(toUserId)) {
            throw new BusinessRuleException("You cannot send a friend request to yourself.");
        }

        // Checked before the friendship lookup so a blocked user learns nothing about whether
        // they are friends with the person who blocked them.
        if (relationships.existsBlock(toUserId, fromUserId)) {
            throw new ForbiddenException("This user is not accepting friend requests.");
        }

        if (isFriend(fromUserId, toUserId)) {
            throw new ConflictException("Users", "already friends");
        }

        // If they already asked us, treat this as agreeing rather than as a duplicate: the
        // two requests merge into one friendship, which is what both people meant. The
        // returned row is the new friendship, not the request that was just consumed.
        Optional<UserRelationship> incoming = relationships
            .findByFirstUserIdAndSecondUserIdAndType(
                toUserId, fromUserId, RelationshipType.FRIEND_REQUEST);
        if (incoming.isPresent()) {
            return acceptFriendRequest(fromUserId, incoming.get().getId());
        }

        Optional existing = relationships.findByFirstUserIdAndSecondUserIdAndType(
            fromUserId, toUserId, RelationshipType.FRIEND_REQUEST);
        if (existing.isPresent()) {
            throw new ConflictException("Friend request", "already sent");
        }

        UserRelationship request = new UserRelationship();
        request.setFirstUser(from);
        request.setSecondUser(to);
        request.setType(RelationshipType.FRIEND_REQUEST);
        request.setCreatedAt(LocalDateTime.now());

        return toResponse(relationships.save(request), Direction.OUTGOING);
    }

    /**
     * Accepts a request and writes the friendship in both directions.
     *
     * <p>The request row is removed rather than kept as FRIEND, so the inbox empties and the
     * pending state cannot be re-accepted later.
     */
    @Transactional
    public RelationshipResponseDto acceptFriendRequest(Long userId, Long requestId) {
        UserRelationship request = relationships.findById(requestId)
            .orElseThrow(() -> new ResourceNotFoundException("Friend request", requestId));

        // The addressee decides. Accepting someone else's request would be an idor.
        if (!request.getSecondUser().getId().equals(userId)) {
            throw new ForbiddenException("Only the recipient can accept this friend request.");
        }
        if (request.getType() != RelationshipType.FRIEND_REQUEST) {
            throw new BusinessRuleException("That is not a pending friend request.");
        }

        User first = request.getFirstUser();
        User second = request.getSecondUser();

        relationships.delete(request);
        relationships.flush();

        saveDirected(first, second, RelationshipType.FRIEND);
        UserRelationship reverse = saveDirected(second, first, RelationshipType.FRIEND);

        return toResponse(reverse, Direction.OUTGOING);
    }

    @Transactional
    public void rejectFriendRequest(Long userId, Long requestId) {
        UserRelationship request = relationships.findById(requestId)
            .orElseThrow(() -> new ResourceNotFoundException("Friend request", requestId));

        if (!request.getSecondUser().getId().equals(userId)) {
            throw new ForbiddenException("Only the recipient can reject this friend request.");
        }
        if (request.getType() != RelationshipType.FRIEND_REQUEST) {
            throw new BusinessRuleException("That is not a pending friend request.");
        }
        relationships.delete(request);
    }

    /** Withdraws a request by its row id. Only the sender may withdraw it. */
    @Transactional
    public void cancelFriendRequest(Long userId, Long requestId) {
        UserRelationship request = relationships.findById(requestId)
            .orElseThrow(() -> new ResourceNotFoundException("Friend request", requestId));
        if (!request.getFirstUser().getId().equals(userId)) {
            throw new ForbiddenException("Only the sender can withdraw this friend request.");
        }
        if (request.getType() != RelationshipType.FRIEND_REQUEST) {
            throw new BusinessRuleException("That is not a pending friend request.");
        }
        relationships.delete(request);
    }

    /** Withdraws a request addressed by recipient rather than by row id. */
    @Transactional
    public void cancelFriendRequestTo(Long userId, Long targetUserId) {
        UserRelationship request = relationships
            .findByFirstUserIdAndSecondUserIdAndType(
                userId, targetUserId, RelationshipType.FRIEND_REQUEST)
            .orElseThrow(() -> new ResourceNotFoundException("Friend request", targetUserId));
        relationships.delete(request);
    }

    /**
     * Blocks a user, ending any friendship and clearing any request between the two.
     *
     * <p>Blocking without that cleanup would leave a friendship row behind, so the blocked
     * user would still appear in the blocker's friends list and could still read shared notes.
     */
    @Transactional
    public RelationshipResponseDto block(Long userId, Long targetUserId) {
        User blocker = requireUser(userId);
        User blocked = requireUser(targetUserId);

        if (userId.equals(targetUserId)) {
            throw new BusinessRuleException("You cannot block yourself.");
        }

        relationships.findBetween(userId, targetUserId)
            .forEach(relationships::delete);
        relationships.flush();

        UserRelationship row = new UserRelationship();
        row.setFirstUser(blocker);
        row.setSecondUser(blocked);
        row.setType(RelationshipType.BLOCKED);
        row.setCreatedAt(LocalDateTime.now());

        return toResponse(relationships.save(row), Direction.OUTGOING);
    }

    @Transactional
    public void unblock(Long userId, Long targetUserId) {
        UserRelationship row = relationships
            .findByFirstUserIdAndSecondUserIdAndType(
                userId, targetUserId, RelationshipType.BLOCKED)
            .orElseThrow(() -> new ResourceNotFoundException("Block", targetUserId));
        relationships.delete(row);
    }

    @Transactional
    public void unfriend(Long userId, Long targetUserId) {
        // Both directions, or the pair would remain half-friends and the reverse row would
        // still show up in the other person's list.
        List<UserRelationship> rows = relationships.findBetween(userId, targetUserId).stream()
            .filter(r -> r.getType() == RelationshipType.FRIEND)
            .toList();
        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("Friendship", targetUserId);
        }
        rows.forEach(relationships::delete);
    }

    @Transactional(readOnly = true)
    public PageResponse<RelationshipResponseDto> listFriends(Long userId, Pageable pageable) {
        return page(relationships.findByFirstUserIdAndType(
            userId, RelationshipType.FRIEND, pageable), Direction.OUTGOING);
    }

    @Transactional(readOnly = true)
    public PageResponse<RelationshipResponseDto> listBlocked(Long userId, Pageable pageable) {
        return page(relationships.findByFirstUserIdAndType(
            userId, RelationshipType.BLOCKED, pageable), Direction.OUTGOING);
    }

    /** Requests waiting for the caller's answer. */
    @Transactional(readOnly = true)
    public PageResponse<RelationshipResponseDto> listIncomingRequests(Long userId,
                                                                      Pageable pageable) {
        return page(relationships.findBySecondUserIdAndType(
            userId, RelationshipType.FRIEND_REQUEST, pageable), Direction.INCOMING);
    }

    /** Requests the caller has sent and nobody has answered. */
    @Transactional(readOnly = true)
    public PageResponse<RelationshipResponseDto> listOutgoingRequests(Long userId,
                                                                      Pageable pageable) {
        return page(relationships.findByFirstUserIdAndType(
            userId, RelationshipType.FRIEND_REQUEST, pageable), Direction.OUTGOING);
    }

    private boolean isFriend(Long a, Long b) {
        return relationships
            .findByFirstUserIdAndSecondUserIdAndType(a, b, RelationshipType.FRIEND)
            .isPresent();
    }

    private UserRelationship saveDirected(User first, User second, RelationshipType type) {
        UserRelationship row = new UserRelationship();
        row.setFirstUser(first);
        row.setSecondUser(second);
        row.setType(type);
        row.setCreatedAt(LocalDateTime.now());
        return relationships.save(row);
    }

    private static PageResponse<RelationshipResponseDto> page(
            Page<UserRelationship> page, Direction direction) {
        return PageResponse.from(page, row -> toResponse(row, direction));
    }

    private static RelationshipResponseDto toResponse(UserRelationship row, Direction direction) {
        // For an outgoing row the other party is secondUser; for an incoming one it is
        // firstUser. Branching on direction is what keeps the caller from having to know
        // which side of the row they are standing on.
        User other = direction == Direction.OUTGOING ? row.getSecondUser() : row.getFirstUser();
        return new RelationshipResponseDto(
            row.getId(),
            new UserSummaryDto(other.getId(), other.getUsername()),
            row.getType(),
            direction,
            row.getCreatedAt());
    }

    private User requireUser(Long userId) {
        return users.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }
}
