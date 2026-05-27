package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 公开用户信息查询（无需 ADMIN 角色）。
 */
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class PublicUserController {

    private final UserRepository userRepository;

    /** 获取用户基本信息（用户名），任何已认证用户可访问 */
    @GetMapping("/user/{id}")
    public ApiResponse<Map<String, Object>> getUserInfo(@PathVariable Long id) {
        return userRepository.findById(id)
                .map(user -> ApiResponse.success(Map.of(
                        "id", user.getId(),
                        "username", user.getUsername()
                )))
                .orElse(ApiResponse.error(404, "用户不存在"));
    }
}
