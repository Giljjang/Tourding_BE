package com.example.tourding.user.dto.request;

import com.example.tourding.direction.dto.RouteOptionDto;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserRidingProfileUpdateReqDto {
    private RouteOptionDto routeOption;
}
