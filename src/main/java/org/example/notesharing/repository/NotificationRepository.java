package org.example.notesharing.repository;

import org.example.notesharing.entity.Notification;
import org.example.notesharing.enums.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Notifications addressed to one user, newest first.
     *
     * <p>{@code read} and {@code type} are nullable filters, so passing null means "no
     * filter". Both are typed as their Java types in the signature, which is what lets
     * Hibernate bind a null without having to guess a JDBC type.
     */
    @Query("""
        select n from Notification n
        where n.notified.id = :userId
          and (:read is null or n.read = :read)
          and (:type is null or n.type = :type)
        order by n.issueDate desc, n.id desc
        """)
    List<Notification> findForUser(@Param("userId") Long userId,
                                   @Param("read") Boolean read,
                                   @Param("type") NotificationType type);

    /**
     * Marks one notification read, scoped to its recipient.
     *
     * <p>Scoped in the WHERE clause rather than checked afterwards, so a notification
     * belonging to someone else simply matches nothing instead of being a 403 that confirms
     * the id exists.
     *
     * @return 1 when this call changed the row, 0 when it was already read or is not the
     *         caller's
     */
    @Modifying
    @Query("""
        update Notification n
        set n.read = true
        where n.id = :id and n.notified.id = :userId and n.read = false
        """)
    int markRead(@Param("id") Long id, @Param("userId") Long userId);

    /** Marks every unread notification for one user read. Returns how many changed. */
    @Modifying
    @Query("""
        update Notification n
        set n.read = true
        where n.notified.id = :userId and n.read = false
        """)
    int markAllRead(@Param("userId") Long userId);

    /** Unread count for the badge. */
    @Query("""
        select count(n) from Notification n
        where n.notified.id = :userId and n.read = false
        """)
    long countUnread(@Param("userId") Long userId);

    /**
     * Removes notifications that a completed action made pointless.
     *
     * <p>Deleting rather than marking read, because "accept this request" has no remaining
     * action once accepted: leaving the row would send the recipient to a 404.
     *
     * <p>Regardless of read state. Having read the notification does not make the thing it
     * referred to still exist, so a read row is just as stale as an unread one.
     *
     * @return how many rows were removed
     */
    @Modifying
    @Query("""
        delete from Notification n
        where n.notified.id = :userId
          and n.notifier.id = :notifierId
          and n.type = :type
        """)
    int dismiss(@Param("notifierId") Long notifierId,
                @Param("userId") Long userId,
                @Param("type") NotificationType type);
}
