package com.okaynow.agencies.service;

import com.okaynow.agencies.config.StripeProperties;
import com.okaynow.agencies.domain.Agency;
import com.okaynow.agencies.dto.ConnectOnboardingResponse;
import com.okaynow.agencies.dto.ConnectStatusResponse;
import com.okaynow.agencies.repository.AgencyRepository;
import com.okaynow.agencies.support.AgencyAccessService;
import com.okaynow.common.exception.BadRequestException;
import com.stripe.Stripe;
import com.stripe.model.Account;
import com.stripe.model.AccountLink;
import com.stripe.param.AccountCreateParams;
import com.stripe.param.AccountLinkCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Stripe Connect Express onboarding so agencies can collect home invoices into their
 * own Stripe accounts. OkayNow does not run W-2/payroll — Connect is for client billing only.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StripeConnectService {

    private final StripeProperties stripeProperties;
    private final AgencyRepository agencyRepository;
    private final AgencyAccessService agencyAccessService;

    public boolean isConfigured() {
        return stripeProperties.isConfigured();
    }

    @Transactional(readOnly = true)
    public ConnectStatusResponse status(UUID userId) {
        Agency agency = agencyAccessService.requireAgencyForUser(userId);
        boolean hasAccount = agency.getStripeConnectAccountId() != null
                && !agency.getStripeConnectAccountId().isBlank();
        boolean charges = agency.isStripeConnectChargesEnabled();
        boolean payouts = agency.isStripeConnectPayoutsEnabled();
        return new ConnectStatusResponse(
                isConfigured(),
                hasAccount,
                charges,
                payouts,
                hasAccount && charges && payouts);
    }

    @Transactional
    public ConnectOnboardingResponse createOnboardingLink(UUID userId) {
        Agency agency = agencyAccessService.requireAgencyForUser(userId);
        agencyAccessService.assertAgencyAllowsWrites(agency);
        if (!isConfigured()) {
            return new ConnectOnboardingResponse(
                    null,
                    "Stripe is not configured. Contact platform support to enable Connect onboarding.");
        }
        Stripe.apiKey = stripeProperties.getSecretKey();
        try {
            String accountId = agency.getStripeConnectAccountId();
            if (accountId == null || accountId.isBlank()) {
                AccountCreateParams createParams = AccountCreateParams.builder()
                        .setType(AccountCreateParams.Type.EXPRESS)
                        .setCountry("US")
                        .setCapabilities(AccountCreateParams.Capabilities.builder()
                                .setCardPayments(AccountCreateParams.Capabilities.CardPayments.builder()
                                        .setRequested(true)
                                        .build())
                                .setTransfers(AccountCreateParams.Capabilities.Transfers.builder()
                                        .setRequested(true)
                                        .build())
                                .build())
                        .setBusinessProfile(AccountCreateParams.BusinessProfile.builder()
                                .setName(agency.getDisplayName())
                                .build())
                        .putMetadata("agencyId", agency.getId().toString())
                        .build();
                Account account = Account.create(createParams);
                accountId = account.getId();
                agency.setStripeConnectAccountId(accountId);
                agencyRepository.save(agency);
            }

            AccountLinkCreateParams linkParams = AccountLinkCreateParams.builder()
                    .setAccount(accountId)
                    .setRefreshUrl(stripeProperties.getConnectRefreshUrl())
                    .setReturnUrl(stripeProperties.getConnectReturnUrl())
                    .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
                    .build();
            AccountLink link = AccountLink.create(linkParams);
            return new ConnectOnboardingResponse(link.getUrl(), null);
        } catch (Exception ex) {
            log.error("Stripe Connect onboarding failed for agency {}", agency.getId(), ex);
            throw new BadRequestException("Unable to start Connect onboarding. Try again later.");
        }
    }

    @Transactional
    public void applyAccountUpdated(Account account) {
        if (account == null || account.getId() == null) {
            return;
        }
        agencyRepository.findByStripeConnectAccountId(account.getId()).ifPresent(agency -> {
            agency.setStripeConnectChargesEnabled(Boolean.TRUE.equals(account.getChargesEnabled()));
            agency.setStripeConnectPayoutsEnabled(Boolean.TRUE.equals(account.getPayoutsEnabled()));
            agencyRepository.save(agency);
            log.info("Updated Connect flags for agency {} charges={} payouts={}",
                    agency.getId(), agency.isStripeConnectChargesEnabled(),
                    agency.isStripeConnectPayoutsEnabled());
        });
    }
}
