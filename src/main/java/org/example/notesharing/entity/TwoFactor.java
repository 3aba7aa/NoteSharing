package org.example.notesharing.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TwoFactor {

    @Id
    private Long id;

    @ManyToOne
    private User user;

    private String key;
    private Integer time;
    private Boolean used;
}
