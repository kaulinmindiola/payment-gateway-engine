package com.paymentgateway.engine.application.usecase;

/** Espeja el shape de ProblemDetail SIN instance/traceId (Decisión 3, Fase 6). */
public record CachedProblemPayload(String type, String title, int status, String detail) {}