package com.okaynow.shifts.mapper;

import com.okaynow.shifts.domain.Shift;
import com.okaynow.shifts.dto.ShiftResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ShiftMapper {

    ShiftResponse toResponse(Shift shift);
}
