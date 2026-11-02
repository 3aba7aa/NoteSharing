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
 * Server-side refresh token record.
 *
 * <p>A stateless access token cannot be revoked. Refresh tokens can, so each one has a row:
 * {@code logout} and {@code refresh} both mark rows revoked, which is what makes token
 * rotation and replay detection real rather than notional.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "refresh_token")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long id;

    @JsonIgnore
    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    /**
     * The {@code jti} claim of the issued JWT. Storing the id rather than the token
     * itself keeps credentials out of the database.
     */
    @Column(name = "token_id", nullable = false, unique = true)
    private String tokenId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private Boolean revoked;
}
