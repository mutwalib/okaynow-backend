package com.okaynow.shifts.repository;

import com.okaynow.shifts.domain.Shift;
import com.okaynow.shifts.domain.DayPeriod;
import com.okaynow.shifts.domain.ShiftStatus;
import com.okaynow.users.domain.Qualification;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalTime;
import java.math.BigDecimal;
import java.util.UUID;

public final class ShiftSpecifications {

    private ShiftSpecifications() {
    }

    /**
     * @param rateField {@code "payRate"} (caregiver/admin) or {@code "billRate"} (client/facility)
     */
    public static Specification<Shift> withFilters(ShiftStatus status, Qualification qualification,
                                                   LocalDate dateFrom, LocalDate dateTo,
                                                   UUID clientProfileId,
                                                   UUID facilityProfileId,
                                                   BigDecimal minPay,
                                                   BigDecimal maxPay,
                                                   String rateField,
                                                   DayPeriod dayPeriod) {
        String ratePath = "billRate".equals(rateField) ? "billRate" : "payRate";
        return (root, query, cb) -> {
            var predicates = cb.conjunction();
            if (status != null) {
                predicates = cb.and(predicates, cb.equal(root.get("status"), status));
            }
            if (qualification != null) {
                predicates = cb.and(predicates, cb.equal(root.get("requiredQualification"), qualification));
            }
            if (dateFrom != null) {
                predicates = cb.and(predicates, cb.greaterThanOrEqualTo(root.get("date"), dateFrom));
            }
            if (dateTo != null) {
                predicates = cb.and(predicates, cb.lessThanOrEqualTo(root.get("date"), dateTo));
            }
            if (clientProfileId != null) {
                predicates = cb.and(predicates,
                        cb.equal(root.get("clientProfileId"), clientProfileId));
            }
            if (facilityProfileId != null) {
                predicates = cb.and(predicates,
                        cb.equal(root.get("facilityProfileId"), facilityProfileId));
            }
            if (minPay != null) {
                predicates = cb.and(predicates,
                        cb.greaterThanOrEqualTo(root.get(ratePath), minPay));
            }
            if (maxPay != null) {
                predicates = cb.and(predicates,
                        cb.lessThanOrEqualTo(root.get(ratePath), maxPay));
            }
            if (dayPeriod != null) {
                var start = root.<LocalTime>get("startTime");
                var periodPredicate = switch (dayPeriod) {
                    case MORNING -> cb.and(
                            cb.greaterThanOrEqualTo(start, LocalTime.of(5, 0)),
                            cb.lessThan(start, LocalTime.NOON));
                    case AFTERNOON -> cb.and(
                            cb.greaterThanOrEqualTo(start, LocalTime.NOON),
                            cb.lessThan(start, LocalTime.of(17, 0)));
                    case EVENING -> cb.and(
                            cb.greaterThanOrEqualTo(start, LocalTime.of(17, 0)),
                            cb.lessThan(start, LocalTime.of(21, 0)));
                    case NIGHT -> cb.or(
                            cb.greaterThanOrEqualTo(start, LocalTime.of(21, 0)),
                            cb.lessThan(start, LocalTime.of(5, 0)));
                    case ALL_DAY -> cb.greaterThanOrEqualTo(
                            root.get("durationMinutes"), 12 * 60);
                };
                predicates = cb.and(predicates, periodPredicate);
            }
            return predicates;
        };
    }

    /**
     * Facility board scope: owned facility profile, plus legacy rows created by that
     * facility user before facilityProfileId existed (no family client attached).
     */
    public static Specification<Shift> ownedByFacility(UUID facilityProfileId, UUID facilityUserId) {
        return (root, query, cb) -> cb.or(
                cb.equal(root.get("facilityProfileId"), facilityProfileId),
                cb.and(
                        cb.isNull(root.get("facilityProfileId")),
                        cb.isNull(root.get("clientProfileId")),
                        cb.equal(root.get("createdBy"), facilityUserId)));
    }
}
