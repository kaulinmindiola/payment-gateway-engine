package com.paymentgateway.engine.application.usecase;

public record CachedProblemPayload(String type, String title, int status, String detail) {}