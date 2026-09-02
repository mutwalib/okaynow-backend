package com.okaynow.users.domain;

/**
 * Platform roles. Spring Security authorities are derived as "ROLE_" + name().
 * CLIENT = home/family payer; AGENCY_ADMIN = tenant agency staff; ADMIN = platform super-admin.
 */
public enum Role {
    CAREGIVER,
    CLIENT,
    FACILITY,
    AGENCY_ADMIN,
    ADMIN
}
