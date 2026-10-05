package org.example.notesharing.repository;

import org.example.notesharing.entity.User;
import org.example.notesharing.entity.UserSettings;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    /**
     * The user's settings row, if they have one.
     *
     * <p>Registered as a query rather than derived from the {@code settings} association name
     * so the intent survives a rename of the field on {@code User}.
     */
    @Query("select s from UserSettings s where s.user.id = :userId")
    Optional<UserSettings> findSettingsById(@Param("userId") Long userId);

    boolean existsByEmailIgnoreCase(String email);

    Optional<User> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    @Query("select u from User u where lower(u.username) like lower(concat('%', :q, '%'))"
        + " or lower(u.email) like lower(concat('%', :q, '%'))")
    Page<User> search(@Param("q") String q, Pageable pageable);
}
