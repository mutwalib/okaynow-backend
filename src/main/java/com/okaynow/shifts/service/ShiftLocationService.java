package com.okaynow.shifts.service;

import com.okaynow.common.geo.GeocodingService;
import com.okaynow.shifts.domain.Shift;
import com.okaynow.shifts.repository.ShiftRepository;
import com.okaynow.users.repository.ClientProfileRepository;
import com.okaynow.users.repository.FacilityProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ensures visit shifts have lat/lng for EVV geofencing (geocode from address when missing).
 */
@Service
@RequiredArgsConstructor
public class ShiftLocationService {

    private final ShiftRepository shiftRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final FacilityProfileRepository facilityProfileRepository;
    private final GeocodingService geocodingService;

    /**
     * If the shift has no pin, geocode its address and persist on the shift
     * (and home/facility profile when those were also missing coords).
     *
     * @return the same shift instance with lat/lng set
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Shift ensureCoordinates(Shift shift) {
        if (shift.getLat() != null && shift.getLng() != null) {
            return shift;
        }
        // Re-load in this transaction so we don't fight a read-only outer tx.
        Shift managed = shiftRepository.findById(shift.getId()).orElse(shift);
        if (managed.getLat() != null && managed.getLng() != null) {
            shift.setLat(managed.getLat());
            shift.setLng(managed.getLng());
            return shift;
        }
        var point = geocodingService.requireGeocode(
                managed.getAddressLine(), managed.getCity(), managed.getState(), managed.getZip());
        managed.setLat(point.lat());
        managed.setLng(point.lng());
        shiftRepository.save(managed);
        shift.setLat(point.lat());
        shift.setLng(point.lng());
        if (managed.getFacilityProfileId() != null) {
            facilityProfileRepository.findById(managed.getFacilityProfileId()).ifPresent(facility -> {
                if (facility.getLat() == null || facility.getLng() == null) {
                    facility.setLat(point.lat());
                    facility.setLng(point.lng());
                    facilityProfileRepository.save(facility);
                }
            });
        }
        if (managed.getClientProfileId() != null) {
            clientProfileRepository.findById(managed.getClientProfileId()).ifPresent(client -> {
                if (client.getLat() == null || client.getLng() == null) {
                    client.setLat(point.lat());
                    client.setLng(point.lng());
                    clientProfileRepository.save(client);
                }
            });
        }
        return shift;
    }
}
