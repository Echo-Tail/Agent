package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.*;
import cafe.snails.ecomagents.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器，处理登录 / 注册 / 登出。
 */
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    /** 用户登录 */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return userService.login(request);
    }

    /** 用户注册（需邀请码） */
    @PostMapping("/register")
    public ApiResponse<UserDTO> register(@Valid @RequestBody RegisterRequest request) {
        return userService.register(request);
    }

    /**
     * 用户登出。
     * JWT 为无状态，实际登出由客户端清除 token 完成，
     * 此端点预留用于审计日志和未来 token 黑名单 / refresh token 吊销。
     */
    @PostMapping("/logout")
    public ApiResponse<?> logout(@RequestHeader(value = "Authorization", required = false) String token) {
        return ApiResponse.success(null);
    }
}
