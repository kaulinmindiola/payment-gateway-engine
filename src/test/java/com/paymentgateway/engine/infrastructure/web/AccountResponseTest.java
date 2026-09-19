package com.paymentgateway.engine.infrastructure.web;

import com.paymentgateway.engine.domain.model.Account;
import com.paymentgateway.engine.domain.model.AccountStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AccountResponseTest {

    @Test
    void from_mapsAllFieldsFromDomainAccount() {
        UUID ownerId = UUID.randomUUID();
        Account account = Account.createNew(ownerId, new BigDecimal("75.50"));

        AccountResponse response = AccountResponse.from(account);

        assertThat(response.id()).isEqualTo(account.getId());
        assertThat(response.ownerId()).isEqualTo(ownerId);
        assertThat(response.balance()).isEqualByComparingTo("75.50");
        assertThat(response.status()).isEqualTo(AccountStatus.ACTIVE);
    }
}