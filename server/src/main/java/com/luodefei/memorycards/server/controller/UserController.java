package com.luodefei.memorycards.server.controller;

import com.luodefei.memorycards.server.dto.AuthDtos.UserProfile;
import com.luodefei.memorycards.server.dto.MembershipDtos.PointsTransaction;
import com.luodefei.memorycards.server.security.AuthUser;
import com.luodefei.memorycards.server.service.AuthService;
import com.luodefei.memorycards.server.service.MembershipService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final AuthService authService;
    private final MembershipService membershipService;

    public UserController(AuthService authService, MembershipService membershipService) {
        this.authService = authService;
        this.membershipService = membershipService;
    }

    @GetMapping("/me")
    public UserProfile me(@AuthenticationPrincipal AuthUser user) {
        return authService.profile(user.id());
    }

    @GetMapping("/me/points")
    public List<PointsTransaction> points(@AuthenticationPrincipal AuthUser user) {
        return membershipService.transactions(user.id());
    }
}
