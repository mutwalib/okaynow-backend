package com.okaynow.shifts.dto;

import com.okaynow.users.domain.Role;

/**
 * Role-scoped shift payloads: caregivers see pay only; clients/facilities see bill only;
 * admins see both.
 */
public final class ShiftResponses {

    private ShiftResponses() {
    }

    public static ShiftResponse forViewer(ShiftResponse raw, Role role) {
        if (raw == null || role == null || role == Role.ADMIN) {
            return raw;
        }
        if (role == Role.CAREGIVER) {
            return copy(
                    raw,
                    raw.payRate(),
                    null,
                    false);
        }
        if (role == Role.CLIENT || role == Role.FACILITY) {
            return copy(
                    raw,
                    null,
                    raw.billRate(),
                    raw.platformPaid());
        }
        return raw;
    }

    private static ShiftResponse copy(
            ShiftResponse raw,
            java.math.BigDecimal payRate,
            java.math.BigDecimal billRate,
            boolean platformPaid) {
        return new ShiftResponse(
                raw.id(),
                raw.clientProfileId(),
                raw.facilityProfileId(),
                raw.requiredQualification(),
                raw.date(),
                raw.startTime(),
                raw.endTime(),
                raw.addressLine(),
                raw.city(),
                raw.state(),
                raw.zip(),
                raw.lat(),
                raw.lng(),
                payRate,
                billRate,
                raw.status(),
                raw.scheduleType(),
                raw.seriesId(),
                raw.notes(),
                platformPaid,
                raw.marketplacePosted(),
                raw.marketplaceSlots(),
                raw.requiredHeadcount(),
                raw.filledSlots(),
                raw.createdBy(),
                raw.createdAt());
    }
}
