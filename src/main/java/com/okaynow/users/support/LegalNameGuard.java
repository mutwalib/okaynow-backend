package com.okaynow.users.support;

import com.okaynow.common.exception.BadRequestException;

/**
 * Legal / registered names are immutable after signup for self-service updates.
 * Corrections require agency staff to update the name on the user's behalf.
 */
public final class LegalNameGuard {

    private static final String MESSAGE =
            "Names cannot be changed after registration. Contact the agency if a correction is required.";

    private LegalNameGuard() {
    }

    public static void assertUnchanged(String currentFirst, String currentLast,
                                       String requestedFirst, String requestedLast) {
        if (!same(currentFirst, requestedFirst) || !same(currentLast, requestedLast)) {
            throw new BadRequestException(MESSAGE);
        }
    }

    private static boolean same(String a, String b) {
        return normalize(a).equals(normalize(b));
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }
}
