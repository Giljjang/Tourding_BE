package com.example.tourding.user.controller;

import com.example.tourding.external.apple.service.AppleAuthService;
import com.example.tourding.user.dto.request.UserCreateReqDto;
import com.example.tourding.user.dto.request.UserUpdateReqDto;
import com.example.tourding.user.dto.response.UserResponseDto;
import com.example.tourding.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/user")
@Tag(name = "User API", description = "사용자 관리 API")
public class UserController {

    private final UserService userService;
    private final AppleAuthService appleAuthService;

    @Operation(summary = "사용자 생성", description = "새로운 사용자를 등록합니다.")
    @PostMapping("/create")
    public ResponseEntity<UserResponseDto> createUser(@RequestBody UserCreateReqDto userCreateReqDto) {
        return ResponseEntity.ok(userService.register(userCreateReqDto));
    }

    @Operation(summary = "사용자 조회", description = "ID를 통해 특정 사용자를 조회합니다.")
    @GetMapping
    public ResponseEntity<UserResponseDto> getUser(@RequestParam Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @Operation(summary = "전체 사용자 조회", description = "등록된 모든 사용자를 조회합니다.")
    @GetMapping("/all")
    public ResponseEntity<List<UserResponseDto>> getAllUsers() {
        return ResponseEntity.ok(userService.findAllUsers());
    }

    @Operation(summary = "사용자 정보 수정", description = "ID를 기준으로 사용자의 정보를 수정합니다.")
    @PutMapping("/update")
    public ResponseEntity<UserResponseDto> updateUser(
            @RequestParam Long id,
            @RequestBody UserUpdateReqDto userUpdateReqDto
    ) {
        return ResponseEntity.ok(userService.updateUser(id, userUpdateReqDto));
    }

    @Operation(summary = "사용자 삭제", description = "ID를 기준으로 사용자를 삭제합니다.")
    @DeleteMapping("/delete")
    public ResponseEntity<Void> deleteUser(@RequestParam Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/revoke")
    public ResponseEntity<String> revokeUser(@RequestParam Long userId, @RequestParam("authorizationCode") String authorizationCode) {
        try {
            appleAuthService.revoke(authorizationCode);

            userService.deleteUser(userId);

            return ResponseEntity.ok("탈퇴 완료");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("회원 탈퇴 실패" + e.getMessage());
        }
    }
}
