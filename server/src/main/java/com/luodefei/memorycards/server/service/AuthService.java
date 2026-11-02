package com.luodefei.memorycards.server.service;

import com.luodefei.memorycards.server.dto.AuthDtos.AuthResponse;
import com.luodefei.memorycards.server.dto.AuthDtos.LoginRequest;
import com.luodefei.memorycards.server.dto.AuthDtos.RegisterRequest;
import com.luodefei.memorycards.server.dto.AuthDtos.UserProfile;
import com.luodefei.memorycards.server.exception.ApiException;
import com.luodefei.memorycards.server.model.User;
import com.luodefei.memorycards.server.repository.UserRepository;
import com.luodefei.memorycards.server.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final MembershipService membershipService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, MembershipService membershipService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.membershipService = membershipService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.username().trim();
        if (userRepository.findByUsername(username).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "用户名已存在");
        }
        long userId = userRepository.insert(username, passwordEncoder.encode(request.password()));
        String token = jwtService.generateToken(userId, username);
        return new AuthResponse(token, profile(userId));
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username().trim())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "用户名或密码错误"));
        if (!passwordEncoder.matches(request.password(), user.password())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        String token = jwtService.generateToken(user.id(), user.username());
        return new AuthResponse(token, profile(user.id()));
    }

    public UserProfile profile(long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "用户不存在"));
        return new UserProfile(user.id(), user.username(), user.nickname(), user.points(),
                membershipService.currentInfo(userId).orElse(null));
    }
}
