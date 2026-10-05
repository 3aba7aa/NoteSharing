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

import java.time.LocalDateTime;

/**
 * Note and relationship ids are carried as association columns only.
 *
 * <p>The original entity declared both {@code noteId}/{@code relationshipId} scalar fields
 * and {@code note}/{@code userRelationship} associations. Hibernate maps both pairs onto the
 * same physical column and refuses the mapping outright, so the scalars are gone.
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

    @ManyToOne
    @JoinColumn(name = "note_id")
    private Note note;

    @ManyToOne
    @JoinColumn(name = "user_relationship_id")
    private UserRelationship userRelationship;
}
