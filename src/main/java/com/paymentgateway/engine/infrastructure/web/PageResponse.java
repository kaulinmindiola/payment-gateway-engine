package com.paymentgateway.engine.infrastructure.web;

import com.paymentgateway.engine.domain.port.PageResult;

import java.util.List;

/**
 * Envoltura de paginación de la API (Sección 6.1 del contexto). DTO web propio:
 * no se serializa PageResult (dominio) directamente, para no acoplar el
 * contrato HTTP a un tipo interno (mismo criterio que AccountResponse).
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> from(PageResult<T> result) {
        return new PageResponse<>(result.content(), result.page(), result.size(),
                result.totalElements(), result.totalPages());
    }
}