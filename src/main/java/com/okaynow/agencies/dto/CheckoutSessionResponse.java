package com.okaynow.agencies.dto;

public record CheckoutSessionResponse(
        String checkoutUrl,
        String message
) {
}
