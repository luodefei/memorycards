package com.luodefei.memorycards.server.config;

import com.luodefei.memorycards.server.repository.MembershipLevelRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements ApplicationRunner {

    private final MembershipLevelRepository levelRepository;

    public DataInitializer(MembershipLevelRepository levelRepository) {
        this.levelRepository = levelRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!levelRepository.findAll().isEmpty()) {
            return;
        }
        levelRepository.insert("LITE", "轻享会员", 1900, 30, 100, "每月赠送 100 积分");
        levelRepository.insert("PLUS", "进阶会员", 4900, 90, 400, "每季赠送 400 积分");
        levelRepository.insert("PRO", "尊享会员", 16800, 365, 2000, "每年赠送 2000 积分");
    }
}
