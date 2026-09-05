package com.okaynow.discipline.dto;

/**
 * Outcome of recording a no-show against a caregiver's discipline record.
 *
 * @param warningNumber 1-based count after this no-show
 * @param restricted    true when this event triggered automatic platform restriction
 * @param maxWarnings   warnings that trigger restriction (inclusive)
 */
public record NoShowDisciplineResult(int warningNumber, boolean restricted, int maxWarnings) {
}
