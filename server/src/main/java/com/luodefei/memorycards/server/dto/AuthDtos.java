package com.luodefei.memorycards.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank
            @Size(min = 3, max = 20)
            @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
            String username,
            @NotBlank
            @Size(min = 6, max = 64)
            String password
    ) {
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password
    ) {
    }

    public record AuthResponse(String token, UserProfile user) {
    }

    public record UserProfile(
            long id,
            String username,
            String nickname,
            int points,
            MembershipInfo membership
    ) {
    }

    public record MembershipInfo(
            String levelCode,
            String levelName,
            long startAt,
            long endAt,
            boolean active,
            int bonusPoints
    ) {
    }
}
