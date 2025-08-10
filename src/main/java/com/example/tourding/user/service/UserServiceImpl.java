package com.example.tourding.user.service;

import com.example.tourding.user.dto.request.UserCreateReqDto;
import com.example.tourding.user.dto.response.UserResponseDto;

public interface UserServiceImpl {
    UserResponseDto register(UserCreateReqDto userCreateReqDto);
}
