package com.okaynow.marketplace.credentialing;

import com.okaynow.marketplace.domain.CaregiverCredential;

/**
 * Primary-source license / credential check (Nursys, MA boards, etc.).
 * Vendor adapters implement this; {@link StubPrimarySourceCredentialVerifier} is the default.
 */
public interface PrimarySourceCredentialVerifier {

    PrimarySourceResult verify(CaregiverCredential credential);

    record PrimarySourceResult(
            String status,
            String notes,
            boolean activeAndInGoodStanding
    ) {
    }
}
