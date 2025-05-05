package com.kt.terraform.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kt.terraform.dto.ResponseTemplate;
import com.kt.terraform.dto.request.InfraInitRequest;
import com.kt.terraform.dto.request.TokenInfraInitRequest;
import com.kt.terraform.exception.errorcode.GlobalErrorCode;
import com.kt.terraform.exception.response.ErrorResponse;
import com.kt.terraform.service.TerraformService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Tag(name = "Terraform", description = "Terraform 관련 API")
@Slf4j
@RequiredArgsConstructor
@RequestMapping
@RestController
public class TerraformController {

    private final TerraformService terraformService;

    @Operation(summary = "테스트 api", description = "테스트 api")
    @GetMapping
    public ResponseEntity<ResponseTemplate<?>> test(
            HttpServletRequest request
    ) {
        Long userId = Long.parseLong(request.getHeader("User-Id"));

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ResponseTemplate.from(userId));
    }

    @Operation(summary = "인프라 배포 API", description = "Terraform을 사용하여 인프라를 배포합니다.")
    @PostMapping("/deploy")
    public ResponseEntity<?> deploy(
        @RequestBody InfraInitRequest request
    ) {
        try {
            log.info("인프라 구축 요청: {}", request.githubRepoUrl());
            terraformService.createInfrastructure(request);
            return ResponseEntity.ok(ResponseTemplate.from("Infrastructure creation started"));
        } catch (Exception e) {
            log.error("인프라 구축 중 오류 발생", e);
            return ResponseEntity.internalServerError().body(
                ErrorResponse.builder()
                    .isSuccess(false)
                    .code(GlobalErrorCode.INTERNAL_SERVER_ERROR.name())
                    .message(e.getMessage())
                    .results(new ErrorResponse.ValidationErrors(null))
                    .build()
            );
        }
    }

    @Operation(summary = "토큰 기반 인프라 배포 API", description = "사용자의 Azure 토큰을 사용하여 인프라를 배포합니다.")
    @PostMapping("/deploy-with-token")
    public ResponseEntity<?> deployWithToken(
        @RequestBody TokenInfraInitRequest request
    ) {
        try {
            log.info("토큰 기반 인프라 구축 요청: {}", request.githubRepoUrl());
            terraformService.createInfrastructureWithToken(request);
            return ResponseEntity.ok(ResponseTemplate.from("Token-based infrastructure creation started"));
        } catch (Exception e) {
            log.error("토큰 기반 인프라 구축 중 오류 발생", e);
            return ResponseEntity.internalServerError().body(
                ErrorResponse.builder()
                    .isSuccess(false)
                    .code(GlobalErrorCode.INTERNAL_SERVER_ERROR.name())
                    .message(e.getMessage())
                    .results(new ErrorResponse.ValidationErrors(null))
                    .build()
            );
        }
    }
}
