package com.kt.terraform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.azure.core.credential.TokenCredential;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.AzureCliCredentialBuilder;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.resourcemanager.AzureResourceManager;

@Configuration
public class AzureConfig {

    @Value("${azure.subscription-id}")
    private String subscriptionId;
    
    @Value("${azure.use-cli-auth:true}")
    private boolean useCliAuth;

    @Bean
    public TokenCredential tokenCredential() {
        if (useCliAuth) {
            // CLI 인증 명시적 사용
            return new AzureCliCredentialBuilder().build();
        } else {
            // 기본 순서대로 인증 시도 (CLI, ENV, VS Code 순)
            return new DefaultAzureCredentialBuilder().build();
        }
    }

    @Bean
    public AzureResourceManager azureResourceManager(TokenCredential tokenCredential) {
        AzureProfile profile = new AzureProfile(AzureEnvironment.AZURE); // ✔️ 자동 추론
        return AzureResourceManager
                .authenticate(tokenCredential, profile)
                .withSubscription(subscriptionId);
    }
}
