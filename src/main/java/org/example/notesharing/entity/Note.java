package org.example.notesharing.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.notesharing.enums.NoteType;
import org.example.notesharing.enums.NoteVisibility;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "Note")
public class Note {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long ownerId;
    private String title;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    @Enumerated(EnumType.STRING)
    private NoteType type;
    @Enumerated(EnumType.STRING)
    private NoteVisibility visibility;
    private Integer notifyThreshold; //in minutes

    @ManyToOne
    private User user;

    @OneToMany
    private List<Notification> notifications;
}