package pe.edu.pucp.tasf.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;

public record CreateShipmentRequest(
    @NotBlank String origin,
    @NotBlank String destination,
    @Positive int quantity,
    @NotNull Instant deadline
) {}
