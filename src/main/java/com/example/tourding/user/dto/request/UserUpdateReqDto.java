package com.example.tourding.user.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor

public class UserUpdateReqDto {
    private String username;
    private String password;
    private String email;
}
