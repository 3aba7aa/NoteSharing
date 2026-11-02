package org.example.notesharing.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.example.notesharing.enums.Theme;

/**
 * Owning side of the one-to-one with {@link User}: the primary key is the user's id,
 * so a user can have at most one settings row by construction.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "user_settings")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class UserSettings {

    @Id
    @Column(name = "user_id")
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long userId;

    @JsonIgnore
    @OneToOne
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    private Theme theme;

    @Column(name = "two_factor_enabled")
    private Boolean twoFactorEnabled;

    @Column(name = "notification_enabled")
    private Boolean notificationEnabled;
}
