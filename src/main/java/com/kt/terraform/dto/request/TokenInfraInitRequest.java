package com.kt.terraform.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;

public record TokenInfraInitRequest(
        String githubRepoUrl,
        String applicationYml,
        List<String> environmentVariables,
        String dockerfilePath,
        
        @NotBlank(message = "Azure 액세스 토큰은 필수입니다")
        String accessToken
) {
} 