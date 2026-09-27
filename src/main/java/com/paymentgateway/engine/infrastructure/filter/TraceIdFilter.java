package com.paymentgateway.engine.infrastructure.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * ADR-0005: genera o respeta el traceId entrante (X-Trace-Id), lo expone en
 * el MDC durante toda la request y lo devuelve en el header de respuesta.
 * Máxima precedencia: debe envolver a todo lo demás para que cualquier log
 * de la request lo lleve.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    // El valor entrante es input NO confiable que acaba en los logs.
    // Solo se acepta si es seguro (sin saltos de línea -> sin log injection, longitud acotada).
    private static final Pattern SAFE_TRACE_ID = Pattern.compile("^[A-Za-z0-9-]{1,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = resolve(request.getHeader(TraceContext.HEADER));
        MDC.put(TraceContext.MDC_KEY, traceId);
        // Antes de la cadena: una vez comprometida la respuesta ya no se pueden añadir headers.
        response.setHeader(TraceContext.HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TraceContext.MDC_KEY);
        }
    }

    static String resolve(String incoming) {
        if (incoming != null && SAFE_TRACE_ID.matcher(incoming).matches()) {
            return incoming;
        }
        return UUID.randomUUID().toString();
    }
}