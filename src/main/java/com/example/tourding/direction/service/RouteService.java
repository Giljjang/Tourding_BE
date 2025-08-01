package com.example.tourding.direction.service;

import com.example.tourding.direction.dto.RouteSummaryRespDto;
import com.example.tourding.direction.entity.RouteSummary;
import com.example.tourding.direction.external.ApiRouteResponse;
import com.example.tourding.direction.external.NaverMapClient;
import com.example.tourding.direction.external.RouteMapper;
import com.example.tourding.direction.repository.RouteSummaryRepository;
import com.example.tourding.user.entity.User;
import com.example.tourding.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor

public class RouteService implements RouteServiceImpl {
    private final NaverMapClient naverMapClient;
    private final UserRepository userRepository;
    private final RouteSummaryRepository routeSummaryRepository;

    @Override
    public RouteSummaryRespDto getRoute(Long userId, String start, String end) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("사용자 없음"));

        ApiRouteResponse apiRouteResponse = naverMapClient.getDirection(start, end);

        var tra = apiRouteResponse.getRoute().getTraoptimal().get(0);

        RouteSummaryRespDto routeSummaryRespDto = RouteSummaryRespDto.from(tra);

        RouteSummary summary = RouteMapper.toEntity(routeSummaryRespDto);
        summary.setUser(user);

        if(user.getSummary() != null) {
            routeSummaryRepository.delete(user.getSummary());
        }

        user.setSummary(summary);
        userRepository.save(user);

        return routeSummaryRespDto;
    }
}
