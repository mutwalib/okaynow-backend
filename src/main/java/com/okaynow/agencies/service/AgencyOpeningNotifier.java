package com.okaynow.agencies.service;

import com.okaynow.agencies.domain.Agency;
import com.okaynow.agencies.domain.AgencyStaff;
import com.okaynow.agencies.repository.AgencyStaffRepository;
import com.okaynow.notifications.domain.NotificationType;
import com.okaynow.notifications.service.NotificationService;
import com.okaynow.shiftrequests.domain.ShiftRequest;
import com.okaynow.shiftrequests.domain.ShiftRequestAgency;
import com.okaynow.shifts.domain.Shift;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Pushes inbox / accepted-opening alerts to agency staff and refreshes their live boards.
 */
@Service
@RequiredArgsConstructor
public class AgencyOpeningNotifier {

    private final AgencyStaffRepository agencyStaffRepository;
    private final NotificationService notificationService;

    public void notifyRequestReceived(ShiftRequestAgency row) {
        ShiftRequest request = row.getShiftRequest();
        Agency agency = row.getAgency();
        String who = sourceLabel(request);
        String when = request.getStartDate()
                + (request.getStartTime() != null
                ? " " + request.getStartTime().toString().substring(0, 5)
                : "");
        String title = "New shift request";
        String body = who + " sent a " + request.getRequiredQualification()
                + " opening for " + when
                + (request.getCity() != null ? " in " + request.getCity() : "")
                + ". Review it in Shift requests.";
        String payload = "{\"inboxId\":\"" + row.getId()
                + "\",\"shiftRequestId\":\"" + request.getId()
                + "\",\"agencyId\":\"" + agency.getId()
                + "\",\"action\":\"SHIFT_REQUEST_RECEIVED\""
                + (request.getSourceShiftId() != null
                ? ",\"shiftId\":\"" + request.getSourceShiftId() + "\""
                : "")
                + "}";
        notifyStaff(agency.getId(), NotificationType.SHIFT_REQUEST_RECEIVED, title, body, payload);
        if (request.getSourceShiftId() != null) {
            notificationService.broadcastAgencyShiftBoard(
                    agency.getId(),
                    "SHIFT_REQUEST_RECEIVED",
                    request.getSourceShiftId(),
                    null);
        }
    }

    public void notifyRequestAccepted(ShiftRequestAgency row, Shift shift, boolean autoBroadcast) {
        Agency agency = row.getAgency();
        ShiftRequest request = row.getShiftRequest();
        String who = sourceLabel(request);
        String title = autoBroadcast ? "Opening auto-broadcast" : "Opening accepted";
        String body = autoBroadcast
                ? who + "'s " + shift.getRequiredQualification() + " opening on "
                + shift.getDate() + " was accepted and posted to your area roster."
                : who + "'s " + shift.getRequiredQualification() + " opening on "
                + shift.getDate() + " is on your shift board.";
        String payload = "{\"inboxId\":\"" + row.getId()
                + "\",\"shiftRequestId\":\"" + request.getId()
                + "\",\"shiftId\":\"" + shift.getId()
                + "\",\"agencyId\":\"" + agency.getId()
                + "\",\"action\":\"" + (autoBroadcast
                ? "SHIFT_REQUEST_AUTO_ACCEPTED"
                : "SHIFT_REQUEST_ACCEPTED")
                + "\"}";
        NotificationType type = autoBroadcast
                ? NotificationType.SHIFT_REQUEST_AUTO_ACCEPTED
                : NotificationType.SHIFT_REQUEST_ACCEPTED;
        notifyStaff(agency.getId(), type, title, body, payload);
        notificationService.broadcastAgencyShiftBoard(
                agency.getId(),
                type.name(),
                shift.getId(),
                shift.getStatus());
    }

    private void notifyStaff(
            UUID agencyId,
            NotificationType type,
            String title,
            String body,
            String payload) {
        List<AgencyStaff> staff = agencyStaffRepository.findByAgencyIdWithUsers(agencyId);
        for (AgencyStaff member : staff) {
            if (member.getUser() == null) {
                continue;
            }
            notificationService.notifyUser(member.getUser(), type, title, body, payload);
        }
    }

    private static String sourceLabel(ShiftRequest request) {
        if (request.getFacilityProfile() != null
                && request.getFacilityProfile().getFacilityName() != null
                && !request.getFacilityProfile().getFacilityName().isBlank()) {
            return request.getFacilityProfile().getFacilityName().trim();
        }
        if (request.getClientProfile() != null) {
            String first = request.getClientProfile().getFirstName() != null
                    ? request.getClientProfile().getFirstName().trim() : "";
            String last = request.getClientProfile().getLastName() != null
                    ? request.getClientProfile().getLastName().trim() : "";
            String full = (first + " " + last).trim();
            if (!full.isEmpty()) {
                return full;
            }
        }
        return "A connected home";
    }
}
