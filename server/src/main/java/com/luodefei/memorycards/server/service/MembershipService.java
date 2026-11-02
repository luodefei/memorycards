package com.luodefei.memorycards.server.service;

import com.luodefei.memorycards.server.dto.AuthDtos.MembershipInfo;
import com.luodefei.memorycards.server.dto.MembershipDtos.LevelResponse;
import com.luodefei.memorycards.server.dto.MembershipDtos.PointsTransaction;
import com.luodefei.memorycards.server.dto.MembershipDtos.SubscribeResponse;
import com.luodefei.memorycards.server.exception.ApiException;
import com.luodefei.memorycards.server.model.Membership;
import com.luodefei.memorycards.server.model.MembershipLevel;
import com.luodefei.memorycards.server.model.User;
import com.luodefei.memorycards.server.repository.MembershipLevelRepository;
import com.luodefei.memorycards.server.repository.MembershipRepository;
import com.luodefei.memorycards.server.repository.PointsRepository;
import com.luodefei.memorycards.server.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class MembershipService {

    private static final long DAY_MILLIS = 24 * 60 * 60 * 1000L;

    private final MembershipLevelRepository levelRepository;
    private final MembershipRepository membershipRepository;
    private final PointsRepository pointsRepository;
    private final UserRepository userRepository;

    public MembershipService(MembershipLevelRepository levelRepository,
                             MembershipRepository membershipRepository,
                             PointsRepository pointsRepository,
                             UserRepository userRepository) {
        this.levelRepository = levelRepository;
        this.membershipRepository = membershipRepository;
        this.pointsRepository = pointsRepository;
        this.userRepository = userRepository;
    }

    public List<LevelResponse> levels() {
        return levelRepository.findAll().stream().map(this::toLevelResponse).toList();
    }

    @Transactional
    public SubscribeResponse subscribe(long userId, String levelCode) {
        MembershipLevel level = levelRepository.findByCode(levelCode)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "会员等级不存在"));

        long now = System.currentTimeMillis();
        long startAt = membershipRepository.findLatestByUser(userId)
                .filter(m -> m.endAt() > now)
                .map(Membership::endAt)
                .orElse(now);
        long endAt = startAt + level.durationDays() * DAY_MILLIS;

        long membershipId = membershipRepository.insert(
                userId, level.id(), startAt, endAt, level.bonusPoints());
        userRepository.addPoints(userId, level.bonusPoints());
        pointsRepository.insert(userId, level.bonusPoints(), "MEMBERSHIP_BONUS",
                "开通" + level.name() + "赠送积分");

        int points = userRepository.findById(userId).map(User::points).orElse(0);
        return new SubscribeResponse(membershipId, level.code(), level.name(),
                startAt, endAt, level.bonusPoints(), points);
    }

    public Optional<MembershipInfo> currentInfo(long userId) {
        return membershipRepository.findLatestByUser(userId).flatMap(membership ->
                levelRepository.findById(membership.levelId())
                        .map(level -> new MembershipInfo(
                                level.code(), level.name(), membership.startAt(),
                                membership.endAt(), membership.endAt() > System.currentTimeMillis(),
                                membership.bonusPoints())));
    }

    public List<PointsTransaction> transactions(long userId) {
        return pointsRepository.findByUser(userId, 50);
    }

    private LevelResponse toLevelResponse(MembershipLevel level) {
        return new LevelResponse(level.id(), level.code(), level.name(), level.priceCents(),
                level.durationDays(), level.bonusPoints(), level.description());
    }
}
