package com.okaynow.shifts.mapper;

import com.okaynow.shifts.domain.Shift;
import com.okaynow.shifts.domain.ShiftScheduleType;
import com.okaynow.shifts.domain.ShiftStatus;
import com.okaynow.shifts.dto.ShiftResponse;
import com.okaynow.users.domain.Qualification;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-08-13T13:23:55-0400",
    comments = "version: 1.5.5.Final, compiler: Eclipse JDT (IDE) 3.46.100.v20260624-0231, environment: Java 21.0.11 (Eclipse Adoptium)"
)
@Component
public class ShiftMapperImpl implements ShiftMapper {

    @Override
    public ShiftResponse toResponse(Shift shift) {
        if ( shift == null ) {
            return null;
        }

        UUID id = null;
        UUID clientProfileId = null;
        UUID facilityProfileId = null;
        Qualification requiredQualification = null;
        LocalDate date = null;
        LocalTime startTime = null;
        LocalTime endTime = null;
        String addressLine = null;
        String city = null;
        String state = null;
        String zip = null;
        Double lat = null;
        Double lng = null;
        BigDecimal payRate = null;
        BigDecimal billRate = null;
        ShiftStatus status = null;
        ShiftScheduleType scheduleType = null;
        UUID seriesId = null;
        String notes = null;
        boolean platformPaid = false;
        boolean marketplacePosted = false;
        int marketplaceSlots = 0;
        int requiredHeadcount = 0;
        int filledSlots = 0;
        BigDecimal surgeBonusPay = null;
        int surgeTierApplied = 0;
        int escalationRadiusBonusMiles = 0;
        UUID createdBy = null;
        Instant createdAt = null;

        id = shift.getId();
        clientProfileId = shift.getClientProfileId();
        facilityProfileId = shift.getFacilityProfileId();
        requiredQualification = shift.getRequiredQualification();
        date = shift.getDate();
        startTime = shift.getStartTime();
        endTime = shift.getEndTime();
        addressLine = shift.getAddressLine();
        city = shift.getCity();
        state = shift.getState();
        zip = shift.getZip();
        lat = shift.getLat();
        lng = shift.getLng();
        payRate = shift.getPayRate();
        billRate = shift.getBillRate();
        status = shift.getStatus();
        scheduleType = shift.getScheduleType();
        seriesId = shift.getSeriesId();
        notes = shift.getNotes();
        platformPaid = shift.isPlatformPaid();
        marketplacePosted = shift.isMarketplacePosted();
        marketplaceSlots = shift.getMarketplaceSlots();
        requiredHeadcount = shift.getRequiredHeadcount();
        filledSlots = shift.getFilledSlots();
        surgeBonusPay = shift.getSurgeBonusPay();
        surgeTierApplied = shift.getSurgeTierApplied();
        escalationRadiusBonusMiles = shift.getEscalationRadiusBonusMiles();
        createdBy = shift.getCreatedBy();
        createdAt = shift.getCreatedAt();

        ShiftResponse shiftResponse = new ShiftResponse( id, clientProfileId, facilityProfileId, requiredQualification, date, startTime, endTime, addressLine, city, state, zip, lat, lng, payRate, billRate, status, scheduleType, seriesId, notes, platformPaid, marketplacePosted, marketplaceSlots, requiredHeadcount, filledSlots, surgeBonusPay, surgeTierApplied, escalationRadiusBonusMiles, createdBy, createdAt );

        return shiftResponse;
    }
}
