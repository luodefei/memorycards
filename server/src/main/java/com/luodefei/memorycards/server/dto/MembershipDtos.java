package com.luodefei.memorycards.server.dto;

import jakarta.validation.constraints.NotBlank;

public final class MembershipDtos {

    private MembershipDtos() {
    }

    public record LevelResponse(
            long id,
            String code,
            String name,
            int priceCents,
            int durationDays,
            int bonusPoints,
            String description
    ) {
    }

    public record SubscribeRequest(@NotBlank String levelCode) {
    }

    public record SubscribeResponse(
            long membershipId,
            String levelCode,
            String levelName,
            long startAt,
            long endAt,
            int grantedPoints,
            int points
    ) {
    }

    public record PointsTransaction(
            long id,
            int amount,
            String type,
            String remark,
            long createdAt
    ) {
    }
}
