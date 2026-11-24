package com.eclakehouse.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    public record Split(@NotNull UUID sellerId, @NotNull @DecimalMin("0.00") BigDecimal amount) {
    }

    public record AuthorizeRequest(@NotNull UUID orderId,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotNull PaymentMethod method,
            @Min(1) @Max(24) Integer installments,
            @NotEmpty List<@Valid Split> splits) {
    }

    public record PayoutResponse(UUID id, UUID orderId, UUID sellerId, BigDecimal grossAmount,
            BigDecimal commission, BigDecimal netAmount, VendorPayout.Status status, Instant createdAt) {
        static PayoutResponse of(VendorPayout p) {
            return new PayoutResponse(p.getId(), p.getOrderId(), p.getSellerId(), p.getGrossAmount(),
                    p.getCommission(), p.getNetAmount(), p.getStatus(), p.getCreatedAt());
        }
    }

    public record PaymentResponse(UUID id, UUID orderId, BigDecimal amount, PaymentMethod method,
            int installments, PaymentStatus status, String declineReason, List<PayoutResponse> payouts,
            Instant createdAt) {
        static PaymentResponse of(Payment p) {
            return new PaymentResponse(p.getId(), p.getOrderId(), p.getAmount(), p.getMethod(),
                    p.getInstallments(), p.getStatus(), p.getDeclineReason(),
                    p.getPayouts().stream().map(PayoutResponse::of).toList(), p.getCreatedAt());
        }
    }
}
