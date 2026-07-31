package com.okaynow.payroll.service;

import com.okaynow.audit.domain.AuditAction;
import com.okaynow.audit.service.AuditLogService;
import com.okaynow.common.dto.PagedResponse;
import com.okaynow.common.exception.BadRequestException;
import com.okaynow.common.exception.ConflictException;
import com.okaynow.common.exception.ResourceNotFoundException;
import com.okaynow.notifications.domain.NotificationType;
import com.okaynow.notifications.service.NotificationService;
import com.okaynow.payroll.domain.AgencySettings;
import com.okaynow.payroll.domain.ClientInvoice;
import com.okaynow.payroll.domain.ClientInvoiceLine;
import com.okaynow.payroll.domain.InvoiceStatus;
import com.okaynow.payroll.domain.PaymentStatus;
import com.okaynow.payroll.domain.ShiftSettlement;
import com.okaynow.payroll.dto.ClientInvoiceLineResponse;
import com.okaynow.payroll.dto.ClientInvoiceResponse;
import com.okaynow.payroll.dto.CreateClientInvoiceRequest;
import com.okaynow.payroll.dto.SettlementResponse;
import com.okaynow.payroll.repository.ClientInvoiceLineRepository;
import com.okaynow.payroll.repository.ClientInvoiceRepository;
import com.okaynow.payroll.repository.ShiftSettlementRepository;
import com.okaynow.reports.dto.GeneratedReport;
import com.okaynow.reports.support.ReportWriters;
import com.okaynow.users.domain.ClientProfile;
import com.okaynow.users.domain.FacilityProfile;
import com.okaynow.users.domain.Role;
import com.okaynow.users.domain.User;
import com.okaynow.users.domain.UserStatus;
import com.okaynow.users.repository.ClientProfileRepository;
import com.okaynow.users.repository.FacilityProfileRepository;
import com.okaynow.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceService {

    private final ClientInvoiceRepository invoiceRepository;
    private final ClientInvoiceLineRepository invoiceLineRepository;
    private final ShiftSettlementRepository settlementRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final FacilityProfileRepository facilityProfileRepository;
    private final UserRepository userRepository;
    private final SettlementService settlementService;
    private final AgencySettingsService agencySettingsService;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public PagedResponse<ClientInvoiceResponse> list(
            InvoiceStatus status,
            UUID clientProfileId,
            UUID facilityProfileId,
            String q,
            Pageable pageable) {
        String query = q == null ? null : q.trim();
        if (query != null && query.isEmpty()) {
            query = null;
        }
        var page = invoiceRepository.search(status, clientProfileId, facilityProfileId, query, pageable);
        return PagedResponse.from(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public ClientInvoiceResponse get(UUID id) {
        return toResponse(findWithLines(id));
    }

    @Transactional(readOnly = true)
    public List<SettlementResponse> uninvoicedPendingForClient(UUID clientProfileId) {
        if (!clientProfileRepository.existsById(clientProfileId)) {
            throw new ResourceNotFoundException("Client not found");
        }
        return settlementRepository
                .findUninvoicedPendingByClient(clientProfileId, PaymentStatus.PENDING)
                .stream()
                .map(settlementService::toAdminResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SettlementResponse> uninvoicedPendingForFacility(UUID facilityProfileId) {
        if (!facilityProfileRepository.existsById(facilityProfileId)) {
            throw new ResourceNotFoundException("Facility not found");
        }
        return settlementRepository
                .findUninvoicedPendingByFacility(facilityProfileId, PaymentStatus.PENDING)
                .stream()
                .map(settlementService::toAdminResponse)
                .toList();
    }

    /**
     * After a shift is completed and settled: create (and optionally send) an invoice
     * when agency auto-invoice settings are enabled.
     */
    @Transactional
    public ClientInvoiceResponse autoInvoiceForCompletedShift(UUID shiftId) {
        AgencySettings settings = agencySettingsService.getOrCreate();
        if (!settings.isAutoInvoiceOnComplete()) {
            return null;
        }
        ShiftSettlement settlement = settlementRepository.findByShiftId(shiftId).orElse(null);
        if (settlement == null
                || settlement.getClientPaymentStatus() != PaymentStatus.PENDING
                || settlement.getClientInvoiceId() != null
                || (settlement.getClientProfileId() == null && settlement.getFacilityProfileId() == null)) {
            return null;
        }
        User actor = systemActor();
        try {
            return create(new CreateClientInvoiceRequest(
                    settlement.getClientProfileId(),
                    settlement.getFacilityProfileId(),
                    List.of(settlement.getId()),
                    LocalDate.now().plusDays(14),
                    "Auto-generated for completed shift on " + settlement.getShiftDate(),
                    settings.isAutoInvoiceSendImmediately()), actor);
        } catch (Exception ex) {
            log.warn("Auto-invoice skipped for shift {}: {}", shiftId, ex.getMessage());
            return null;
        }
    }

    /**
     * Create one invoice per family client / facility for all currently uninvoiced pending settlements.
     */
    @Transactional
    public List<ClientInvoiceResponse> generateOutstandingInvoices(User actor, boolean sendNow) {
        List<ShiftSettlement> pending = settlementRepository.findAllUninvoicedPending(PaymentStatus.PENDING);
        Map<String, List<UUID>> byBillTo = new java.util.LinkedHashMap<>();
        Map<String, CreateClientInvoiceRequest> templates = new java.util.LinkedHashMap<>();
        for (ShiftSettlement s : pending) {
            if (s.getClientProfileId() != null) {
                String key = "FAMILY:" + s.getClientProfileId();
                byBillTo.computeIfAbsent(key, k -> new ArrayList<>()).add(s.getId());
                templates.putIfAbsent(key, new CreateClientInvoiceRequest(
                        s.getClientProfileId(), null, List.of(), LocalDate.now().plusDays(14),
                        "Auto-generated for completed care shifts", sendNow));
            } else if (s.getFacilityProfileId() != null) {
                String key = "FACILITY:" + s.getFacilityProfileId();
                byBillTo.computeIfAbsent(key, k -> new ArrayList<>()).add(s.getId());
                templates.putIfAbsent(key, new CreateClientInvoiceRequest(
                        null, s.getFacilityProfileId(), List.of(), LocalDate.now().plusDays(14),
                        "Auto-generated for completed care shifts", sendNow));
            }
        }
        List<ClientInvoiceResponse> created = new ArrayList<>();
        for (Map.Entry<String, List<UUID>> entry : byBillTo.entrySet()) {
            CreateClientInvoiceRequest template = templates.get(entry.getKey());
            created.add(create(new CreateClientInvoiceRequest(
                    template.clientProfileId(),
                    template.facilityProfileId(),
                    entry.getValue(),
                    template.dueDate(),
                    template.notes(),
                    sendNow), actor));
        }
        return created;
    }

    private User systemActor() {
        List<User> admins = userRepository.findByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE);
        if (!admins.isEmpty()) {
            return admins.getFirst();
        }
        throw new BadRequestException("No active admin user available to attribute auto-invoices");
    }

    @Transactional
    public ClientInvoiceResponse create(CreateClientInvoiceRequest request, User actor) {
        boolean hasClient = request.clientProfileId() != null;
        boolean hasFacility = request.facilityProfileId() != null;
        if (hasClient == hasFacility) {
            throw new BadRequestException("Provide exactly one of clientProfileId or facilityProfileId");
        }

        ClientProfile client = null;
        FacilityProfile facility = null;
        if (hasClient) {
            client = clientProfileRepository.findById(request.clientProfileId())
                    .orElseThrow(() -> new ResourceNotFoundException("Client not found"));
        } else {
            facility = facilityProfileRepository.findById(request.facilityProfileId())
                    .orElseThrow(() -> new ResourceNotFoundException("Facility not found"));
        }

        Set<UUID> uniqueIds = new LinkedHashSet<>(request.settlementIds());
        if (uniqueIds.isEmpty()) {
            throw new BadRequestException("Select at least one settlement to invoice");
        }

        List<ShiftSettlement> settlements = settlementRepository.findAllById(uniqueIds);
        if (settlements.size() != uniqueIds.size()) {
            throw new BadRequestException("One or more settlements were not found");
        }

        BigDecimal total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        List<ClientInvoiceLine> lines = new ArrayList<>();
        for (ShiftSettlement settlement : settlements) {
            if (hasClient) {
                validateSettlementForFamilyInvoice(settlement, client.getId());
            } else {
                validateSettlementForFacilityInvoice(settlement, facility.getId());
            }
            ClientInvoiceLine line = ClientInvoiceLine.builder()
                    .settlementId(settlement.getId())
                    .shiftId(settlement.getShiftId())
                    .shiftDate(settlement.getShiftDate())
                    .description("Care shift on " + settlement.getShiftDate()
                            + " · " + settlement.getHours() + " hrs @ $"
                            + settlement.getBillRate() + "/hr")
                    .hours(settlement.getHours())
                    .billRate(settlement.getBillRate())
                    .amount(settlement.getClientAmount())
                    .build();
            lines.add(line);
            total = total.add(settlement.getClientAmount());
        }

        LocalDate issued = LocalDate.now();
        LocalDate due = request.dueDate() != null ? request.dueDate() : issued.plusDays(14);
        if (due.isBefore(issued)) {
            throw new BadRequestException("dueDate cannot be before the issue date");
        }

        UUID billToId = hasClient ? client.getId() : facility.getId();
        ClientInvoice invoice = ClientInvoice.builder()
                .invoiceNumber(nextInvoiceNumber())
                .clientProfileId(hasClient ? client.getId() : null)
                .facilityProfileId(hasFacility ? facility.getId() : null)
                .status(InvoiceStatus.DRAFT)
                .issuedDate(issued)
                .dueDate(due)
                .totalAmount(total)
                .notes(blankToNull(request.notes()))
                .createdBy(actor.getId())
                .build();
        for (ClientInvoiceLine line : lines) {
            invoice.addLine(line);
        }
        invoice = invoiceRepository.save(invoice);

        for (ShiftSettlement settlement : settlements) {
            settlement.setClientInvoiceId(invoice.getId());
        }
        settlementRepository.saveAll(settlements);

        auditLogService.record(actor, AuditAction.INVOICE_CREATED, "INVOICE",
                invoice.getId(), billToId,
                "number=%s amount=%s lines=%s".formatted(
                        invoice.getInvoiceNumber(), total, lines.size()));

        if (request.sendNow()) {
            return send(invoice.getId(), actor);
        }
        return toResponse(findWithLines(invoice.getId()));
    }

    /**
     * Flat-fee invoice when a client rejects a claimed/assigned caregiver.
     * {@code amount} of zero is a no-op (returns null).
     */
    @Transactional
    public ClientInvoiceResponse createCaregiverRejectionFeeInvoice(
            UUID clientProfileId,
            UUID shiftId,
            LocalDate shiftDate,
            BigDecimal amount,
            String caregiverName,
            String reason,
            User actor,
            boolean sendNow) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        if (clientProfileId == null) {
            throw new BadRequestException("Rejection fee invoices require a family client");
        }
        ClientProfile client = clientProfileRepository.findById(clientProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found"));

        BigDecimal fee = amount.setScale(2, RoundingMode.HALF_UP);
        String desc = "Caregiver rejection fee"
                + (caregiverName != null && !caregiverName.isBlank()
                        ? " — " + caregiverName.trim()
                        : "")
                + " · shift " + shiftDate
                + (reason != null && !reason.isBlank() ? " · " + reason.trim() : "");

        ClientInvoiceLine line = ClientInvoiceLine.builder()
                .settlementId(null)
                .shiftId(shiftId)
                .shiftDate(shiftDate)
                .description(desc)
                .hours(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .billRate(fee)
                .amount(fee)
                .build();

        LocalDate issued = LocalDate.now();
        ClientInvoice invoice = ClientInvoice.builder()
                .invoiceNumber(nextInvoiceNumber())
                .clientProfileId(client.getId())
                .status(InvoiceStatus.DRAFT)
                .issuedDate(issued)
                .dueDate(issued.plusDays(14))
                .totalAmount(fee)
                .notes("Policy fee for rejecting a caregiver who claimed or was assigned to a shift.")
                .createdBy(actor.getId())
                .build();
        invoice.addLine(line);
        invoice = invoiceRepository.save(invoice);

        auditLogService.record(actor, AuditAction.INVOICE_CREATED, "INVOICE",
                invoice.getId(), client.getId(),
                "rejection-fee number=%s amount=%s shift=%s".formatted(
                        invoice.getInvoiceNumber(), fee, shiftId));

        if (sendNow) {
            return send(invoice.getId(), actor);
        }
        return toResponse(findWithLines(invoice.getId()));
    }

    /**
     * Flat fee when a family/facility hires a caregiver found via the platform for
     * ongoing private (off-platform) care — charged per Terms of Service.
     */
    @Transactional
    public ClientInvoiceResponse createPlatformConversionFeeInvoice(
            UUID clientProfileId,
            UUID facilityProfileId,
            UUID caregiverProfileId,
            BigDecimal amount,
            String caregiverName,
            String notes,
            User actor,
            boolean sendNow) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        boolean hasClient = clientProfileId != null;
        boolean hasFacility = facilityProfileId != null;
        if (hasClient == hasFacility) {
            throw new BadRequestException("Provide exactly one of clientProfileId or facilityProfileId");
        }
        if (caregiverProfileId == null) {
            throw new BadRequestException("caregiverProfileId is required for a conversion fee");
        }
        if (hasActiveConversionForCaregiver(clientProfileId, facilityProfileId, caregiverProfileId)) {
            throw new ConflictException(
                    "A platform conversion fee was already invoiced for this caregiver");
        }

        BigDecimal fee = amount.setScale(2, RoundingMode.HALF_UP);
        String desc = "Platform conversion fee"
                + (caregiverName != null && !caregiverName.isBlank()
                        ? " — hiring " + caregiverName.trim() + " off-platform"
                        : " — off-platform hire of a caregiver connected via OkayNow");

        ClientInvoiceLine line = ClientInvoiceLine.builder()
                .settlementId(null)
                .shiftId(null)
                .caregiverProfileId(caregiverProfileId)
                .shiftDate(LocalDate.now())
                .description(desc)
                .hours(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .billRate(fee)
                .amount(fee)
                .build();

        LocalDate issued = LocalDate.now();
        ClientInvoice invoice = ClientInvoice.builder()
                .invoiceNumber(nextInvoiceNumber())
                .clientProfileId(clientProfileId)
                .facilityProfileId(facilityProfileId)
                .status(InvoiceStatus.DRAFT)
                .issuedDate(issued)
                .dueDate(issued.plusDays(14))
                .totalAmount(fee)
                .notes(blankToNull(notes) != null
                        ? blankToNull(notes)
                        : "Fee required by OkayNow Terms when continuing care privately with a caregiver introduced through the platform.")
                .createdBy(actor.getId())
                .build();
        invoice.addLine(line);
        invoice = invoiceRepository.save(invoice);

        UUID billTo = hasClient ? clientProfileId : facilityProfileId;
        auditLogService.record(actor, AuditAction.INVOICE_CREATED, "INVOICE",
                invoice.getId(), billTo,
                "conversion-fee number=%s amount=%s caregiver=%s"
                        .formatted(invoice.getInvoiceNumber(), fee, caregiverProfileId));

        if (sendNow) {
            return send(invoice.getId(), actor);
        }
        return toResponse(findWithLines(invoice.getId()));
    }

    @Transactional(readOnly = true)
    public boolean hasActiveConversionForCaregiver(
            UUID clientProfileId, UUID facilityProfileId, UUID caregiverProfileId) {
        return invoiceLineRepository.existsActiveConversionForCaregiver(
                clientProfileId, facilityProfileId, caregiverProfileId);
    }

    @Transactional(readOnly = true)
    public List<UUID> listReportedConversionCaregiverIds(
            UUID clientProfileId, UUID facilityProfileId) {
        return invoiceLineRepository.findReportedConversionCaregiverIds(
                clientProfileId, facilityProfileId);
    }

    @Transactional
    public ClientInvoiceResponse send(UUID invoiceId, User actor) {
        ClientInvoice invoice = findWithLines(invoiceId);
        if (invoice.getStatus() == InvoiceStatus.VOID) {
            throw new ConflictException("Cannot send a voided invoice");
        }
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new ConflictException("Invoice is already paid");
        }
        if (invoice.getStatus() == InvoiceStatus.SENT) {
            return toResponse(invoice);
        }

        invoice.setStatus(InvoiceStatus.SENT);
        invoice.setSentAt(Instant.now());
        invoiceRepository.save(invoice);

        User notifyUser;
        UUID billToId;
        if (invoice.getFacilityProfileId() != null) {
            FacilityProfile facility = facilityProfileRepository.findById(invoice.getFacilityProfileId())
                    .orElseThrow(() -> new ResourceNotFoundException("Facility not found"));
            notifyUser = facility.getUser();
            billToId = facility.getId();
        } else {
            ClientProfile client = clientProfileRepository.findById(invoice.getClientProfileId())
                    .orElseThrow(() -> new ResourceNotFoundException("Client not found"));
            notifyUser = client.getUser();
            billToId = client.getId();
        }
        String payload = "{\"invoiceId\":\"%s\",\"invoiceNumber\":\"%s\",\"totalAmount\":%s,\"dueDate\":\"%s\"}"
                .formatted(
                        invoice.getId(),
                        invoice.getInvoiceNumber(),
                        invoice.getTotalAmount(),
                        invoice.getDueDate());
        notificationService.notifyUser(
                notifyUser,
                NotificationType.INVOICE_SENT,
                "Invoice " + invoice.getInvoiceNumber(),
                "OkayNow is requesting payment of $"
                        + invoice.getTotalAmount()
                        + " by "
                        + invoice.getDueDate()
                        + ".",
                payload);

        auditLogService.record(actor, AuditAction.INVOICE_SENT, "INVOICE",
                invoice.getId(), billToId,
                "number=%s".formatted(invoice.getInvoiceNumber()));
        return toResponse(invoice);
    }

    @Transactional
    public ClientInvoiceResponse markPaid(UUID invoiceId, User actor) {
        ClientInvoice invoice = findWithLines(invoiceId);
        if (invoice.getStatus() == InvoiceStatus.VOID) {
            throw new ConflictException("Cannot mark a voided invoice as paid");
        }
        if (invoice.getStatus() == InvoiceStatus.DRAFT) {
            throw new BadRequestException("Send the invoice before marking it paid");
        }
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            return toResponse(invoice);
        }

        // Prefer settlements linked by invoice id; fall back to line settlement ids.
        List<ShiftSettlement> linked = settlementRepository.findByClientInvoiceId(invoiceId);
        Set<UUID> settlementIds = new LinkedHashSet<>();
        for (ShiftSettlement s : linked) {
            settlementIds.add(s.getId());
        }
        for (ClientInvoiceLine line : invoice.getLines()) {
            if (line.getSettlementId() != null) {
                settlementIds.add(line.getSettlementId());
            }
        }
        for (UUID settlementId : settlementIds) {
            settlementService.markClientPayment(settlementId, PaymentStatus.PAID, actor);
        }

        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidAt(Instant.now());
        invoiceRepository.save(invoice);

        auditLogService.record(actor, AuditAction.INVOICE_PAID, "INVOICE",
                invoice.getId(), billToId(invoice),
                "number=%s settlements=%s".formatted(
                        invoice.getInvoiceNumber(), settlementIds.size()));
        return toResponse(findWithLines(invoiceId));
    }

    @Transactional
    public ClientInvoiceResponse voidInvoice(UUID invoiceId, User actor) {
        ClientInvoice invoice = findWithLines(invoiceId);
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new ConflictException("Cannot void a paid invoice");
        }
        if (invoice.getStatus() == InvoiceStatus.VOID) {
            return toResponse(invoice);
        }

        Set<UUID> settlementIds = new HashSet<>();
        for (ClientInvoiceLine line : invoice.getLines()) {
            if (line.getSettlementId() != null) {
                settlementIds.add(line.getSettlementId());
            }
        }
        List<ShiftSettlement> settlements = settlementRepository.findAllById(settlementIds);
        for (ShiftSettlement settlement : settlements) {
            if (invoice.getId().equals(settlement.getClientInvoiceId())) {
                settlement.setClientInvoiceId(null);
            }
        }
        settlementRepository.saveAll(settlements);

        invoice.setStatus(InvoiceStatus.VOID);
        invoice.setVoidedAt(Instant.now());
        invoiceRepository.save(invoice);

        auditLogService.record(actor, AuditAction.INVOICE_VOIDED, "INVOICE",
                invoice.getId(), billToId(invoice),
                "number=%s".formatted(invoice.getInvoiceNumber()));
        return toResponse(invoice);
    }

    @Transactional(readOnly = true)
    public ClientInvoiceResponse getForClientUser(UUID invoiceId, User actor) {
        ClientProfile client = clientProfileRepository.findByUserId(actor.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Client profile not found"));
        ClientInvoice invoice = findWithLines(invoiceId);
        if (!client.getId().equals(invoice.getClientProfileId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You can only view your own invoices");
        }
        if (invoice.getStatus() == InvoiceStatus.DRAFT) {
            throw new ResourceNotFoundException("Invoice not found");
        }
        return toResponse(invoice);
    }

    @Transactional(readOnly = true)
    public PagedResponse<ClientInvoiceResponse> listForClientUser(User actor, Pageable pageable) {
        ClientProfile client = clientProfileRepository.findByUserId(actor.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Client profile not found"));
        return PagedResponse.from(
                invoiceRepository
                        .findByClientProfileIdAndStatusNotOrderByIssuedDateDescCreatedAtDesc(
                                client.getId(), InvoiceStatus.DRAFT, pageable)
                        .map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public ClientInvoiceResponse getForFacilityUser(UUID invoiceId, User actor) {
        FacilityProfile facility = facilityProfileRepository.findByUserId(actor.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Facility profile not found"));
        ClientInvoice invoice = findWithLines(invoiceId);
        if (!facility.getId().equals(invoice.getFacilityProfileId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You can only view your own invoices");
        }
        if (invoice.getStatus() == InvoiceStatus.DRAFT) {
            throw new ResourceNotFoundException("Invoice not found");
        }
        return toResponse(invoice);
    }

    @Transactional(readOnly = true)
    public PagedResponse<ClientInvoiceResponse> listForFacilityUser(User actor, Pageable pageable) {
        FacilityProfile facility = facilityProfileRepository.findByUserId(actor.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Facility profile not found"));
        return PagedResponse.from(
                invoiceRepository
                        .findByFacilityProfileIdAndStatusNotOrderByIssuedDateDescCreatedAtDesc(
                                facility.getId(), InvoiceStatus.DRAFT, pageable)
                        .map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public GeneratedReport exportPdf(UUID invoiceId, User actor) throws Exception {
        ClientInvoice invoice = findWithLines(invoiceId);
        return buildPdf(invoice, actor.getEmail());
    }

    @Transactional(readOnly = true)
    public GeneratedReport exportPdfForClient(UUID invoiceId, User actor) throws Exception {
        getForClientUser(invoiceId, actor);
        ClientInvoice invoice = findWithLines(invoiceId);
        return buildPdf(invoice, actor.getEmail());
    }

    @Transactional(readOnly = true)
    public GeneratedReport exportPdfForFacility(UUID invoiceId, User actor) throws Exception {
        getForFacilityUser(invoiceId, actor);
        ClientInvoice invoice = findWithLines(invoiceId);
        return buildPdf(invoice, actor.getEmail());
    }

    private GeneratedReport buildPdf(ClientInvoice invoice, String generatedFor) throws Exception {
        String billToName;
        String billToAddress;
        String billToContact;
        if (invoice.getFacilityProfileId() != null) {
            FacilityProfile facility = facilityProfileRepository.findById(invoice.getFacilityProfileId())
                    .orElse(null);
            billToName = facility != null ? facility.getFacilityName() : "Facility";
            billToAddress = formatFacilityAddress(facility);
            billToContact = facility != null && facility.getUser() != null
                    ? facility.getUser().getEmail()
                    : null;
        } else {
            ClientProfile client = invoice.getClientProfileId() != null
                    ? clientProfileRepository.findById(invoice.getClientProfileId()).orElse(null)
                    : null;
            billToName = client != null
                    ? (client.getFirstName() + " " + client.getLastName()).trim()
                    : "Client";
            billToAddress = formatClientAddress(client);
            billToContact = client != null && client.getUser() != null
                    ? client.getUser().getEmail()
                    : null;
        }

        List<ReportWriters.InvoiceLine> lines = invoice.getLines().stream()
                .map(line -> new ReportWriters.InvoiceLine(
                        String.valueOf(line.getShiftDate()),
                        line.getDescription(),
                        line.getHours() != null ? line.getHours().toPlainString() : "",
                        money(line.getBillRate()),
                        money(line.getAmount())))
                .toList();

        byte[] bytes = ReportWriters.invoicePdf(
                invoice.getInvoiceNumber(),
                invoice.getStatus().name(),
                billToName,
                billToAddress,
                billToContact,
                String.valueOf(invoice.getIssuedDate()),
                String.valueOf(invoice.getDueDate()),
                invoice.getNotes(),
                lines,
                money(invoice.getTotalAmount()),
                generatedFor);

        String filename = invoice.getInvoiceNumber().replaceAll("[^A-Za-z0-9._-]", "_") + ".pdf";
        return new GeneratedReport(filename, "application/pdf", bytes);
    }

    private static String formatFacilityAddress(FacilityProfile facility) {
        if (facility == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (facility.getAddressLine() != null && !facility.getAddressLine().isBlank()) {
            sb.append(facility.getAddressLine().trim());
        }
        String city = blankToNull(facility.getCity());
        String state = blankToNull(facility.getState());
        String zip = blankToNull(facility.getZip());
        StringBuilder loc = new StringBuilder();
        if (city != null) {
            loc.append(city);
        }
        if (state != null) {
            if (!loc.isEmpty()) {
                loc.append(", ");
            }
            loc.append(state);
        }
        if (zip != null) {
            if (!loc.isEmpty()) {
                loc.append(" ");
            }
            loc.append(zip);
        }
        if (!loc.isEmpty()) {
            if (!sb.isEmpty()) {
                sb.append("\n");
            }
            sb.append(loc);
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private static String formatClientAddress(ClientProfile client) {
        if (client == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (client.getAddressLine() != null && !client.getAddressLine().isBlank()) {
            sb.append(client.getAddressLine().trim());
        }
        String city = blankToNull(client.getCity());
        String state = blankToNull(client.getState());
        String zip = blankToNull(client.getZip());
        StringBuilder loc = new StringBuilder();
        if (city != null) {
            loc.append(city);
        }
        if (state != null) {
            if (!loc.isEmpty()) {
                loc.append(", ");
            }
            loc.append(state);
        }
        if (zip != null) {
            if (!loc.isEmpty()) {
                loc.append(" ");
            }
            loc.append(zip);
        }
        if (!loc.isEmpty()) {
            if (!sb.isEmpty()) {
                sb.append("\n");
            }
            sb.append(loc);
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private static String money(BigDecimal amount) {
        if (amount == null) {
            return "$0.00";
        }
        return NumberFormat.getCurrencyInstance(Locale.US).format(amount);
    }

    private void validateSettlementForFamilyInvoice(ShiftSettlement settlement, UUID clientProfileId) {
        if (settlement.getClientProfileId() == null
                || !clientProfileId.equals(settlement.getClientProfileId())) {
            throw new BadRequestException(
                    "Settlement " + settlement.getId() + " does not belong to this client");
        }
        assertSettlementInvoiceable(settlement);
    }

    private void validateSettlementForFacilityInvoice(ShiftSettlement settlement, UUID facilityProfileId) {
        if (settlement.getFacilityProfileId() == null
                || !facilityProfileId.equals(settlement.getFacilityProfileId())) {
            throw new BadRequestException(
                    "Settlement " + settlement.getId() + " does not belong to this facility");
        }
        assertSettlementInvoiceable(settlement);
    }

    private void assertSettlementInvoiceable(ShiftSettlement settlement) {
        if (settlement.getClientPaymentStatus() != PaymentStatus.PENDING) {
            throw new BadRequestException(
                    "Settlement " + settlement.getId() + " is already marked paid");
        }
        if (settlement.getClientInvoiceId() != null) {
            throw new ConflictException(
                    "Settlement " + settlement.getId() + " is already on another invoice");
        }
    }

    private String nextInvoiceNumber() {
        String prefix = "INV-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-";
        for (int i = 0; i < 8; i++) {
            String candidate = prefix + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
            if (!invoiceRepository.existsByInvoiceNumber(candidate)) {
                return candidate;
            }
        }
        return prefix + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private ClientInvoice findWithLines(UUID id) {
        return invoiceRepository.findWithLinesById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
    }

    private static UUID billToId(ClientInvoice invoice) {
        return invoice.getFacilityProfileId() != null
                ? invoice.getFacilityProfileId()
                : invoice.getClientProfileId();
    }

    private ClientInvoiceResponse toResponse(ClientInvoice invoice) {
        ClientProfile client = invoice.getClientProfileId() != null
                ? clientProfileRepository.findById(invoice.getClientProfileId()).orElse(null)
                : null;
        FacilityProfile facility = invoice.getFacilityProfileId() != null
                ? facilityProfileRepository.findById(invoice.getFacilityProfileId()).orElse(null)
                : null;
        List<ClientInvoiceLineResponse> lines = invoice.getLines().stream()
                .map(line -> new ClientInvoiceLineResponse(
                        line.getId(),
                        line.getSettlementId(),
                        line.getShiftId(),
                        line.getShiftDate(),
                        line.getDescription(),
                        line.getHours(),
                        line.getBillRate(),
                        line.getAmount()))
                .toList();
        return new ClientInvoiceResponse(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getClientProfileId(),
                client != null ? client.getFirstName() : null,
                client != null ? client.getLastName() : null,
                invoice.getFacilityProfileId(),
                facility != null ? facility.getFacilityName() : null,
                invoice.getStatus(),
                invoice.getIssuedDate(),
                invoice.getDueDate(),
                invoice.getTotalAmount(),
                invoice.getNotes(),
                invoice.getSentAt(),
                invoice.getPaidAt(),
                invoice.getVoidedAt(),
                invoice.getCreatedAt(),
                lines);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
