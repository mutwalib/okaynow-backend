package com.okaynow.users.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LegalNameGuardTest {

    @Test
    void allowsSameNameWithWhitespaceDifferences() {
        assertDoesNotThrow(() ->
                LegalNameGuard.assertUnchanged("Jane", "Doe", " Jane ", "  Doe "));
    }

    @Test
    void rejectsChangedName() {
        assertThrows(com.okaynow.common.exception.BadRequestException.class, () ->
                LegalNameGuard.assertUnchanged("Jane", "Doe", "Janet", "Doe"));
    }
}
