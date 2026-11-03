package com.luodefei.memorycards.server.controller;

import com.luodefei.memorycards.server.dto.ResearchDtos.DeepResearchRequest;
import com.luodefei.memorycards.server.dto.ResearchDtos.DeepResearchResponse;
import com.luodefei.memorycards.server.security.AuthUser;
import com.luodefei.memorycards.server.service.ResearchService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/research")
public class ResearchController {

    private final ResearchService researchService;

    public ResearchController(ResearchService researchService) {
        this.researchService = researchService;
    }

    @PostMapping("/deep")
    public DeepResearchResponse deep(@AuthenticationPrincipal AuthUser user,
                                     @Valid @RequestBody DeepResearchRequest request) {
        return new DeepResearchResponse(researchService.deepResearch(request.front(), request.back()));
    }
}
