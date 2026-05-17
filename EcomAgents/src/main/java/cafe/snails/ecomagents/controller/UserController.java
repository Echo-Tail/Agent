package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.UserDTO;
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

    /** 获取单个用户 */
    @GetMapping("/users/{id}")
    public ApiResponse<UserDTO> getUser(@PathVariable("id") Long id) {
        return userService.getUser(id);
    }

    /** 更新用户状态 */
    @PutMapping("/users/{id}/status")
    public ApiResponse<UserDTO> updateStatus(@PathVariable("id") Long id, @RequestBody Map<String, String> body) {
        return userService.updateStatus(id, body.get("status"));
    }

    /** 切换用户启用/禁用状态 */
    @PostMapping("/users/{id}/toggle")
    public ApiResponse<UserDTO> toggleStatus(@PathVariable("id") Long id) {
        return userService.toggleStatus(id);
    }
}
