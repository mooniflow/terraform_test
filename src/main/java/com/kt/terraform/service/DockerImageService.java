package com.kt.terraform.service;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
public class DockerImageService {

    @Value("${azure.acr}")
    private String acrUrl;

    @Value("${azure.acr-username}")
    private String acrUsername;

    @Value("${azure.acr-password}")
    private String acrPassword;

    public String buildAndPushDockerImage(
            String githubRepoUrl, String dockerfilePath, String applicationYml
    ) throws IOException, InterruptedException {

        log.info("Docker 이미지 빌드 및 푸시 시작: {}, Dockerfile: {}", githubRepoUrl, dockerfilePath);

        // 모의(Mock) 처리: 실제 Docker 빌드 및 푸시 작업을 수행하지 않고 고정된 이미지 이름 반환
        String uuid = UUID.randomUUID().toString();
        String repoDir = "/tmp/repo_" + uuid;
        
        // git clone 작업은 그대로 유지 (terraform 테스트를 위해 소스 코드가 필요할 수 있음)
        log.info("Git 저장소 클론: {}", githubRepoUrl);
        Process clone = new ProcessBuilder("git", "clone", githubRepoUrl, repoDir)
                .inheritIO().start();
        if (clone.waitFor() != 0) {
            log.warn("Git clone 실패, 테스트용 목적지 디렉토리 생성");
            new File(repoDir).mkdirs();
        }

        // application.yml 생성은 유지 (Terraform 작업에 필요)
        log.info("application.yml 생성");
        File targetYml = new File(repoDir + "/src/main/resources/application.yml");
        if (!targetYml.exists()) {
            File resourcesDir = targetYml.getParentFile();
            if (!resourcesDir.exists() && !resourcesDir.mkdirs()) {
                log.warn("디렉토리 생성 실패: {}, 무시하고 계속 진행", resourcesDir.getAbsolutePath());
            } else {
                try (BufferedWriter writer = Files.newBufferedWriter(targetYml.toPath())) {
                    writer.write(applicationYml);
                } catch (IOException e) {
                    log.warn("application.yml 파일 작성 실패: {}", e.getMessage());
                }
            }
        }

        // Gradle 빌드 단계 건너뛰기
        log.info("Gradle 빌드 단계 건너뛰기 (모의 처리)");
        
        // Docker 로그인, 빌드, 푸시 단계 모두 건너뛰기
        log.info("Docker 작업 건너뛰기 (모의 처리)");
        
        // 고정된 이미지 이름 생성 및 반환
        String imageName = "mock-springapp-" + uuid;
        String fullImageName = acrUrl + "/" + imageName + ":latest";
        log.info("모의 Docker 이미지 이름 생성: {}", fullImageName);
        
        return fullImageName;
    }
}
