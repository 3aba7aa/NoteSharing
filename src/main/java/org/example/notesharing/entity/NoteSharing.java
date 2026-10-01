package org.example.notesharing.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.notesharing.enums.NotePermission;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteSharing {
    @Id
    private Long id;

    @ManyToOne
    private User user;

    @ManyToOne
    private Note note;

    private NotePermission permission;
}
