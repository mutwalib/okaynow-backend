package com.okaynow.marketplace.credentialing;

import com.okaynow.marketplace.domain.CaregiverCredential;
import com.okaynow.marketplace.domain.CredentialType;
import org.springframework.stereotype.Component;

/**
 * Placeholder until Nursys / state-board integrations are wired.
 * Marks LICENSE credentials as {@code STUB_UNVERIFIED}; other types as {@code NOT_APPLICABLE}.
 */
@Component
public class StubPrimarySourceCredentialVerifier implements PrimarySourceCredentialVerifier {

    @Override
    public PrimarySourceResult verify(CaregiverCredential credential) {
        if (credential.getCredentialType() == CredentialType.LICENSE) {
            return new PrimarySourceResult(
                    "STUB_UNVERIFIED",
                    "Primary-source verification not configured. Wire Nursys or MA board adapter.",
                    false);
        }
        return new PrimarySourceResult(
                "NOT_APPLICABLE",
                "No primary-source adapter for " + credential.getCredentialType(),
                false);
    }
}
