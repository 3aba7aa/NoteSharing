package org.example.notesharing.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.time.LocalDateTime;

/**
 * TOTP enrolment record.
 *
 * <p>The original entity carried {@code key}/{@code time}/{@code used}, which is the shape
 * of a one-time-password row. TOTP derives its code from a shared secret and the current
 * time step, so only the secret and the enrolment instant are stored.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "two_factor")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class TwoFactor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long id;

    @JsonIgnore
    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String secret;

    @Column(name = "enabled_at", nullable = false)
    private LocalDateTime enabledAt;
}
