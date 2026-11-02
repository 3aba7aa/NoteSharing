package org.example.notesharing.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.example.notesharing.enums.NotificationType;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * A notification addressed to one user about something that happened.
 *
 * <p>The original entity declared both scalar {@code noteId}/{@code relationshipId} fields and
 * {@code note}/{@code userRelationship} associations. Hibernate maps both pairs onto the same
 * physical column and refuses the mapping outright, so the scalars are gone.
 *
 * <p>There is no reference to the friend request. Accepting or rejecting a request deletes
 * that row, so a foreign key to it would either block the delete or leave a dangling link;
 * the notifier and message carry the same information without the coupling.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "notification")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long id;

    private String message;

    @Enumerated(EnumType.STRING)
    private NotificationType type;

    @Column(name = "issue_date", nullable = false)
    private LocalDateTime issueDate;

    @Column(nullable = false)
    private Boolean read;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "notifier_id")
    private User notifier;

    @ManyToOne
    @JoinColumn(name = "notified_id")
    private User notified;

    /**
     * The note this is about, if any. Cleared by the database when the note is deleted: a
     * notification about a deleted note is still a true statement about the past, so the row
     * stays and only the reference goes.
     *
     * <p>{@code ON DELETE SET NULL} is declared here as well as in the migration. The dev
     * profile builds its schema from the entities rather than from the migrations, so without
     * it the foreign key would be strict there and deleting a shared note would fail with a
     * constraint violation that prod never sees.
     */
    @ManyToOne
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JoinColumn(name = "note_id")
    private Note note;
}
