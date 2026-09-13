/**
 * Casos de uso del Payment Gateway Engine.
 *
 * Orquesta los flujos de negocio (CreateAccount, GetAccount, TransferMoney,
 * GetTransaction) invocando exclusivamente los puertos definidos en domain/.
 * No conoce detalles de infraestructura (HTTP, JPA, Redis).
 */
package com.paymentgatewayengine.application;
