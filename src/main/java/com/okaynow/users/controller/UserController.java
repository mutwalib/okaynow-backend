package com.okaynow.users.controller;

import com.okaynow.users.dto.UserResponse;
import com.okaynow.users.mapper.UserMapper;
import com.okaynow.users.service.AccountDeletionService;
import com.okaynow.users.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserMapper userMapper;
    private final AccountDeletionService accountDeletionService;

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(Authentication authentication) {
        return ResponseEntity.ok(userMapper.toUserResponse(userService.getByEmail(authentication.getName())));
    }

    /**
     * Soft-deletes the authenticated user's account (App Store account-deletion requirement).
     */
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMe(Authentication authentication) {
        accountDeletionService.deleteOwnAccount(userService.getByEmail(authentication.getName()));
        return ResponseEntity.noContent().build();
    }
}
