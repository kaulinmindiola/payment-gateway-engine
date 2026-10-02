package com.paymentgateway.engine.infrastructure.web;

import com.paymentgateway.engine.application.usecase.GetCurrentUser;
import com.paymentgateway.engine.domain.exception.UserNotFoundException;
import com.paymentgateway.engine.domain.model.User;
import com.paymentgateway.engine.domain.model.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.endsWith;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private GetCurrentUser getCurrentUser;

    @Test
    void me_returnsCallerIdentity() throws Exception {
        UUID id = UUID.randomUUID();
        given(getCurrentUser.execute(id))
                .willReturn(User.reconstitute(id, "alice@example.com", "Alice", UserStatus.ACTIVE));

        mockMvc.perform(get("/api/v1/users/me").header("X-User-Id", id.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Alice"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void me_forUnknownUser_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        given(getCurrentUser.execute(id)).willThrow(new UserNotFoundException(id.toString()));

        mockMvc.perform(get("/api/v1/users/me").header("X-User-Id", id.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(endsWith("/errors/user-not-found")));
    }

    @Test
    void me_withoutHeader_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("/errors/missing-required-header")));
    }
}