package org.example.notesharing.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.notesharing.enums.NotificationType;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "Notification")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String message;
    @Enumerated(EnumType.STRING)
    private NotificationType type;
    private Long noteId;
    private Long relationshipId;
    private LocalDateTime issueDate;
    private Boolean read;

    @ManyToOne
    private User notifier;

    @ManyToOne
    private User notified;

    @ManyToOne
    private Note note;

    @ManyToOne
    private UserRelationship userRelationship;
}
