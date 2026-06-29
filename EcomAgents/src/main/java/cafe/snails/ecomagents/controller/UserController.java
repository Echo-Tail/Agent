package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.UserDTO;
import cafe.snails.ecomagents.security.CurrentUserId;
import cafe.snails.ecomagents.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户管理控制器，支持用户列表查看、状态管理。
 */
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** 获取所有用户 */
    @GetMapping("/users")
    public ApiResponse<List<UserDTO>> listUsers() {
        return userService.listUsers();
    }

    /** 按用户名搜索用户 */
    @GetMapping("/users/search")
    public ApiResponse<List<UserDTO>> searchUsers(@RequestParam("keyword") String keyword) {
        return userService.searchUsers(keyword);
    }

    /** 获取单个用户 */
    @GetMapping("/users/{id}")
    public ApiResponse<UserDTO> getUser(@PathVariable("id") Long id) {
        return userService.getUser(id);
    }

    /** 更新用户状态 */
    @PutMapping("/users/{id}/status")
    public ApiResponse<UserDTO> updateStatus(@PathVariable("id") Long id, @RequestBody Map<String, String> body,
                                              @CurrentUserId Long currentUserId) {
        return userService.updateStatus(id, body.get("status"), currentUserId);
    }

    /** 切换用户启用/禁用状态 */
    @PostMapping("/users/{id}/toggle")
    public ApiResponse<UserDTO> toggleStatus(@PathVariable("id") Long id, @CurrentUserId Long currentUserId) {
        return userService.toggleStatus(id, currentUserId);
    }
}
