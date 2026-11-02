package com.luodefei.memorycards.server.model;

public record User(long id, String username, String password, String nickname, int points, long createdAt) {
}
