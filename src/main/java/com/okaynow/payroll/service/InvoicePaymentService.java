package com.okaynow.payroll.service;

import com.okaynow.agencies.config.StripeProperties;
import com.okaynow.agencies.domain.Agency;
import com.okaynow.agencies.dto.CheckoutSessionResponse;
import com.okaynow.agencies.repository.AgencyRepository;
import com.okaynow.common.exception.BadRequestException;
import com.okaynow.common.exception.ConflictException;
import com.okaynow.common.exception.ResourceNotFoundException;
import com.okaynow.payroll.domain.ClientInvoice;
import com.okaynow.payroll.domain.InvoiceStatus;
import com.okaynow.payroll.repository.ClientInvoiceRepository;
import com.okaynow.users.domain.ClientProfile;
import com.okaynow.users.domain.User;
import com.okaynow.users.repository.ClientProfileRepository;
import com.stripe.Stripe;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;

/**
 * Collects private-pay home invoices into the agency's Stripe Connect account.
 * OkayNow does not take the funds (application fees can be added later).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvoicePaymentService {

    private final StripeProperties stripeProperties;
    private final ClientInvoiceRepository invoiceRepository;
    private final AgencyRepository agencyRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final InvoiceService invoiceService;

    @Transactional
    public CheckoutSessionResponse createCheckoutForClient(UUID invoiceId, User clientUser) {
        ClientProfile profile = clientProfileRepository.findByUserId(clientUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Client profile not found"));
        ClientInvoice invoice = invoiceRepository.findWithLinesById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
        if (invoice.getClientProfileId() == null
                || !invoice.getClientProfileId().equals(profile.getId())) {
            throw new ResourceNotFoundException("Invoice not found");
        }
        if (invoice.getStatus() != InvoiceStatus.SENT) {
            throw new ConflictException("Only sent invoices can be paid online");
        }
        if (invoice.getTotalAmount() == null
                || invoice.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Invoice has no amount due");
        }
        if (invoice.getAgencyId() == null) {
            throw new BadRequestException(
                    "This invoice is not linked to an agency Connect account. Contact the agency to pay.");
        }
        Agency agency = agencyRepository.findById(invoice.getAgencyId())
                .orElseThrow(() -> new ResourceNotFoundException("Agency not found"));
        if (agency.getStripeConnectAccountId() == null
                || agency.getStripeConnectAccountId().isBlank()
                || !agency.isStripeConnectChargesEnabled()) {
            throw new BadRequestException(
                    "This agency is not ready to collect online payments yet. Contact them to pay.");
        }
        if (!stripeProperties.isConfigured()) {
            return new CheckoutSessionResponse(
                    null,
                    "Online payments are not configured. Contact your agency to arrange payment.");
        }

        Stripe.apiKey = stripeProperties.getSecretKey();
        long amountCents = invoice.getTotalAmount()
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
        try {
            SessionCreateParams.Builder params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl(stripeProperties.getInvoiceSuccessUrl())
                    .setCancelUrl(stripeProperties.getInvoiceCancelUrl())
                    .setCustomerEmail(clientUser.getEmail())
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setQuantity(1L)
                            .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                    .setCurrency("usd")
                                    .setUnitAmount(amountCents)
                                    .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                            .setName("Invoice " + invoice.getInvoiceNumber())
                                            .setDescription(agency.getDisplayName() + " — home care services")
                                            .build())
                                    .build())
                            .build())
                    .putMetadata("purpose", "HOME_INVOICE")
                    .putMetadata("invoiceId", invoice.getId().toString())
                    .putMetadata("agencyId", agency.getId().toString())
                    .setPaymentIntentData(SessionCreateParams.PaymentIntentData.builder()
                            .setTransferData(SessionCreateParams.PaymentIntentData.TransferData.builder()
                                    .setDestination(agency.getStripeConnectAccountId())
                                    .build())
                            .putMetadata("purpose", "HOME_INVOICE")
                            .putMetadata("invoiceId", invoice.getId().toString())
                            .putMetadata("agencyId", agency.getId().toString())
                            .build());

            Session session = Session.create(params.build());
            invoice.setStripeCheckoutSessionId(session.getId());
            invoiceRepository.save(invoice);
            return new CheckoutSessionResponse(session.getUrl(), null);
        } catch (Exception ex) {
            log.error("Invoice Connect checkout failed for invoice {}", invoiceId, ex);
            throw new BadRequestException("Unable to start payment. Try again later.");
        }
    }

    @Transactional
    public void applyCheckoutCompleted(Session session) {
        Map<String, String> metadata = session.getMetadata();
        if (metadata == null || !"HOME_INVOICE".equals(metadata.get("purpose"))) {
            return;
        }
        String invoiceIdRaw = metadata.get("invoiceId");
        if (invoiceIdRaw == null || invoiceIdRaw.isBlank()) {
            return;
        }
        UUID invoiceId = UUID.fromString(invoiceIdRaw);
        invoiceService.markPaidFromStripe(
                invoiceId,
                session.getId(),
                session.getPaymentIntent());
        log.info("Marked invoice {} paid via Stripe Connect checkout {}", invoiceId, session.getId());
    }
}
