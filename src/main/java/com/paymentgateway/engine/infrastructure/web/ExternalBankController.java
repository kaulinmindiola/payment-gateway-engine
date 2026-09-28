package com.paymentgateway.engine.infrastructure.web;

import com.paymentgateway.engine.application.usecase.GetExternalBank;
import com.paymentgateway.engine.application.usecase.GetExternalBanks;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "External banks")
@RestController
@RequestMapping("/api/v1/external-banks")
public class ExternalBankController {

    private final GetExternalBank getExternalBank;
    private final GetExternalBanks getExternalBanks;

    public ExternalBankController(GetExternalBank getExternalBank, GetExternalBanks getExternalBanks) {
        this.getExternalBank = getExternalBank;
        this.getExternalBanks = getExternalBanks;
    }

    @Operation(summary = "List all external banks", description = "Public reference catalog, no authentication required")
    @ApiResponse(responseCode = "200", description = "List of external banks")
    @GetMapping
    public List<ExternalBankResponse> getAll() {
        return getExternalBanks.execute().stream().map(ExternalBankResponse::from).toList();
    }

    @Operation(summary = "Get external bank details", description = "Public reference catalog, no authentication required")
    @ApiResponse(responseCode = "200", description = "External bank details")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    @GetMapping("/{id}")
    public ResponseEntity<ExternalBankResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ExternalBankResponse.from(getExternalBank.execute(id)));
    }
}