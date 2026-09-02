package com.okaynow.agencies.service;

import com.okaynow.agencies.config.StripeProperties;
import com.okaynow.agencies.domain.Agency;
import com.okaynow.agencies.domain.SubscriptionPlan;
import com.okaynow.agencies.domain.SubscriptionStatus;
import com.okaynow.agencies.dto.CheckoutSessionResponse;
import com.okaynow.agencies.repository.AgencyRepository;
import com.okaynow.agencies.support.AgencyAccessService;
import com.okaynow.common.exception.BadRequestException;
import com.okaynow.payroll.service.InvoicePaymentService;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Account;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StripeBillingService {

    private final StripeProperties stripeProperties;
    private final AgencyRepository agencyRepository;
    private final AgencyAccessService agencyAccessService;
    private final StripeConnectService stripeConnectService;
    private final InvoicePaymentService invoicePaymentService;

    public boolean isConfigured() {
        return stripeProperties.isConfigured();
    }

    @Transactional
    public CheckoutSessionResponse createCheckoutSession(UUID userId, SubscriptionPlan plan) {
        Agency agency = agencyAccessService.requireAgencyForUser(userId);
        var staff = agencyAccessService.requireStaffForUser(userId);
        String customerEmail = staff.getUser().getEmail();
        if (!isConfigured()) {
            return new CheckoutSessionResponse(
                    null,
                    "Stripe billing is not configured. Contact platform support to activate your subscription.");
        }
        Stripe.apiKey = stripeProperties.getSecretKey();
        String priceId = priceIdForPlan(plan);
        if (priceId == null || priceId.isBlank()) {
            throw new BadRequestException("Billing is not configured for plan " + plan);
        }
        try {
            SessionCreateParams.Builder params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    .setSuccessUrl(stripeProperties.getSuccessUrl())
                    .setCancelUrl(stripeProperties.getCancelUrl())
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setPrice(priceId)
                            .setQuantity(1L)
                            .build())
                    .putMetadata("agencyId", agency.getId().toString())
                    .putMetadata("plan", plan.name());
            if (agency.getStripeCustomerId() != null) {
                params.setCustomer(agency.getStripeCustomerId());
            } else {
                params.setCustomerEmail(customerEmail);
            }
            Session session = Session.create(params.build());
            return new CheckoutSessionResponse(session.getUrl(), null);
        } catch (Exception ex) {
            log.error("Stripe checkout failed for agency {}", agency.getId(), ex);
            throw new BadRequestException("Unable to start checkout. Try again later.");
        }
    }

    @Transactional
    public void handleWebhook(String payload, String signatureHeader) {
        if (!isConfigured()) {
            log.debug("Ignoring Stripe webhook — Stripe not configured");
            return;
        }
        Event event;
        try {
            event = Webhook.constructEvent(
                    payload, signatureHeader, stripeProperties.getWebhookSecret());
        } catch (SignatureVerificationException ex) {
            throw new BadRequestException("Invalid Stripe webhook signature");
        }
        if ("checkout.session.completed".equals(event.getType())) {
            Session session = (Session) event.getDataObjectDeserializer()
                    .getObject()
                    .orElse(null);
            if (session != null) {
                Map<String, String> metadata = session.getMetadata();
                if (metadata != null && "HOME_INVOICE".equals(metadata.get("purpose"))) {
                    invoicePaymentService.applyCheckoutCompleted(session);
                } else {
                    applySubscriptionCheckoutCompleted(session);
                }
            }
        } else if ("account.updated".equals(event.getType())) {
            Account account = (Account) event.getDataObjectDeserializer()
                    .getObject()
                    .orElse(null);
            if (account != null) {
                stripeConnectService.applyAccountUpdated(account);
            }
        } else if ("customer.subscription.updated".equals(event.getType())
                || "customer.subscription.deleted".equals(event.getType())) {
            log.info("Received Stripe subscription event {}", event.getType());
        }
    }

    private void applySubscriptionCheckoutCompleted(Session session) {
        Map<String, String> metadata = session.getMetadata();
        if (metadata == null || !metadata.containsKey("agencyId")) {
            return;
        }
        // Home invoice checkouts also carry agencyId — ignore those here.
        if ("HOME_INVOICE".equals(metadata.get("purpose"))) {
            return;
        }
        UUID agencyId = UUID.fromString(metadata.get("agencyId"));
        agencyRepository.findById(agencyId).ifPresent(agency -> {
            agency.setStripeCustomerId(session.getCustomer());
            agency.setStripeSubscriptionId(session.getSubscription());
            agency.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
            if (metadata.containsKey("plan")) {
                agency.setSubscriptionPlan(SubscriptionPlan.valueOf(metadata.get("plan")));
            }
            agency.setSubscriptionPeriodStart(Instant.now());
            agency.setDirectoryListed(true);
            agencyRepository.save(agency);
            log.info("Activated subscription for agency {}", agencyId);
        });
    }

    private String priceIdForPlan(SubscriptionPlan plan) {
        return switch (plan) {
            case STARTER -> stripeProperties.getPriceStarter();
            case PROFESSIONAL -> stripeProperties.getPriceProfessional();
            case FEATURED -> stripeProperties.getPriceFeatured();
        };
    }
}
