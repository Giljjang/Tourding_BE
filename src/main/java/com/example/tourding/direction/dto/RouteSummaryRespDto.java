package com.example.tourding.direction.dto;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder

public class RouteSummaryRespDto { // 경로 출발, 도착 시간만 들어오도록 초 / 미터단위로

    private Boolean isUsed;
    private Double duration;
    private Double distance;

}
