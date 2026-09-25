package com.paymentgateway.engine.domain.port;

import java.util.List;
import java.util.function.Function;

/** Página de resultados agnóstica de framework (domain/ no conoce Spring Data). */
public record PageResult<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public PageResult {
        content = List.copyOf(content);
    }

    public <R> PageResult<R> map(Function<T, R> mapper) {
        return new PageResult<>(content.stream().map(mapper).toList(), page, size, totalElements, totalPages);
    }
}