package com.example.tourding.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/")
@Tag(name = "Health Check", description = "애플리케이션 상태 확인 API")
public class UserController {

    @GetMapping("/health")
    @Operation(
        summary = "애플리케이션 상태 확인",
        description = "Tourding 애플리케이션이 정상적으로 실행 중인지 확인합니다."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200", 
            description = "애플리케이션 정상 실행",
            content = @Content(
                mediaType = "text/plain",
                schema = @Schema(
                    type = "string",
                    example = "Tourding 애플리케이션이 정상적으로 실행 중입니다."
                )
            )
        )
    })
    public String healthCheck() {
        return "Tourding 애플리케이션이 정상적으로 실행 중입니다.";
    }
}
