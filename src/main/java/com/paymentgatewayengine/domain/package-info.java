/**
 * Núcleo del dominio del Payment Gateway Engine.
 *
 * Contiene las entidades de negocio (Account, Transaction, TransactionLog),
 * sus invariantes, y los puertos (interfaces) que expresan las necesidades
 * del dominio hacia el exterior (persistencia, idempotencia, autorización externa).
 *
 * Regla arquitectónica (CON-002, verificada automáticamente desde la Fase 2
 * con ArchUnit — DEC-018): este paquete y sus subpaquetes NO deben depender
 * de ningún framework (Spring, JPA, etc.).
 */
package com.paymentgatewayengine.domain;
