package com.kt.terraform.controller;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kt.terraform.dto.ResponseTemplate;
import com.kt.terraform.exception.errorcode.GlobalErrorCode;
import com.kt.terraform.exception.response.ErrorResponse;
import com.kt.terraform.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Tag(name = "Auth", description = "인증 관련 API")
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Azure 액세스 토큰 획득", description = "Azure CLI를 통해 액세스 토큰을 획득합니다.")
    @GetMapping("/azure-token")
    public ResponseEntity<?> getAzureToken() {
        try {
            String accessToken = authService.getAzureAccessToken();
            return ResponseEntity.ok(ResponseTemplate.from(accessToken));
        } catch (IOException e) {
            log.error("Azure 토큰 획득 중 IO 오류 발생", e);
            
            // 조건부 액세스 정책 관련 오류 확인
            if (e.getMessage() != null && 
                (e.getMessage().contains("조건부 액세스 정책") || 
                 e.getMessage().contains("Conditional Access") ||
                 e.getMessage().contains("AADSTS"))) {
                
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ErrorResponse.builder()
                                .isSuccess(false)
                                .code("AZURE_AUTH_CONDITIONAL_ACCESS_ERROR")
                                .message("Azure 조건부 액세스 정책 오류: 다음 명령어를 터미널에서 실행하고 다시 시도하세요.\n" +
                                         "1. az account clear\n" +
                                         "2. rm -rf ~/.azure\n" +
                                         "3. az login --scope https://graph.microsoft.com//.default\n" +
                                         "4. 로그인 후 다시 시도하세요.")
                                .results(new ErrorResponse.ValidationErrors(null))
                                .build());
            }
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.builder()
                            .isSuccess(false)
                            .code(GlobalErrorCode.INTERNAL_SERVER_ERROR.name())
                            .message("Azure CLI 실행 중 IO 오류: " + e.getMessage())
                            .results(new ErrorResponse.ValidationErrors(null))
                            .build());
        } catch (InterruptedException e) {
            log.error("Azure 토큰 획득 프로세스 중단됨", e);
            Thread.currentThread().interrupt(); // 인터럽트 상태 복원
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.builder()
                            .isSuccess(false)
                            .code(GlobalErrorCode.INTERNAL_SERVER_ERROR.name())
                            .message("Azure CLI 실행이 중단됨: " + e.getMessage())
                            .results(new ErrorResponse.ValidationErrors(null))
                            .build());
        } catch (Exception e) {
            log.error("Azure 토큰 획득 중 오류 발생", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.builder()
                            .isSuccess(false)
                            .code(GlobalErrorCode.INTERNAL_SERVER_ERROR.name())
                            .message("Azure 토큰 획득 실패: " + e.getMessage())
                            .results(new ErrorResponse.ValidationErrors(null))
                            .build());
        }
    }
} 