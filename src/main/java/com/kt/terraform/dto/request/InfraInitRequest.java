package com.kt.terraform.dto.request;

import java.util.List;

public record InfraInitRequest(
        String githubRepoUrl,
        String applicationYml,
        List<String> environmentVariables,
        String dockerfilePath
) {
} 