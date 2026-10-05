package org.example.notesharing.dto.common;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Uniform envelope for paginated collections.
 *
 * @param page zero-based page index, matching the request's {@code page} parameter
 */
public record PageResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
            page.getContent(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages());
    }

    public static <T, R> PageResponse<R> from(Page<T> page, java.util.function.Function<T, R> mapper) {
        return new PageResponse<>(
            page.getContent().stream().map(mapper).toList(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages());
    }
}
