package org.example.notesharing.enums;

/**
 * Relationship kinds, all stored as directed rows: {@code firstUser} acts on {@code secondUser}.
 *
 * <p>Direction lives in the columns, not in the enum. An earlier version carried suffixes like
 * {@code BLOCKED12} and {@code FRIENDREQ21} to mean "first blocked second" and so on, which
 * doubled every constant and had to be kept in step with the schema by hand.
 *
 * <p>{@link #FRIEND} is the one symmetric case. It is still written as a row in each
 * direction, so "my friends" is a single indexed lookup and the uniqueness constraint prevents
 * duplicates without application code having to canonicalise the pair.
 */
public enum RelationshipType {
    /** firstUser has asked secondUser to be friends; awaiting secondUser's answer. */
    FRIEND_REQUEST,

    /** Mutual friendship. Written as a row in both directions. */
    FRIEND,

    /** firstUser has blocked secondUser. One-way: secondUser cannot see this row's effect. */
    BLOCKED
}
