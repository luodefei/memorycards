package com.luodefei.memorycards.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class ResearchDtos {

    private ResearchDtos() {
    }

    public record DeepResearchRequest(
            @NotBlank @Size(max = 2000) String front,
            @Size(max = 4000) String back
    ) {
    }

    public record DeepResearchResponse(String result) {
    }
}
