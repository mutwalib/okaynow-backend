package com.okaynow.admin.service;

import com.okaynow.admin.dto.OpsAttentionResponse;
import com.okaynow.admin.dto.OpsAttentionResponse.OpsAttentionItem;
import com.okaynow.evv.domain.ClockMethod;
import com.okaynow.evv.repository.VisitRepository;
import com.okaynow.marketplace.domain.CredentialVerificationStatus;
import com.okaynow.marketplace.repository.CaregiverCredentialRepository;
import com.okaynow.payroll.domain.InvoiceStatus;
import com.okaynow.payroll.repository.ClientInvoiceRepository;
import com.okaynow.shifts.domain.Shift;
import com.okaynow.shifts.domain.ShiftStatus;
import com.okaynow.shifts.repository.ShiftRepository;
import com.okaynow.staffing.repository.ClientCaregiverAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OpsAttentionService {

    private static final int CREDENTIAL_WINDOW_DAYS = 30;
    private static final int EVV_LOOKBACK_DAYS = 14;

    private final ShiftRepository shiftRepository;
    private final ClientCaregiverAssignmentRepository assignmentRepository;
    private final CaregiverCredentialRepository credentialRepository;
    private final ClientInvoiceRepository invoiceRepository;
    private final VisitRepository visitRepository;

    @Transactional(readOnly = true)
    public OpsAttentionResponse build() {
        LocalDate today = LocalDate.now();
        List<Shift> openUnfilled = shiftRepository.findOpenUnfilledFrom(today);
        int openUnfilledCount = openUnfilled.size();

        int withoutKnown = 0;
        for (Shift s : openUnfilled) {
            if (s.getClientProfileId() == null) {
                continue;
            }
            if (assignmentRepository
                    .findByClientProfileIdAndActiveTrueOrderByCreatedAtAsc(s.getClientProfileId())
                    .isEmpty()) {
                withoutKnown++;
            }
        }

        int credsExpiring = (int) credentialRepository.countExpiringBetween(
                CredentialVerificationStatus.APPROVED,
                today,
                today.plusDays(CREDENTIAL_WINDOW_DAYS));

        int sentInvoices = (int) invoiceRepository
                .findByStatusOrderByIssuedDateDescCreatedAtDesc(
                        InvoiceStatus.SENT, PageRequest.of(0, 1))
                .getTotalElements();

        Instant since = Instant.now().minus(EVV_LOOKBACK_DAYS, ChronoUnit.DAYS);
        int evvExceptions = (int) visitRepository.countExceptionsSince(since, ClockMethod.MANUAL);

        List<OpsAttentionItem> items = new ArrayList<>();
        if (openUnfilledCount > 0) {
            items.add(new OpsAttentionItem(
                    "open_unfilled",
                    "Open seats to fill",
                    openUnfilledCount + " shift(s) still need caregivers",
                    "/shifts?status=OPEN",
                    openUnfilledCount,
                    openUnfilledCount >= 5 ? "high" : "medium"));
        }
        if (withoutKnown > 0) {
            items.add(new OpsAttentionItem(
                    "no_roster",
                    "Home shifts without a known caregiver",
                    withoutKnown + " open family shift(s) have no PRIMARY/roster — invite continuity first",
                    "/clients",
                    withoutKnown,
                    "high"));
        }
        if (credsExpiring > 0) {
            items.add(new OpsAttentionItem(
                    "creds_expiring",
                    "Credentials expiring soon",
                    credsExpiring + " approved credential(s) expire within "
                            + CREDENTIAL_WINDOW_DAYS + " days",
                    "/users",
                    credsExpiring,
                    "medium"));
        }
        if (sentInvoices > 0) {
            items.add(new OpsAttentionItem(
                    "invoices_sent",
                    "Unpaid client invoices",
                    sentInvoices + " SENT invoice(s) awaiting payment",
                    "/finance",
                    sentInvoices,
                    "medium"));
        }
        if (evvExceptions > 0) {
            items.add(new OpsAttentionItem(
                    "evv_exceptions",
                    "EVV / visit exceptions",
                    evvExceptions + " visit(s) in the last " + EVV_LOOKBACK_DAYS
                            + " days need review (manual clock or unconfirmed arrival)",
                    "/claims",
                    evvExceptions,
                    "medium"));
        }

        return new OpsAttentionResponse(
                openUnfilledCount,
                withoutKnown,
                credsExpiring,
                sentInvoices,
                evvExceptions,
                items);
    }
}
