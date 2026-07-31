package com.okaynow.users.controller;

import com.okaynow.users.dto.UserResponse;
import com.okaynow.users.mapper.UserMapper;
import com.okaynow.users.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserMapper userMapper;

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(Authentication authentication) {
        return ResponseEntity.ok(userMapper.toUserResponse(userService.getByEmail(authentication.getName())));
    }
}
