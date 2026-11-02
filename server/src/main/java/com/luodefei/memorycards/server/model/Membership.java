package com.luodefei.memorycards.server.model;

public record Membership(
        long id,
        long userId,
        long levelId,
        long startAt,
        long endAt,
        int bonusPoints,
        long createdAt
) {
}
