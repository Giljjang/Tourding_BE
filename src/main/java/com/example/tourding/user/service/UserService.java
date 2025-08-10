package com.example.tourding.user.service;

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

@Service
@RequiredArgsConstructor
@Transactional

public class UserService implements UserServiceImpl{

    private final UserRepository userRepository;

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

    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new EntityNotFoundException("[Delete Error] 유저를 찾을 수 없습니다. " + id);
        }
        userRepository.deleteById(id);
    }

    private UserResponseDto toDto(User user) {
        return new UserResponseDto(user.getId(), user.getUsername(), user.getEmail());
    }
}
