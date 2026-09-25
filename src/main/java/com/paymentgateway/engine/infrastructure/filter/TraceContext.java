package com.paymentgateway.engine.infrastructure.filter;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * Acceso centralizado al traceId de la request actual (ADR-0005).
 * Funciona porque todo el procesamiento de una request es SÍNCRONO en el
 * mismo hilo (RestClient y reintentos de Resilience4j incluidos). Si algún
 * día se introduce código asíncrono, el MDC no se propaga solo.
 */
public final class TraceContext {

    public static final String HEADER = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";

    private TraceContext() {
    }

    /** traceId de la request actual, o null si no hay request HTTP en curso. */
    public static String current() {
        return MDC.get(MDC_KEY);
    }

    /** traceId actual o, fuera de una request HTTP (p.ej. tests directos), uno nuevo. */
    public static String currentOrNew() {
        String current = current();
        return current != null ? current : UUID.randomUUID().toString();
    }
}