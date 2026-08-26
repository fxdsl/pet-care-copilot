package com.petassistant.business.controller;

import java.security.Principal;

import com.petassistant.business.data.dto.request.UpdateUserProfileRequest;
import com.petassistant.business.data.dto.response.CurrentUserResponse;
import com.petassistant.business.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 当前登录用户资料入口，用户 ID 只取自通过验证的 JWT。 */
@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public CurrentUserResponse current(Principal principal) {
        return userService.current(principal.getName());
    }

    @PatchMapping
    public CurrentUserResponse update(
            Principal principal,
            @Valid @RequestBody UpdateUserProfileRequest request
    ) {
        return userService.update(principal.getName(), request);
    }
}
