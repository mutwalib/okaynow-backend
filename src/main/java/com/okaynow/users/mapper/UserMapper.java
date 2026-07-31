package com.okaynow.users.mapper;

import com.okaynow.users.domain.CaregiverProfile;
import com.okaynow.users.domain.ClientProfile;
import com.okaynow.users.domain.User;
import com.okaynow.users.dto.CaregiverProfileResponse;
import com.okaynow.users.dto.ClientProfileResponse;
import com.okaynow.users.dto.UserResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse toUserResponse(User user);

    @Mapping(target = "userId", source = "user.id")
    CaregiverProfileResponse toCaregiverProfileResponse(CaregiverProfile profile);

    @Mapping(target = "userId", source = "user.id")
    ClientProfileResponse toClientProfileResponse(ClientProfile profile);
}
