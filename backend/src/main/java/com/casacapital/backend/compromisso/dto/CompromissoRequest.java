package com.casacapital.backend.compromisso.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record CompromissoRequest(
        @NotBlank(message = "informe o compromisso") @Size(max = 160) String titulo,
        @NotNull(message = "informe a data e a hora") LocalDateTime inicio,
        @Size(max = 160) String local,
        @Size(max = 2000) String obs,
        @Min(0) @Max(10080) Integer antecedenciaMin, // até 7 dias antes
        Long membroId
) {
}
