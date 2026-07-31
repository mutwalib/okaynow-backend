package com.okaynow.admin.dto;

public record UpdateClientShiftPermissionsRequest(
        boolean canViewShifts,
        boolean canCreateShifts,
        boolean canUpdateShifts,
        boolean canDeleteShifts
) {
}
