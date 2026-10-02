package com.paymentgateway.engine.infrastructure.web;

import com.paymentgateway.engine.application.usecase.GetCurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@SecurityRequirement(name = "userId")
@Tag(name = "Users")
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final GetCurrentUser getCurrentUser;

    public UserController(GetCurrentUser getCurrentUser) {
        this.getCurrentUser = getCurrentUser;
    }

    @Operation(summary = "Who am I",
            description = "Returns the identity behind X-User-Id (read-only). Users are seeded, not managed through the API.")
    @ApiResponse(responseCode = "200", description = "The caller's identity")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    @GetMapping("/me")
    public UserResponse me(@Parameter(hidden = true) @RequestHeader("X-User-Id") UUID userId) {
        return UserResponse.from(getCurrentUser.execute(userId));
    }
}