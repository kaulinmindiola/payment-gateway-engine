package com.paymentgateway.engine.application.usecase;

import com.paymentgateway.engine.application.fake.FakeUserRepositoryPort;
import com.paymentgateway.engine.domain.exception.UserNotFoundException;
import com.paymentgateway.engine.domain.model.User;
import com.paymentgateway.engine.domain.model.UserStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GetCurrentUserTest {

    private final FakeUserRepositoryPort users = new FakeUserRepositoryPort();
    private final GetCurrentUser getCurrentUser = new GetCurrentUser(users);

    @Test
    void execute_forExistingUser_returnsIdentity() {
        UUID id = UUID.randomUUID();
        users.seed(User.reconstitute(id, "alice@example.com", "Alice", UserStatus.ACTIVE));

        User user = getCurrentUser.execute(id);

        assertThat(user.getName()).isEqualTo("Alice");
        assertThat(user.getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void execute_forUnknownUser_throwsUserNotFound() {
        assertThatThrownBy(() -> getCurrentUser.execute(UUID.randomUUID()))
                .isInstanceOf(UserNotFoundException.class);
    }
}