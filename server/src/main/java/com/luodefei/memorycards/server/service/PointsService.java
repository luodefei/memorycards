package com.luodefei.memorycards.server.service;

import com.luodefei.memorycards.server.dto.PointsDtos.ActionResponse;
import com.luodefei.memorycards.server.exception.ApiException;
import com.luodefei.memorycards.server.model.User;
import com.luodefei.memorycards.server.repository.PointsRepository;
import com.luodefei.memorycards.server.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;

@Service
public class PointsService {

    static final int CHECKIN_REWARD = 10;
    static final int SHARE_REWARD = 5;
    static final int SHARE_DAILY_LIMIT = 3;
    static final Set<Integer> PURCHASE_PACKS = Set.of(100, 500, 1000);

    private final PointsRepository pointsRepository;
    private final UserRepository userRepository;

    public PointsService(PointsRepository pointsRepository, UserRepository userRepository) {
        this.pointsRepository = pointsRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ActionResponse checkin(long userId) {
        long startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        if (pointsRepository.countByUserTypeSince(userId, "CHECKIN", startOfDay) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "今天已签到，明天再来吧");
        }
        return grant(userId, CHECKIN_REWARD, "CHECKIN", "每日签到");
    }

    @Transactional
    public ActionResponse share(long userId) {
        long startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        if (pointsRepository.countByUserTypeSince(userId, "SHARE", startOfDay) >= SHARE_DAILY_LIMIT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "今日分享奖励已达上限（" + SHARE_DAILY_LIMIT + " 次）");
        }
        return grant(userId, SHARE_REWARD, "SHARE", "分享领积分");
    }

    @Transactional
    public ActionResponse purchase(long userId, Integer points) {
        if (points == null || !PURCHASE_PACKS.contains(points)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "积分套餐无效，可选：" + PURCHASE_PACKS);
        }
        return grant(userId, points, "PURCHASE", "购买 " + points + " 积分");
    }

    private ActionResponse grant(long userId, int amount, String type, String remark) {
        pointsRepository.insert(userId, amount, type, remark);
        userRepository.addPoints(userId, amount);
        int total = userRepository.findById(userId).map(User::points).orElse(0);
        return new ActionResponse(amount, total, remark);
    }
}
