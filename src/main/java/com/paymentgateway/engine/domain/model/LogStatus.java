package com.paymentgateway.engine.domain.model;

public enum LogStatus {
    PENDING,
    COMPLETED,
    FAILED,
    REVERSED // reservado — ningún caso de uso de este alcance lo dispara (Sección 4 del contexto)
}