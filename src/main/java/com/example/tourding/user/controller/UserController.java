package com.example.tourding.user.controller;

import com.example.tourding.external.apple.service.AppleAuthService;
import com.example.tourding.user.dto.request.UserCreateReqDto;
import com.example.tourding.user.dto.request.UserRidingProfileUpdateReqDto;
import com.example.tourding.user.dto.request.UserUpdateReqDto;
import com.example.tourding.user.dto.response.UserRidingProfileRespDto;
import com.example.tourding.user.dto.response.UserResponseDto;
import com.example.tourding.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
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

    @Operation(summary = "온보딩 라이딩 정보 수정", description = "사용자의 자전거 종류, 빠른 코스, 회피옵션, 경사 난이도를 저장합니다.")
    @PutMapping("/{userId}/riding-profile")
    public ResponseEntity<UserRidingProfileRespDto> updateRidingProfile(
            @PathVariable Long userId,
            @RequestBody UserRidingProfileUpdateReqDto requestDto
    ) {
        return ResponseEntity.ok(userService.updateRidingProfile(userId, requestDto));
    }

    @Operation(summary = "라이딩 정보 조회", description = "사용자의 저장된 라이딩 옵션을 조회합니다.")
    @GetMapping("/{userId}/riding-profile")
    public ResponseEntity<UserRidingProfileRespDto> getRidingProfile(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.getRidingProfile(userId));
    }

    @Operation(
            summary = "회원 탈퇴",
            description = "사용자와 사용자에게 연결된 데이터를 삭제합니다."
    )
    @DeleteMapping("/delete")
    public ResponseEntity<Void> deleteUser(@RequestParam Long id) {
        log.info("User deletion requested - userId={}", id);
        userService.deleteUser(id);
        log.info("User deletion completed - userId={}", id);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Apple 회원 탈퇴",
            description = "Apple authorization code를 검증하고 Apple 연결 해제 후 Tourding 내부 데이터를 삭제합니다."
    )
    @PostMapping("/revoke")
    public ResponseEntity<String> revokeUser(
            @RequestParam Long userId,
            @RequestParam("authorizationCode") String authorizationCode
    ) throws Exception {
        log.info("Apple user deletion requested - userId={}", userId);
        appleAuthService.revoke(authorizationCode);
        userService.deleteUser(userId);
        log.info("Apple user deletion completed - userId={}", userId);
        return ResponseEntity.ok("탈퇴 완료");
    }
}
