package org.example.notesharing.web;

import org.example.notesharing.exception.BusinessRuleException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

/**
 * Builds a safe {@link Pageable} from client-supplied paging and sorting.
 *
 * <p>Two things are checked. Page size and number are clamped, so one request cannot ask for
 * the whole table. Sort properties are checked against an allow-list, because an unknown
 * property otherwise reaches Hibernate as a query it cannot resolve and surfaces as a 500 --
 * a client typo reported as a server fault.
 */
public final class SafePageable {

    private SafePageable() {
    }

    public static final int MAX_PAGE_SIZE = 100;
    public static final int MAX_PAGE_NUMBER = 10_000;

    /**
     * @param allowedSortFields entity properties the caller may sort by; anything else is
     *                          rejected with a 400 naming the offending field
     */
    public static Pageable of(Pageable requested, Set<String> allowedSortFields) {
        int size = Math.min(Math.max(requested.getPageSize(), 1), MAX_PAGE_SIZE);
        int page = Math.min(Math.max(requested.getPageNumber(), 0), MAX_PAGE_NUMBER);

        Sort sort = requested.getSort().and(Sort.unsorted());
        for (Sort.Order order : sort) {
            if (!allowedSortFields.contains(order.getProperty())) {
                throw new BusinessRuleException(
                    "Cannot sort by '" + order.getProperty() + "'. Sortable fields: "
                        + String.join(", ", new java.util.TreeSet<>(allowedSortFields)) + ".");
            }
        }

        // Fall back to createdAt desc when the caller asked for no particular order, so
        // results are stable across pages instead of arriving in unspecified order.
        Sort effective = sort.isSorted() ? sort : Sort.by(Sort.Direction.DESC, "createdAt");
        return PageRequest.of(page, size, effective);
    }

    /** Fields on {@code Note} a client may sort by. */
    public static final Set<String> NOTE_SORT_FIELDS =
        Set.of("id", "title", "type", "visibility", "startDate", "endDate",
            "createdAt", "modifiedAt");

    /** Fields on {@code UserRelationship} a client may sort by. */
    public static final Set<String> SOCIAL_SORT_FIELDS =
        Set.of("id", "type", "createdAt");
}
