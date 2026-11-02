package com.luodefei.memorycards.server.dto;

import jakarta.validation.constraints.NotNull;

public final class PointsDtos {

    private PointsDtos() {
    }

    public record PurchaseRequest(@NotNull Integer points) {
    }

    public record ActionResponse(int granted, int points, String remark) {
    }
}
