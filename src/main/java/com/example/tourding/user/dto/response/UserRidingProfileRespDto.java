package com.example.tourding.user.dto.response;

import com.example.tourding.direction.dto.RouteOptionDto;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserRidingProfileRespDto {
    private Long userId;
    private RouteOptionDto routeOption;
}
