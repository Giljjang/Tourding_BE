package com.example.tourding.user.service;

import com.example.tourding.ai.entity.AiRouteRequest;
import com.example.tourding.ai.repository.AiRouteCandidateRepository;
import com.example.tourding.ai.repository.AiRouteRequestRepository;
import com.example.tourding.direction.repository.RouteSummaryHistoryRepository;
import com.example.tourding.direction.repository.RouteSummaryRepository;
import com.example.tourding.user.entity.User;
import com.example.tourding.user.repository.UserRepository;
import com.example.tourding.user.repository.UserRidingProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock
    private UserRepository userRepository;
    @Mock
    private RouteSummaryRepository routeSummaryRepository;
    @Mock
    private RouteSummaryHistoryRepository routeSummaryHistoryRepository;
    @Mock
    private UserRidingProfileRepository userRidingProfileRepository;
    @Mock
    private AiRouteRequestRepository aiRouteRequestRepository;
    @Mock
    private AiRouteCandidateRepository aiRouteCandidateRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void deleteUserDeletesChildRowsBeforeUser() {
        User user = new User("delete-user", "password", "delete@example.com");
        ReflectionTestUtils.setField(user, "id", 10L);

        AiRouteRequest aiRouteRequest = AiRouteRequest.builder().user(user).build();
        ReflectionTestUtils.setField(aiRouteRequest, "id", 20L);

        when(userRepository.existsById(10L)).thenReturn(true);
        when(aiRouteRequestRepository.findByUserId(10L)).thenReturn(List.of(aiRouteRequest));

        userService.deleteUser(10L);

        InOrder inOrder = inOrder(
                aiRouteCandidateRepository,
                aiRouteRequestRepository,
                routeSummaryHistoryRepository,
                routeSummaryRepository,
                userRidingProfileRepository,
                userRepository
        );
        inOrder.verify(aiRouteCandidateRepository).deleteAllByAiRouteRequestIdIn(List.of(20L));
        inOrder.verify(aiRouteRequestRepository).deleteAllByUserId(10L);
        inOrder.verify(routeSummaryHistoryRepository).deleteAllByUserId(10L);
        inOrder.verify(routeSummaryRepository).deleteAllByUserId(10L);
        inOrder.verify(userRidingProfileRepository).deleteByUserId(10L);
        inOrder.verify(userRepository).deleteByIdDirect(10L);
    }
}
