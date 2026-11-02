package com.luodefei.memorycards.server.controller;

import com.luodefei.memorycards.server.dto.PointsDtos.ActionResponse;
import com.luodefei.memorycards.server.dto.PointsDtos.PurchaseRequest;
import com.luodefei.memorycards.server.security.AuthUser;
import com.luodefei.memorycards.server.service.PointsService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/points")
public class PointsController {

    private final PointsService pointsService;

    public PointsController(PointsService pointsService) {
        this.pointsService = pointsService;
    }

    @PostMapping("/checkin")
    public ActionResponse checkin(@AuthenticationPrincipal AuthUser user) {
        return pointsService.checkin(user.id());
    }

    @PostMapping("/share")
    public ActionResponse share(@AuthenticationPrincipal AuthUser user) {
        return pointsService.share(user.id());
    }

    @PostMapping("/purchase")
    public ActionResponse purchase(@AuthenticationPrincipal AuthUser user,
                                   @Valid @RequestBody PurchaseRequest request) {
        return pointsService.purchase(user.id(), request.points());
    }
}
