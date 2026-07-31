package com.okaynow.evv.dto;

public record ClockInRequest(
        Double lat,
        Double lng,
        String notes
) {
}
