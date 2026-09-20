package com.paymentgateway.engine.infrastructure.config;

import com.paymentgateway.engine.domain.policy.LockOrderPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LockOrderPolicy es una clase de dominio PURA (Fase 5, sin @Component --
 * ver nota en el propio archivo), consistente con CS-03. Este @Bean es
 * el único punto donde infrastructure/ decide cómo instanciarla para que
 * Spring pueda inyectarla en InternalTransferHandler -- domain/ nunca
 * sabe que Spring existe.
 */
@Configuration
public class DomainPolicyConfig {

    @Bean
    public LockOrderPolicy lockOrderPolicy() {
        return new LockOrderPolicy();
    }
}