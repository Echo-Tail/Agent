package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.dto.*;
import cafe.snails.ecomagents.model.User;
import cafe.snails.ecomagents.repository.InviteCodeRepository;
import cafe.snails.ecomagents.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户业务逻辑，包括登录 / 注册 / 管理。
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final InviteCodeRepository inviteCodeRepository;

    /**
     * 用户登录。校验用户名、密码和账号状态，登录成功返回 mock token。
     */
    public ApiResponse<LoginResponse> login(LoginRequest req) {
        var userOpt = userRepository.findByUsername(req.getUsername());
        if (userOpt.isEmpty()) {
            return ApiResponse.error(401, "用户名或密码错误");
        }
        User user = userOpt.get();
        if (!user.getPassword().equals(req.getPassword())) {
            return ApiResponse.error(401, "用户名或密码错误");
        }
        if (!"active".equals(user.getStatus())) {
            return ApiResponse.error(403, "账号已被禁用");
        }
        LoginResponse resp = new LoginResponse();
        resp.setUser(toDTO(user));
        resp.setToken("mock-token-" + user.getId());
        return ApiResponse.success("登录成功", resp);
    }

    /**
     * 用户注册。需提供有效的未使用邀请码，用户名不能重复。
     */
    @Transactional
    public ApiResponse<UserDTO> register(RegisterRequest req) {
        var inviteOpt = inviteCodeRepository.findByCodeAndUsedFalse(req.getInviteCode());
        if (inviteOpt.isEmpty()) {
            return ApiResponse.error(400, "无效或已使用的邀请码");
        }
        if (userRepository.existsByUsername(req.getUsername())) {
            return ApiResponse.error(400, "用户名已存在");
        }

        User user = User.builder()
                .username(req.getUsername())
                .email(req.getEmail() != null ? req.getEmail() : "")
                .password(req.getPassword())
                .role("user")
                .status("active")
                .inviteCode(req.getInviteCode())
                .createdAt(LocalDate.now())
                .build();
        user = userRepository.save(user);

        var invite = inviteOpt.get();
        invite.setUsed(true);
        invite.setUsedBy(req.getUsername());
        invite.setUsedByUserId(user.getId());
        inviteCodeRepository.save(invite);

        return ApiResponse.success("注册成功", toDTO(user));
    }

    /** 获取所有用户列表 */
    public ApiResponse<List<UserDTO>> listUsers() {
        List<UserDTO> dtos = userRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ApiResponse.success(dtos);
    }

    /** 根据 ID 获取单个用户 */
    public ApiResponse<UserDTO> getUser(Long id) {
        return userRepository.findById(id)
                .map(u -> ApiResponse.success(toDTO(u)))
                .orElse(ApiResponse.error(404, "用户不存在"));
    }

    /** 更新用户状态（active / disabled） */
    @Transactional
    public ApiResponse<UserDTO> updateStatus(Long id, String status) {
        return userRepository.findById(id)
                .map(user -> {
                    user.setStatus(status);
                    userRepository.save(user);
                    return ApiResponse.success(toDTO(user));
                })
                .orElse(ApiResponse.error(404, "用户不存在"));
    }

    /** 切换用户启用/禁用状态 */
    @Transactional
    public ApiResponse<UserDTO> toggleStatus(Long id) {
        return userRepository.findById(id)
                .map(user -> {
                    user.setStatus("active".equals(user.getStatus()) ? "disabled" : "active");
                    userRepository.save(user);
                    return ApiResponse.<UserDTO>success(
                            "用户已" + ("active".equals(user.getStatus()) ? "启用" : "禁用"),
                            toDTO(user));
                })
                .orElse(ApiResponse.error(404, "用户不存在"));
    }

    /** 将 User 实体转换为 UserDTO（过滤密码等敏感字段） */
    private UserDTO toDTO(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .status(user.getStatus())
                .inviteCode(user.getInviteCode())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
