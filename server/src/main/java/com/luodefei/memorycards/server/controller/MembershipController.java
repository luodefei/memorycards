package com.luodefei.memorycards.server.controller;

import com.luodefei.memorycards.server.dto.AuthDtos.MembershipInfo;
import com.luodefei.memorycards.server.dto.MembershipDtos.LevelResponse;
import com.luodefei.memorycards.server.dto.MembershipDtos.SubscribeRequest;
import com.luodefei.memorycards.server.dto.MembershipDtos.SubscribeResponse;
import com.luodefei.memorycards.server.security.AuthUser;
import com.luodefei.memorycards.server.service.MembershipService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/membership")
public class MembershipController {

    private final MembershipService membershipService;

    public MembershipController(MembershipService membershipService) {
        this.membershipService = membershipService;
    }

    @GetMapping("/levels")
    public List<LevelResponse> levels() {
        return membershipService.levels();
    }

    @PostMapping("/subscribe")
    public SubscribeResponse subscribe(@AuthenticationPrincipal AuthUser user,
                                       @Valid @RequestBody SubscribeRequest request) {
        return membershipService.subscribe(user.id(), request.levelCode());
    }

    @GetMapping("/current")
    public ResponseEntity<MembershipInfo> current(@AuthenticationPrincipal AuthUser user) {
        return membershipService.currentInfo(user.id())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
