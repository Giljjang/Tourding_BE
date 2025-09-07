package com.example.tourding.user.service;

import com.example.tourding.direction.entity.RouteSummary;
import com.example.tourding.direction.repository.RouteSummaryRepository;
import com.example.tourding.direction.service.RouteService;
import com.example.tourding.user.dto.request.UserCreateReqDto;
import com.example.tourding.user.dto.request.UserUpdateReqDto;
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

    public UserResponseDto register(UserCreateReqDto userCreateReqDto) {
        User user = new User(userCreateReqDto.getUsername(), userCreateReqDto.getPassword(), userCreateReqDto.getEmail());
        return toDto(userRepository.save(user));
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
        Optional<RouteSummary> routeSummaryOpt = routeSummaryRepository.findRouteSummaryByUserId(id);
        routeSummaryOpt.ifPresent(summary -> routeService.deleteUserRoute(summary.getId(), user));

        userRepository.delete(user);
    }

    private UserResponseDto toDto(User user) {
        return new UserResponseDto(user.getId(), user.getUsername(), user.getEmail());
    }
}
