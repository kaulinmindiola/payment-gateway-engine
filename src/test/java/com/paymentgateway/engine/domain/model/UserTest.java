package com.paymentgateway.engine.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    @Test
    void reconstitute_withActiveStatus_isActiveReturnsTrue() {
        User user = User.reconstitute(UUID.randomUUID(), "a@b.com", "Alice", UserStatus.ACTIVE);
        assertThat(user.isActive()).isTrue();
    }

    @Test
    void reconstitute_withInactiveStatus_isActiveReturnsFalse() {
        User user = User.reconstitute(UUID.randomUUID(), "a@b.com", "Alice", UserStatus.INACTIVE);
        assertThat(user.isActive()).isFalse();
    }

    @Test
    void reconstitute_withNullId_throws() {
        assertThatThrownBy(() ->
                User.reconstitute(null, "a@b.com", "Alice", UserStatus.ACTIVE))
                .isInstanceOf(NullPointerException.class);
    }
}