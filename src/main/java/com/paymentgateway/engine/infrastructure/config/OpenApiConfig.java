package com.paymentgateway.engine.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configuration
@ConditionalOnWebApplication
public class OpenApiConfig {

    private static final String PROBLEM_JSON = "application/problem+json";
    private static final String TYPE_BASE = "https://payment-gateway-engine/errors/";
    private static final String DEMO_ACCOUNT = "/api/v1/accounts/21cd6102-7cfe-4c51-8a21-92ecf9e1b6af";

    @Bean
    public OpenAPI paymentGatewayOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Payment Gateway Engine API")
                        .version("v1")
                        .description("Internal and external transfers with pessimistic locking, dual idempotency "
                                + "and a resilient provider integration. Every error follows RFC 7807 "
                                + "(application/problem+json) and includes a traceId that matches the "
                                + "X-Trace-Id response header and the server logs."))
                // Fixed server: keeps the generated spec stable across runs (sync test).
                .servers(List.of(new Server().url("http://localhost:8080").description("Local Docker Compose")))
                .components(new Components()
                        .addSchemas("ProblemDetail", problemDetailSchema())
                        .addResponses("BadRequest", problem("Invalid request (format, missing header, validation)",
                                400, "Bad Request", "missing-required-header",
                                "Required header 'X-User-Id' is missing.", DEMO_ACCOUNT))
                        .addResponses("Forbidden", problem("The requester does not own the resource",
                                403, "Forbidden", "ownership-violation",
                                "Requester does not own resource: 21cd6102-7cfe-4c51-8a21-92ecf9e1b6af", DEMO_ACCOUNT))
                        .addResponses("NotFound", problem("Resource not found",
                                404, "Not Found", "account-not-found",
                                "Account not found: 00000000-0000-0000-0000-000000000000",
                                "/api/v1/accounts/00000000-0000-0000-0000-000000000000"))
                        .addResponses("Conflict", problem("A request with the same X-Idempotency-Key is in progress",
                                409, "Conflict", "idempotency-conflict",
                                "A transfer with idempotency key 'example-409' is already in progress",
                                "/api/v1/payments/transfer"))
                        .addResponses("UnprocessableEntity", problem("Business rule violated",
                                422, "Unprocessable Entity", "business-rule-violation",
                                "Source and target account must not be the same: 21cd6102-7cfe-4c51-8a21-92ecf9e1b6af",
                                "/api/v1/payments/transfer"))
                        .addResponses("ServiceUnavailable", problem("External authorization provider unavailable after retries",
                                503, "Service Unavailable", "authorization-provider-unavailable",
                                "External authorization provider is temporarily unavailable. The transfer was not "
                                        + "executed and can be safely retried with the same X-Idempotency-Key.",
                                "/api/v1/payments/transfer")));
    }

    private static Schema<?> problemDetailSchema() {
        return new ObjectSchema()
                .description("RFC 7807 problem detail")
                .addProperty("type", new StringSchema().format("uri"))
                .addProperty("title", new StringSchema())
                .addProperty("status", new IntegerSchema())
                .addProperty("detail", new StringSchema())
                .addProperty("instance", new StringSchema().format("uri"))
                .addProperty("traceId", new StringSchema()
                        .description("Same value as the X-Trace-Id response header and the request logs"));
    }

    private static ApiResponse problem(String description, int status, String title,
                                        String typeSlug, String detail, String instance) {
        Map<String, Object> example = new LinkedHashMap<>();
        example.put("type", TYPE_BASE + typeSlug);
        example.put("title", title);
        example.put("status", status);
        example.put("detail", detail);
        example.put("instance", instance);
        example.put("traceId", "example-" + status);

        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType(PROBLEM_JSON,
                        new io.swagger.v3.oas.models.media.MediaType()
                                .schema(new Schema<>().$ref("#/components/schemas/ProblemDetail"))
                                .example(example)));
    }
}