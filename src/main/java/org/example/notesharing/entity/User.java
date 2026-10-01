package org.example.notesharing.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.notesharing.enums.Gender;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "User")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String Username;
    private String HashedPassword; //using Bcrypt
    @Column(unique = true,  nullable = false, name = "Email")
    private String Email;
    private LocalDate BOD;
    private LocalDateTime CreatedAt;
    @Enumerated(EnumType.STRING)
    private Gender gender;
    //private PhoneNumber phone;
    @Lob
    @Column(columnDefinition = "BLOB")
    private byte[] imageData;

    @OneToOne
    private UserSettings userSettings;

    @OneToMany
    private List<Notification> notifications;

    @OneToMany
    private List<NoteSharing> notesSharing;

    @OneToMany
    private List<TwoFactor> twoFactors;

    @OneToMany
    private List<UserRelationship> userRelationships;

    @OneToMany
    private List<Note> notes;


}
