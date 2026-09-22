package com.paymentgateway.engine.infrastructure.web;

import com.paymentgateway.engine.application.usecase.GetExternalBank;
import com.paymentgateway.engine.application.usecase.GetExternalBanks;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// Sin X-User-Id: catálogo público de solo lectura, sin ownership
// (Sección 6 del contexto: "ninguna" auth -- excepción explícita a CON-008,
// documentada en ADR-0010).
@RestController
@RequestMapping("/api/v1/external-banks")
public class ExternalBankController {

    private final GetExternalBank getExternalBank;
    private final GetExternalBanks getExternalBanks;

    public ExternalBankController(GetExternalBank getExternalBank, GetExternalBanks getExternalBanks) {
        this.getExternalBank = getExternalBank;
        this.getExternalBanks = getExternalBanks;
    }

    @GetMapping
    public List<ExternalBankResponse> getAll() {
        return getExternalBanks.execute().stream().map(ExternalBankResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExternalBankResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ExternalBankResponse.from(getExternalBank.execute(id)));
    }
}