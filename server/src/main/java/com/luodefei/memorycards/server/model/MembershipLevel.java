package com.luodefei.memorycards.server.model;

public record MembershipLevel(
        long id,
        String code,
        String name,
        int priceCents,
        int durationDays,
        int bonusPoints,
        String description
) {
}
