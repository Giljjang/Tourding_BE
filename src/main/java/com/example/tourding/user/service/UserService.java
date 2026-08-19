package com.example.tourding.user.service;

import com.example.tourding.ai.entity.UserRidingProfile;
import com.example.tourding.ai.repository.UserRidingProfileRepository;
import com.example.tourding.direction.dto.RouteOptionDto;
import com.example.tourding.direction.entity.RouteSummary;
import com.example.tourding.direction.repository.RouteSummaryRepository;
import com.example.tourding.direction.service.RouteService;
import com.example.tourding.user.dto.request.UserCreateReqDto;
import com.example.tourding.user.dto.request.UserRidingProfileUpdateReqDto;
import com.example.tourding.user.dto.request.UserUpdateReqDto;
import com.example.tourding.user.dto.response.UserRidingProfileRespDto;
import com.example.tourding.user.dto.response.UserResponseDto;
import com.example.tourding.user.entity.User;
import com.example.tourding.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional

public class UserService implements UserServiceImpl{

    private final UserRepository userRepository;
    private final RouteSummaryRepository routeSummaryRepository;
    private final RouteService routeService;
    private final UserRidingProfileRepository userRidingProfileRepository;

    public UserResponseDto register(UserCreateReqDto userCreateReqDto) {
        Optional<User> checkUser = userRepository.findByUsername(userCreateReqDto.getUsername());

        if (checkUser.isPresent()) {
            ensureDefaultRidingProfile(checkUser.get());
            return toDto(checkUser.get());
        }
        User user = new User(userCreateReqDto.getUsername(), userCreateReqDto.getPassword(), userCreateReqDto.getEmail());
        User savedUser = userRepository.save(user);
        ensureDefaultRidingProfile(savedUser);
        return toDto(savedUser);
    }

    public UserResponseDto getUserById(Long id) {
        User user = userRepository.findById(id).orElseThrow(EntityNotFoundException::new);
        return new UserResponseDto(user.getId(), user.getUsername(), user.getEmail());
    }

    @Transactional(readOnly = true)
    public User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("[Find Error] 유저를 찾을 수 없음"));
    }

    @Transactional(readOnly = true)
    public List<UserResponseDto> findAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public UserResponseDto updateUser(Long id, UserUpdateReqDto userUpdateDto) {
        User user = getUser(id);
        user.setUserInfo(userUpdateDto.getUsername(), userUpdateDto.getPassword(), userUpdateDto.getEmail());

        User updateUser = userRepository.save(user);

        return toDto(updateUser);
    }

    @Transactional
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("유저 찾기 실패 : " + id));
        Optional<RouteSummary> routeSummaryTrue = routeSummaryRepository.findRouteSummaryByUserIdAndIsUsed(id,true);
        routeSummaryTrue.ifPresent(summary -> routeService.deleteUserRoute(summary.getId(), user));

        Optional<RouteSummary> routeSummaryFalse = routeSummaryRepository.findRouteSummaryByUserIdAndIsUsed(id,false);
        routeSummaryFalse.ifPresent(summary -> routeService.deleteUserRoute(summary.getId(), user));

        userRepository.delete(user);
    }

    @Transactional
    public UserRidingProfileRespDto updateRidingProfile(Long userId, UserRidingProfileUpdateReqDto requestDto) {
        User user = getUser(userId);
        UserRidingProfile profile = userRidingProfileRepository.findByUserId(userId)
                .orElse(UserRidingProfile.builder().user(user).build());

        applyRouteOption(profile, requestDto == null ? null : requestDto.getRouteOption());

        return toRidingProfileDto(userRidingProfileRepository.save(profile));
    }

    @Transactional(readOnly = true)
    public UserRidingProfileRespDto getRidingProfile(Long userId) {
        User user = getUser(userId);
        UserRidingProfile profile = userRidingProfileRepository.findByUserId(userId)
                .orElseGet(() -> UserRidingProfile.builder().user(user).build());
        return toRidingProfileDto(profile);
    }

    private void ensureDefaultRidingProfile(User user) {
        if (userRidingProfileRepository.findByUserId(user.getId()).isPresent()) {
            return;
        }
        UserRidingProfile profile = UserRidingProfile.builder()
                .user(user)
                .cyclingProfile("cycling-regular")
                .fastRoute(true)
                .avoidSteps(true)
                .avoidFords(true)
                .skillLevel("BEGINNER")
                .avoidHills(false)
                .preferPaved(true)
                .preferBikeRoad(true)
                .avoidMainRoad(false)
                .build();
        userRidingProfileRepository.save(profile);
    }

    private UserResponseDto toDto(User user) {
        return new UserResponseDto(user.getId(), user.getUsername(), user.getEmail());
    }

    private UserRidingProfileRespDto toRidingProfileDto(UserRidingProfile profile) {
        return UserRidingProfileRespDto.builder()
                .userId(profile.getUser().getId())
                .routeOption(RouteOptionDto.builder()
                        .cyclingProfile(defaultString(profile.getCyclingProfile(), "cycling-regular"))
                        .fastRoute(defaultBoolean(profile.getFastRoute(), true))
                        .avoidSteps(defaultBoolean(profile.getAvoidSteps(), true))
                        .avoidFords(defaultBoolean(profile.getAvoidFords(), true))
                        .skillLevel(defaultString(profile.getSkillLevel(), "BEGINNER"))
                        .build())
                .build();
    }

    private void applyRouteOption(UserRidingProfile profile, RouteOptionDto routeOption) {
        RouteOptionDto defaults = RouteOptionDto.defaults();
        profile.setCyclingProfile(defaultString(routeOption == null ? null : routeOption.getCyclingProfile(), defaults.getCyclingProfile()));
        profile.setFastRoute(defaultBoolean(routeOption == null ? null : routeOption.getFastRoute(), defaults.getFastRoute()));
        profile.setAvoidSteps(defaultBoolean(routeOption == null ? null : routeOption.getAvoidSteps(), defaults.getAvoidSteps()));
        profile.setAvoidFords(defaultBoolean(routeOption == null ? null : routeOption.getAvoidFords(), defaults.getAvoidFords()));
        profile.setSkillLevel(defaultString(routeOption == null ? null : routeOption.getSkillLevel(), defaults.getSkillLevel()));
        profile.setAvoidHills(false);
        profile.setPreferPaved(true);
        profile.setPreferBikeRoad(true);
        profile.setAvoidMainRoad(false);
    }

    private String defaultString(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private Boolean defaultBoolean(Boolean value, Boolean defaultValue) {
        return value == null ? defaultValue : value;
    }
}
