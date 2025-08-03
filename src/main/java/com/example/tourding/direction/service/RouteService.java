package com.example.tourding.direction.service;

import com.example.tourding.direction.dto.RouteGuideRespDto;
import com.example.tourding.direction.dto.RoutePathRespDto;
import com.example.tourding.direction.dto.RouteSectionRespDto;
import com.example.tourding.direction.dto.RouteSummaryRespDto;
import com.example.tourding.direction.entity.RouteGuide;
import com.example.tourding.direction.entity.RoutePath;
import com.example.tourding.direction.entity.RouteSection;
import com.example.tourding.direction.entity.RouteSummary;
import com.example.tourding.direction.external.ApiRouteResponse;
import com.example.tourding.direction.external.NaverMapClient;
import com.example.tourding.direction.external.RouteMapper;
import com.example.tourding.direction.repository.RouteGuideRepository;
import com.example.tourding.direction.repository.RoutePathRepository;
import com.example.tourding.direction.repository.RouteSectionRepository;
import com.example.tourding.direction.repository.RouteSummaryRepository;
import com.example.tourding.user.entity.User;
import com.example.tourding.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor

public class RouteService implements RouteServiceImpl {
    private final NaverMapClient naverMapClient;
    private final UserRepository userRepository;
    private final RouteSummaryRepository routeSummaryRepository;
    private final RouteGuideRepository routeGuideRepository;
    private final RoutePathRepository routePathRepository;
    private final RouteSectionRepository routeSectionRepository;


    @Override
    public RouteSummaryRespDto getRoute(Long userId, String start, String end) {
        System.out.println("조회하려는 userId : " + userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("사용자 없음"));

        ApiRouteResponse apiRouteResponse = naverMapClient.getDirection(start, end);
        if(apiRouteResponse.getRoute() == null) {
            throw new RuntimeException("API 응답에 route가 없음");
        }

        var tra = apiRouteResponse.getRoute().getTraoptimal().get(0);
        if(tra == null) {
            throw new RuntimeException("route안에 traoptimal이 없음");
        }

        RouteSummaryRespDto routeSummaryRespDto = RouteSummaryRespDto.from(tra);
        RouteSummary newSummary = RouteMapper.toEntity(routeSummaryRespDto);
        newSummary.setUser(user);

        if(user.getSummary() != null) {
            updateRouteSummary(user.getSummary().getId(), newSummary);
        } else {
            user.setSummary(newSummary);
            userRepository.save(user);
        }

        return routeSummaryRespDto;
    }

    @Transactional
    public RouteSummary updateRouteSummary(Long userId, RouteSummary routeSummary) {
        RouteSummary existing = routeSummaryRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("RouteSummary not found"));

        // summary 필드 업데이트
        existing.update(routeSummary);

        routeSummary.getRouteGuides().forEach(guide -> guide.setSummary(existing));
        routeSummary.getRouteSections().forEach(section -> section.setSummary(existing));
        routeSummary.getRoutePaths().forEach(path -> path.setSummary(existing));

        // 기존 guide, section, path 컬렉션 클리어 후 새 데이터 세팅
        existing.getRouteGuides().clear();
        existing.getRouteGuides().addAll(routeSummary.getRouteGuides());

        existing.getRouteSections().clear();
        existing.getRouteSections().addAll(routeSummary.getRouteSections());

        existing.getRoutePaths().clear();
        existing.getRoutePaths().addAll(routeSummary.getRoutePaths());

        return routeSummaryRepository.save(existing);
    }

    public List<RouteGuideRespDto> getGuideByUserId(Long userId) {
        List<RouteGuide> routeGuides = routeGuideRepository.findBySummary_User_id(userId);

        return IntStream.range(0, routeGuides.size())
                .mapToObj(i -> RouteGuideRespDto.fromEntity(routeGuides.get(i), i))
                .collect(Collectors.toList());
    }

    public List<RoutePathRespDto> getPathByUserId(Long userId) {
        List<RoutePath> routePaths = routePathRepository.findBySummary_User_id(userId);

        return IntStream.range(0, routePaths.size())
                .mapToObj(i -> RoutePathRespDto.fromEntity(routePaths.get(i), i))
                .collect(Collectors.toList());
    }

    public List<RouteSectionRespDto> getSectionByUserId(Long userId) {
        List<RouteSection> routeSections = routeSectionRepository.findBySummary_User_id(userId);

        return IntStream.range(0, routeSections.size())
                .mapToObj(i -> RouteSectionRespDto.fromEntity(routeSections.get(i), i))
                .collect(Collectors.toList());
    }
}
