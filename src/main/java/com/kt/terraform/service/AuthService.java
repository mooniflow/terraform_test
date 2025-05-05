package com.kt.terraform.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /**
     * Azure CLI를 사용하여 Azure 액세스 토큰을 가져옵니다.
     * 조건부 액세스 정책 오류가 발생할 경우 다양한 스코프와 방법으로 재시도합니다.
     * 
     * @return Azure 액세스 토큰
     * @throws IOException 프로세스 실행 중 I/O 오류 발생 시
     * @throws InterruptedException 프로세스가 중단될 경우
     */
    public String getAzureAccessToken() throws IOException, InterruptedException {
        String accessToken = null;
        
        try {
            // 1. 먼저 Graph API 스코프로 토큰 획득 시도 (조건부 액세스 정책 우회 가능성)
            log.info("Graph API 스코프로 토큰 획득 시도");
            accessToken = executeAzCliCommand("az account get-access-token --scope https://graph.microsoft.com//.default --query accessToken -o tsv");
            log.info("Graph API 스코프 토큰 획득 성공, Management API 스코프 토큰 요청...");
            
            // 2. Graph API 스코프 획득 후 Management API 스코프 토큰 요청
            accessToken = executeAzCliCommand("az account get-access-token --scope https://management.azure.com//.default --query accessToken -o tsv");
            log.info("Azure Management API 액세스 토큰이 성공적으로 획득되었습니다.");
        } catch (IOException | InterruptedException e) {
            log.error("스코프 지정 토큰 획득 실패: {}", e.getMessage());
            
            if (e.getMessage() != null && 
                (e.getMessage().contains("AADSTS53003") || 
                 e.getMessage().contains("AADSTS50079") ||
                 e.getMessage().contains("AADSTS50076") ||
                 e.getMessage().contains("Access has been blocked by Conditional Access policies"))) {
                
                log.warn("조건부 액세스 정책으로 인한 오류가 발생했습니다.");
                log.warn("사용자는 다음 명령어를 순서대로 실행해야 합니다:");
                log.warn("1. az account clear");
                log.warn("2. rm -rf ~/.azure");
                log.warn("3. az login --scope https://graph.microsoft.com//.default");
                log.warn("4. az account get-access-token --scope https://management.azure.com//.default");
                
                throw new IOException("조건부 액세스 정책으로 인해 인증이 차단되었습니다. 터미널에서 다음 명령을 실행하세요:\n" +
                        "1. az account clear\n" +
                        "2. rm -rf ~/.azure\n" +
                        "3. az login --scope https://graph.microsoft.com//.default\n" +
                        "4. 로그인 후 다시 시도하세요.", e);
            }
            
            try {
                // CLI 캐시를 강제로 비우고 재시도
                log.info("Azure CLI 캐시 초기화 시도");
                executeAzCliCommand("az account clear");
                
                // 일반 방식으로 토큰 획득 시도
                log.info("캐시 초기화 후 토큰 획득 재시도");
                accessToken = executeAzCliCommand("az account get-access-token --query accessToken -o tsv");
            } catch (IOException | InterruptedException ex) {
                log.error("모든 인증 시도 실패", ex);
                throw new IOException("Azure 액세스 토큰을 획득할 수 없습니다. 터미널에서 'az login --scope https://graph.microsoft.com//.default'를 실행한 후 다시 시도하세요.", ex);
            }
        }
        
        if (accessToken == null || accessToken.trim().isEmpty()) {
            throw new IOException("Azure 액세스 토큰을 획득할 수 없습니다. Azure CLI로 로그인되어 있는지 확인하세요.");
        }
        
        return accessToken;
    }
    
    /**
     * Azure CLI 명령을 실행하고 결과를 반환합니다.
     */
    private String executeAzCliCommand(String command) throws IOException, InterruptedException {
        log.debug("Azure CLI 명령 실행: {}", command);
        Process process = Runtime.getRuntime().exec(command);
        
        // 표준 출력 읽기
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }
        }
        
        // 오류 출력 읽기
        StringBuilder error = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                error.append(line).append("\n");
            }
        }
        
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            log.error("Azure CLI 명령 실행 실패 (종료 코드: {}): {}", exitCode, error);
            throw new IOException("Azure CLI 명령 실행 실패: " + error);
        }
        
        return output.toString().trim();
    }
} 