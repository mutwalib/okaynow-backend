package com.okaynow.shifts.mapper;

import com.okaynow.shifts.domain.Shift;
import com.okaynow.shifts.dto.ShiftResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ShiftMapper {

    @Mapping(target = "agencyDisplayName", ignore = true)
    ShiftResponse toResponse(Shift shift);
}
