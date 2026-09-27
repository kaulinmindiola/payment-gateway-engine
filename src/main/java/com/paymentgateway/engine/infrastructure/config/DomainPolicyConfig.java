package com.paymentgateway.engine.infrastructure.config;

import com.paymentgateway.engine.domain.policy.LockOrderPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class DomainPolicyConfig {

    @Bean
    public LockOrderPolicy lockOrderPolicy() {
        return new LockOrderPolicy();
    }
}