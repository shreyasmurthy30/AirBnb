package com.project.airBnbApp.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class UpdateInventoryRequestDto {
    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    @NotNull(message = "Surge factor is required")
    @DecimalMin(value = "0.1", message = "Surge factor must be at least 0.1")
    @DecimalMax(value = "10.0", message = "Surge factor cannot exceed 10.0")
    private BigDecimal surgeFactor;

    private Boolean closed;
}
