package com.okaynow.users.domain;

/**
 * Platform roles per CLAUDE.md Section 3. Spring Security authorities are derived as
 * "ROLE_" + name().
 */
public enum Role {
    CAREGIVER,
    CLIENT,
    FACILITY,
    ADMIN
}
