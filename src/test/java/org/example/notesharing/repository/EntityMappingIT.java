package org.example.notesharing.repository;

import org.example.notesharing.config.FlywayTestConfig;
import org.example.notesharing.entity.Note;
import org.example.notesharing.entity.NoteSharing;
import org.example.notesharing.entity.User;
import org.example.notesharing.entity.UserSettings;
import org.example.notesharing.enums.Gender;
import org.example.notesharing.enums.NotePermission;
import org.example.notesharing.enums.NoteType;
import org.example.notesharing.enums.NoteVisibility;
import org.example.notesharing.enums.Theme;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the entity mappings survive a real PostgreSQL engine.
 *
 * <p>Every assertion here fails against the pre-Task-2 entities for a reason that
 * H2 would have let through: the table name {@code User} is a reserved word,
 * {@code columnDefinition = "BLOB"} is MySQL-only syntax, {@code NoteSharing} had no
 * id generator, and three enums persisted as ordinals.
 */
@SpringBootTest
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(FlywayTestConfig.class)
@Testcontainers
class EntityMappingIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired UserRepository users;
    @Autowired UserSettingsRepository settings;
    @Autowired NoteRepository notes;
    @Autowired NoteSharingRepository sharings;

    private User newUser(String username, String email) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(email);
        u.setHashedPassword("hash");
        u.setGender(Gender.NONE);
        u.setCreatedAt(LocalDateTime.now());
        return u;
    }

    @Test
    void userRoundTripsWithImageData() {
        User saved = users.saveAndFlush(newUser("bob", "image@x.com"));
        saved.setImageData(new byte[]{1, 2, 3, 4});
        users.saveAndFlush(saved);

        User found = users.findById(saved.getId()).orElseThrow();
        assertThat(found.getImageData()).containsExactly(1, 2, 3, 4);
        assertThat(found.getEmail()).isEqualTo("image@x.com");
    }

    @Test
    void emailIsUnique() {
        users.saveAndFlush(newUser("unique", "unique@x.com"));
        assertThatThrownBy(() -> users.saveAndFlush(newUser("unique2", "unique@x.com")))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void noteOwnershipLivesOnTheAssociationNotADuplicateColumn() {
        User owner = users.saveAndFlush(newUser("note-owner", "note-owner@x.com"));

        Note note = new Note();
        note.setTitle("t");
        note.setContent("c");
        note.setCreatedAt(LocalDateTime.now());
        note.setModifiedAt(LocalDateTime.now());
        note.setType(NoteType.GENERAL);
        note.setVisibility(NoteVisibility.PRIVATE);
        note.setUser(owner);
        Note savedNote = notes.saveAndFlush(note);

        Note found = notes.findById(savedNote.getId()).orElseThrow();
        assertThat(found.getUser().getId()).isEqualTo(owner.getId());
    }

    @Test
    void noteSharingGeneratesItsOwnId() {
        User owner = users.saveAndFlush(newUser("sharing-owner", "sharing-owner@x.com"));
        Note note = notes.saveAndFlush(noteFor(owner));

        NoteSharing ns = new NoteSharing();
        ns.setUser(owner);
        ns.setNote(note);
        ns.setPermission(NotePermission.VIEW);

        assertThat(sharings.saveAndFlush(ns).getId()).isNotNull();
    }

    @Test
    void aNoteCanBeSharedWithAUserOnlyOnce() {
        User owner = users.saveAndFlush(newUser("dup-owner", "dup-owner@x.com"));
        Note note = notes.saveAndFlush(noteFor(owner));

        NoteSharing first = new NoteSharing();
        first.setUser(owner);
        first.setNote(note);
        first.setPermission(NotePermission.VIEW);
        sharings.saveAndFlush(first);

        NoteSharing second = new NoteSharing();
        second.setUser(owner);
        second.setNote(note);
        second.setPermission(NotePermission.EDIT);
        assertThatThrownBy(() -> sharings.saveAndFlush(second))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void enumsPersistByNameNotOrdinal() {
        User u = users.saveAndFlush(newUser("themebob", "themebob@x.com"));

        UserSettings s = new UserSettings();
        s.setUser(u);
        s.setTheme(Theme.DARK);
        s.setTwoFactorEnabled(false);
        s.setNotificationEnabled(true);
        settings.saveAndFlush(s);

        UserSettings found = settings.findById(u.getId()).orElseThrow();
        assertThat(found.getTheme()).isEqualTo(Theme.DARK);
    }

    @Test
    void userHasAtMostOneSettingsRow() {
        User u = users.saveAndFlush(newUser("settingsbob", "settingsbob@x.com"));

        UserSettings first = new UserSettings();
        first.setUser(u);
        first.setTheme(Theme.LIGHT);
        settings.saveAndFlush(first);

        UserSettings second = new UserSettings();
        second.setUser(u);
        second.setTheme(Theme.DARK);
        assertThatThrownBy(() -> settings.saveAndFlush(second))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Note noteFor(User owner) {
        Note note = new Note();
        note.setTitle("t");
        note.setContent("c");
        note.setCreatedAt(LocalDateTime.now());
        note.setModifiedAt(LocalDateTime.now());
        note.setType(NoteType.GENERAL);
        note.setVisibility(NoteVisibility.PRIVATE);
        note.setUser(owner);
        return note;
    }
}
