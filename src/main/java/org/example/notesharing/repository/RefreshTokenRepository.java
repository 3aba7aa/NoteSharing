package org.example.notesharing.repository;

import org.example.notesharing.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * Takes a {@code String}, not a {@link java.util.UUID}: the column is varchar, and a UUID
     * parameter makes Hibernate fail to bind rather than convert.
     */
    Optional<RefreshToken> findByTokenId(String tokenId);

    @Modifying
    @Query("update RefreshToken r set r.revoked = true where r.user.id = :userId and r.revoked = false")
    int revokeAllForUser(@Param("userId") Long userId);
}
