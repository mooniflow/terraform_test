package com.kt.terraform.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kt.terraform.dto.request.InfraInitRequest;
import com.kt.terraform.dto.request.TokenInfraInitRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TerraformService {

    @Value("${azure.resource-group}")
    private String resourceGroup;

    @Value("${azure.subscription-id}")
    private String subscriptionId;

    @Value("${azure.location}")
    private String location;

    @Value("${terraform.working-dir:terraform}")
    private String workingDir;

    @Value("${azure.use-cli-auth:true}")
    private boolean useCliAuth;

    @Value("${azure.cli-session-path:${user.home}/.azure}")
    private String azureCliSessionPath;

    private final DockerImageService dockerImageService;

    public void createInfrastructure(InfraInitRequest request) {
        String applicationYml = new String(Base64.getDecoder().decode(request.applicationYml()), StandardCharsets.UTF_8);

        try {
            log.info("Before applicationYml: {}", applicationYml);

            // 1. Parse application.yml
            Yaml yaml = new Yaml();
            Map<String, Object> config;
            try {
                config = yaml.load(applicationYml);
                if (config == null) {
                    throw new RuntimeException("Invalid YAML format: configuration is empty");
                }
            } catch (Exception e) {
                log.error("Failed to parse application.yml: {}", applicationYml);
                throw new RuntimeException("Invalid YAML format: " + e.getMessage(), e);
            }

            log.info("Parsed application.yml: {}", config);
            
            // 2. Create terraform directory
            Path terraformDir = Paths.get(workingDir, "terraform-" + System.currentTimeMillis());
            Files.createDirectories(terraformDir);

            // 3. Copy template files
            copyTemplateFiles(terraformDir);

            // 4. Create terraform.tfvars file and get updated application.yml
            String updatedApplicationYml = createTerraformVars(terraformDir, config, false);

            // 5. Build and push Docker image with updated application.yml
            String fullImageName = dockerImageService.buildAndPushDockerImage(
                request.githubRepoUrl(),
                request.dockerfilePath() != null ? request.dockerfilePath() : "Dockerfile",
                updatedApplicationYml
            );

            log.info("Docker image pushed: {}", fullImageName);

            // 6. Update container_image in terraform.tfvars
            updateContainerImage(terraformDir, fullImageName);

            // 7. Initialize and apply terraform
            executeTerraform(terraformDir);

        } catch (Exception e) {
            log.error("Failed to create infrastructure", e);
            throw new RuntimeException("Failed to create infrastructure", e);
        }
    }

    public void createInfrastructureWithToken(TokenInfraInitRequest request) {
        String applicationYml = new String(Base64.getDecoder().decode(request.applicationYml()), StandardCharsets.UTF_8);
    
        try {
            log.info("Before applicationYml: {}", applicationYml);
    
            // 1. Parse application.yml
            Yaml yaml = new Yaml();
            Map<String, Object> config;
            try {
                config = yaml.load(applicationYml);
                if (config == null) {
                    throw new RuntimeException("Invalid YAML format: configuration is empty");
                }
            } catch (Exception e) {
                log.error("Failed to parse application.yml: {}", applicationYml);
                throw new RuntimeException("Invalid YAML format: " + e.getMessage(), e);
            }
    
            log.info("Parsed application.yml: {}", config);
    
            // 2. Create terraform directory
            Path terraformDir = Paths.get(workingDir, "terraform-" + System.currentTimeMillis());
            Files.createDirectories(terraformDir);
    
            // 3. Copy template files
            copyTemplateFiles(terraformDir);
    
            // 4. Create terraform.tfvars WITHOUT client_id/secret/tenant_id for access token
            String updatedApplicationYml = createTerraformVars(terraformDir, config, true);
    
            // 5. Build and push Docker image with updated application.yml
            String fullImageName = dockerImageService.buildAndPushDockerImage(
                request.githubRepoUrl(),
                request.dockerfilePath() != null ? request.dockerfilePath() : "Dockerfile",
                updatedApplicationYml
            );
    
            log.info("Docker image pushed: {}", fullImageName);
    
            // 6. Update container_image in terraform.tfvars
            updateContainerImage(terraformDir, fullImageName);
    
            // 7. Terraform 실행 (access token 사용)
            executeTerraformWithToken(terraformDir, request.accessToken());
    
        } catch (Exception e) {
            log.error("Failed to create infrastructure", e);
            throw new RuntimeException("Failed to create infrastructure", e);
        }
    }
    
    private void copyTemplateFiles(Path terraformDir) throws IOException {
        String[] templateFiles = {
            "provider.tf", "app_service.tf", "kafka.tf", "mongodb.tf", "mysql.tf", "postgresql.tf", "redis.tf", "variables.tf"
        };
        for (String fileName : templateFiles) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream("terraform/" + fileName)) {
                if (in == null) throw new FileNotFoundException("Resource not found: " + fileName);
                Files.copy(in, terraformDir.resolve(fileName));
            }
        }
        
        // application.yml.tftpl 파일도 복사
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("terraform/application.yml.tftpl")) {
            if (in == null) {
                // 파일이 없으면 간단한 템플릿 생성
                String template = 
                    "spring:\n" +
                    "  application:\n" +
                    "    name: ${app_name}\n" +
                    "  datasource:\n" +
                    "    url: ${database_url}\n" +
                    "    username: ${database_username}\n" +
                    "    password: ${database_password}\n" +
                    "  data:\n" +
                    "    mongodb:\n" +
                    "      uri: ${mongodb_uri}\n" +
                    "  redis:\n" +
                    "    host: ${redis_host}\n" +
                    "    port: ${redis_port}\n" +
                    "    password: ${redis_password}\n" +
                    "  kafka:\n" +
                    "    bootstrap-servers: ${kafka_bootstrap_servers}\n";
                Files.write(terraformDir.resolve("application.yml.tftpl"), template.getBytes());
            } else {
                Files.copy(in, terraformDir.resolve("application.yml.tftpl"));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private String createTerraformVars(Path terraformDir, Map<String, Object> config, boolean withToken) throws IOException {
        StringBuilder vars = new StringBuilder();
    
        if (!withToken) {
            vars.append("# Azure Authentication\n");
            vars.append("client_id       = \"").append(System.getenv("ARM_CLIENT_ID")).append("\"\n");
            vars.append("client_secret   = \"").append(System.getenv("ARM_CLIENT_SECRET")).append("\"\n");
            vars.append("tenant_id       = \"").append(System.getenv("ARM_TENANT_ID")).append("\"\n");
            vars.append("subscription_id = \"").append(System.getenv("ARM_SUBSCRIPTION_ID")).append("\"\n\n");
        }
    
        // Azure Configuration
        vars.append("# Azure Configuration\n");
        vars.append("location        = \"").append(location).append("\"\n");
        vars.append("resource_group  = \"").append(resourceGroup).append("\"\n\n");
    
        // Process each service configuration
        processRedisConfig(config, vars);
        processKafkaConfig(config, vars);
        processMongoDBConfig(config, vars);
        processDatabaseConfig(config, vars);
    
        // App Service configuration
        vars.append("# App Service Configuration\n");
        String timestamp = String.valueOf(System.currentTimeMillis());
        vars.append("app_name = \"app-").append(timestamp).append("\"\n");
        vars.append("container_image = \"\"\n");
        
        // 데이터베이스 및 서비스 연결 정보
        vars.append("\n# Database and Service Connection Information\n");
        
        // 데이터베이스 URL 추출 및 추가
        if (config.containsKey("datasource")) {
            Map<String, Object> datasource = (Map<String, Object>) config.get("datasource");
            if (datasource.containsKey("url")) {
                vars.append("database_url = \"").append(datasource.get("url")).append("\"\n");
            }
            if (datasource.containsKey("username")) {
                vars.append("database_username = \"").append(datasource.get("username")).append("\"\n");
            }
            if (datasource.containsKey("password")) {
                vars.append("database_password = \"").append(datasource.get("password")).append("\"\n");
            }
        }
        
        // MongoDB URI 추출 및 추가
        if (config.containsKey("data")) {
            Map<String, Object> data = (Map<String, Object>) config.get("data");
            if (data.containsKey("mongodb")) {
                Map<String, Object> mongodb = (Map<String, Object>) data.get("mongodb");
                if (mongodb.containsKey("uri")) {
                    vars.append("mongodb_uri = \"").append(mongodb.get("uri")).append("\"\n");
                }
            }
        }
        
        // Redis 설정 추출 및 추가
        if (config.containsKey("redis")) {
            Map<String, Object> redis = (Map<String, Object>) config.get("redis");
            if (redis.containsKey("host")) {
                vars.append("redis_host = \"").append(redis.get("host")).append("\"\n");
            }
            if (redis.containsKey("port")) {
                vars.append("redis_port = ").append(redis.get("port")).append("\n");
            }
            if (redis.containsKey("password")) {
                vars.append("redis_password = \"").append(redis.get("password")).append("\"\n");
            }
        }
        
        // Kafka 설정 추출 및 추가
        if (config.containsKey("kafka")) {
            Map<String, Object> kafka = (Map<String, Object>) config.get("kafka");
            if (kafka.containsKey("bootstrap-servers")) {
                vars.append("kafka_bootstrap_servers = \"").append(kafka.get("bootstrap-servers")).append("\"\n");
            }
        }
    
        Files.write(terraformDir.resolve("terraform.tfvars"), vars.toString().getBytes());
        return new Yaml().dump(config);
    }
    

    private void processRedisConfig(Map<String, Object> springConfig, StringBuilder vars) {
        if (springConfig.containsKey("redis")) {
            vars.append("# Redis Configuration\n");
            vars.append("redis_name = \"redis-").append(System.currentTimeMillis()).append("\"\n\n");
            
            Map<String, Object> redisConfig = new HashMap<>();
            redisConfig.put("host", "azurerm_redis_cache.redis.hostname");
            redisConfig.put("port", "azurerm_redis_cache.redis.ssl_port");
            redisConfig.put("password", "azurerm_redis_cache.redis.primary_access_key");
            springConfig.put("redis", redisConfig);
        }
    }

    private void processKafkaConfig(Map<String, Object> springConfig, StringBuilder vars) {
        if (springConfig.containsKey("kafka")) {
            vars.append("# Kafka Configuration\n");
            vars.append("kafka_name = \"kafka-").append(System.currentTimeMillis()).append("\"\n\n");
            
            Map<String, Object> kafkaConfig = new HashMap<>();
            kafkaConfig.put("bootstrap-servers", "azurerm_eventhub_namespace.kafka.default_primary_connection_string");
            springConfig.put("kafka", kafkaConfig);
        }
    }

    private void processMongoDBConfig(Map<String, Object> springConfig, StringBuilder vars) {
        Map<String, Object> dataConfig = Optional.ofNullable(springConfig.get("data"))
            .map(obj -> (Map<String, Object>) obj)
            .orElse(new HashMap<>());
            
        if (dataConfig.containsKey("mongodb")) {
            vars.append("# MongoDB Configuration\n");
            vars.append("mongodb_name = \"mongodb-").append(System.currentTimeMillis()).append("\"\n\n");
            
            Map<String, Object> mongodbConfig = new HashMap<>();
            mongodbConfig.put("uri", "azurerm_cosmosdb_account.mongodb.connection_strings[0]");
            dataConfig.put("mongodb", mongodbConfig);
            springConfig.put("data", dataConfig);
        }
    }

    private void processDatabaseConfig(Map<String, Object> springConfig, StringBuilder vars) {
        Map<String, Object> datasource = Optional.ofNullable(springConfig.get("datasource"))
            .map(obj -> (Map<String, Object>) obj)
            .orElse(new HashMap<>());
            
        String url = Optional.ofNullable(datasource.get("url"))
            .map(Object::toString)
            .orElse("");

        if (url.contains("mysql")) {
            vars.append("# MySQL Configuration\n");
            vars.append("mysql_name = \"mysql-").append(System.currentTimeMillis()).append("\"\n\n");
            
            datasource.put("url", "jdbc:mysql://azurerm_mysql_server.mysql.fqdn/azurerm_mysql_database.mysql.name");
            datasource.put("username", "mysql_admin_username");
            datasource.put("password", "mysql_admin_password");
            springConfig.put("datasource", datasource);
        } else if (url.contains("postgresql")) {
            vars.append("# PostgreSQL Configuration\n");
            vars.append("postgres_name = \"postgres-").append(System.currentTimeMillis()).append("\"\n\n");
            
            datasource.put("url", "jdbc:postgresql://azurerm_postgresql_server.postgres.fqdn/azurerm_postgresql_database.postgres.name");
            datasource.put("username", "postgres_admin_username");
            datasource.put("password", "postgres_admin_password");
            springConfig.put("datasource", datasource);
        }
    }

    private void executeTerraform(Path terraformDir) throws IOException, InterruptedException {
        // Azure CLI 세션 경로 확인
        Path azureCliPath = Paths.get(azureCliSessionPath);
        
        // Azure CLI 세션 존재 확인
        if (!Files.exists(azureCliPath)) {
            log.warn("Azure CLI 세션 디렉터리가 존재하지 않습니다: {}", azureCliPath);
        } else {
            log.info("Azure CLI 세션 디렉터리 확인: {}", azureCliPath);
        }

        // 1. Terraform init
        System.out.println("[Terraform] Running 'terraform init' in " + terraformDir);
        ProcessBuilder initProcess = new ProcessBuilder("terraform", "init")
            .directory(terraformDir.toFile());
        
        // Add Azure authentication environment variables
        Map<String, String> env = initProcess.environment();
        if (useCliAuth) {
            env.put("ARM_USE_MSI", "false");
            env.put("ARM_USE_AZURECLI_AUTH", "true");
            env.put("AZURE_CONFIG_DIR", azureCliPath.toString());
            log.info("Azure CLI 인증만 사용: {}", azureCliPath);
        } else {
            env.put("ARM_SUBSCRIPTION_ID", "4793a72d-f4c1-4471-9327-e04225717278");
            env.put("ARM_TENANT_ID", System.getenv("ARM_TENANT_ID"));
            env.put("ARM_CLIENT_ID", System.getenv("ARM_CLIENT_ID"));
            env.put("ARM_CLIENT_SECRET", System.getenv("ARM_CLIENT_SECRET"));
        }

        Process init = initProcess.start();

        try (BufferedReader stdOut = new BufferedReader(new InputStreamReader(init.getInputStream()));
             BufferedReader stdErr = new BufferedReader(new InputStreamReader(init.getErrorStream()))) {
            stdOut.lines().forEach(line -> System.out.println("[Terraform][init][stdout] " + line));
            stdErr.lines().forEach(line -> System.err.println("[Terraform][init][stderr] " + line));
        }
        int initExitCode = init.waitFor();
        System.out.println("[Terraform] 'terraform init' finished with exit code " + initExitCode);
        if (initExitCode != 0) {
            throw new RuntimeException("Terraform init failed with exit code: " + initExitCode);
        }

        // 2. Terraform apply
        System.out.println("[Terraform] Running 'terraform apply' in " + terraformDir);
        ProcessBuilder applyProcess = new ProcessBuilder("terraform", "apply", "-auto-approve")
            .directory(terraformDir.toFile());
        
        // Add Azure authentication environment variables
        env = applyProcess.environment();
        if (useCliAuth) {
            env.put("ARM_USE_MSI", "false");
            env.put("ARM_USE_AZURECLI_AUTH", "true");
            env.put("AZURE_CONFIG_DIR", azureCliPath.toString());
            log.info("Azure CLI 인증만 사용: {}", azureCliPath);
        } else {
            env.put("ARM_SUBSCRIPTION_ID", "4793a72d-f4c1-4471-9327-e04225717278");
            env.put("ARM_TENANT_ID", System.getenv("ARM_TENANT_ID"));
            env.put("ARM_CLIENT_ID", System.getenv("ARM_CLIENT_ID"));
            env.put("ARM_CLIENT_SECRET", System.getenv("ARM_CLIENT_SECRET"));
        }

        Process apply = applyProcess.start();

        try (BufferedReader stdOut = new BufferedReader(new InputStreamReader(apply.getInputStream()));
             BufferedReader stdErr = new BufferedReader(new InputStreamReader(apply.getErrorStream()))) {
            stdOut.lines().forEach(line -> System.out.println("[Terraform][apply][stdout] " + line));
            stdErr.lines().forEach(line -> System.err.println("[Terraform][apply][stderr] " + line));
        }
        int applyExitCode = apply.waitFor();
        System.out.println("[Terraform] 'terraform apply' finished with exit code " + applyExitCode);
        if (applyExitCode != 0) {
            throw new RuntimeException("Terraform apply failed with exit code: " + applyExitCode);
        }

        // 3. Update application.yml with actual values from Terraform outputs
        updateApplicationYmlWithTerraformOutputs(terraformDir);
    }

    public void executeTerraformWithToken(Path terraformDir, String accessToken) throws IOException, InterruptedException {
        log.info("Running terraform with access token in {}", terraformDir);
        
        // 토큰 길이 확인 및 로깅 (보안을 위해 전체 토큰은 로그에 남기지 않음)
        if (accessToken == null || accessToken.isEmpty()) {
            throw new RuntimeException("Access token is empty or null");
        }
        log.info("Access token received (length: {}), first 10 chars: {}...", 
            accessToken.length(), 
            accessToken.length() > 10 ? accessToken.substring(0, 10) + "..." : "<too short>");
        
        // .terraform 디렉토리가 있으면 삭제 (이전 캐시된 데이터 제거)
        Path terraformCacheDir = terraformDir.resolve(".terraform");
        if (Files.exists(terraformCacheDir)) {
            log.info("Removing existing .terraform directory at {}", terraformCacheDir);
            try {
                Files.walk(terraformCacheDir)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
                Files.deleteIfExists(terraformCacheDir);
                log.info(".terraform directory successfully removed");
            } catch (IOException e) {
                log.warn("Failed to completely remove .terraform directory: {}", e.getMessage());
            }
        }
        
        // 1. Terraform init
        log.info("[Terraform] Running 'terraform init' in {}", terraformDir);
        ProcessBuilder initProcess = new ProcessBuilder("terraform", "init")
            .directory(terraformDir.toFile());
        
        // 인증 환경 변수 설정 - init 프로세스
        Map<String, String> initEnv = initProcess.environment();
        
        // 기존 Azure 관련 환경 변수 모두 제거
        initEnv.remove("ARM_CLIENT_ID");
        initEnv.remove("ARM_CLIENT_SECRET");
        initEnv.remove("ARM_USE_CLI");
        initEnv.remove("ARM_USE_AZURECLI_AUTH");
        initEnv.remove("ARM_USE_MSI");
        initEnv.remove("ARM_USE_OIDC");
        initEnv.remove("AZURE_CONFIG_DIR");
        
        // Terraform Azure Provider에서 사용하는 환경 변수 명확히 설정
        initEnv.put("ARM_ACCESS_TOKEN", accessToken);
        initEnv.put("ARM_SUBSCRIPTION_ID", subscriptionId);
        initEnv.put("ARM_TENANT_ID", "e6c9ec09-8430-4a99-bf15-242bc089b409");
        initEnv.put("ARM_USE_CLI", "false");
        initEnv.put("ARM_USE_AZURECLI_AUTH", "false");
        initEnv.put("ARM_USE_MSI", "false");
        initEnv.put("ARM_USE_OIDC", "false");
        
        // 디버그 로깅 활성화
        initEnv.put("TF_LOG", "DEBUG");
        initEnv.put("TF_LOG_PATH", terraformDir.resolve("terraform-init.log").toString());
        
        // 환경 변수 로깅 (민감 정보는 마스킹)
        log.info("Init 프로세스 환경 변수:");
        initEnv.forEach((k, v) -> {
            if (k.toLowerCase().contains("token") || k.toLowerCase().contains("secret")) {
                log.info("  {} = **** (length: {})", k, v.length());
            } else {
                log.info("  {} = {}", k, v);
            }
        });
        
        log.info("환경 변수 설정 완료: ARM_SUBSCRIPTION_ID={}, ARM_USE_CLI={}, ARM_TENANT_ID={}, ARM_ACCESS_TOKEN 길이={}", 
            initEnv.get("ARM_SUBSCRIPTION_ID"), initEnv.get("ARM_USE_CLI"), initEnv.get("ARM_TENANT_ID"), accessToken.length());
        
        Process init = initProcess.start();
        try (BufferedReader stdOut = new BufferedReader(new InputStreamReader(init.getInputStream()));
             BufferedReader stdErr = new BufferedReader(new InputStreamReader(init.getErrorStream()))) {
            stdOut.lines().forEach(line -> System.out.println("[Terraform][init][stdout] " + line));
            stdErr.lines().forEach(line -> System.err.println("[Terraform][init][stderr] " + line));
        }
        int initExitCode = init.waitFor();
        log.info("[Terraform] 'terraform init' finished with exit code {}", initExitCode);
        if (initExitCode != 0) {
            throw new RuntimeException("Terraform init failed with exit code: " + initExitCode);
        }

        // 2. Terraform apply
        log.info("[Terraform] Running 'terraform apply' in {}", terraformDir);
        ProcessBuilder applyProcess = new ProcessBuilder("terraform", "apply", "-auto-approve")
            .directory(terraformDir.toFile());
        
        // Apply에 대한 환경 변수도 새로 할당하여 명확하게 설정
        Map<String, String> applyEnv = applyProcess.environment();
        
        // 기존 Azure 관련 환경 변수 모두 제거
        applyEnv.remove("ARM_CLIENT_ID");
        applyEnv.remove("ARM_CLIENT_SECRET");
        applyEnv.remove("ARM_USE_CLI");
        applyEnv.remove("ARM_USE_AZURECLI_AUTH");
        applyEnv.remove("ARM_USE_MSI");
        applyEnv.remove("ARM_USE_OIDC");
        applyEnv.remove("AZURE_CONFIG_DIR");
        
        // Terraform Azure Provider에서 사용하는 환경 변수 명확히 설정
        applyEnv.put("ARM_ACCESS_TOKEN", accessToken);
        applyEnv.put("ARM_SUBSCRIPTION_ID", subscriptionId);
        applyEnv.put("ARM_TENANT_ID", "e6c9ec09-8430-4a99-bf15-242bc089b409");
        applyEnv.put("ARM_USE_CLI", "false");
        applyEnv.put("ARM_USE_AZURECLI_AUTH", "false");
        applyEnv.put("ARM_USE_MSI", "false");
        applyEnv.put("ARM_USE_OIDC", "false");
        
        // 디버그 로깅 활성화
        applyEnv.put("TF_LOG", "DEBUG");
        applyEnv.put("TF_LOG_PATH", terraformDir.resolve("terraform-apply.log").toString());
        
        // 환경 변수 로깅 (민감 정보는 마스킹)
        log.info("Apply 프로세스 환경 변수:");
        applyEnv.forEach((k, v) -> {
            if (k.toLowerCase().contains("token") || k.toLowerCase().contains("secret")) {
                log.info("  {} = **** (length: {})", k, v.length());
            } else {
                log.info("  {} = {}", k, v);
            }
        });
        
        // 표준 출력 및 오류 스트림 리다이렉션
        applyProcess.redirectErrorStream(true);
        
        Process apply = applyProcess.start();
        boolean hasAccessError = false;
        StringBuilder output = new StringBuilder();
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(apply.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[Terraform][apply][stdout] " + line);
                output.append(line).append("\n");
                
                // 다양한 액세스 오류 감지
                if (line.contains("AADSTS") || 
                    line.contains("Access has been blocked by Conditional Access") ||
                    line.contains("Continuous access evaluation") ||
                    line.contains("InteractionRequired") ||
                    line.contains("LocationConditionEvaluation") ||
                    line.contains("az login") ||
                    line.contains("obtain subscription") ||
                    line.contains("Authorization failed")) {
                    hasAccessError = true;
                }
            }
        }
        
        int applyExitCode = apply.waitFor();
        log.info("[Terraform] 'terraform apply' finished with exit code {}", applyExitCode);
        
        if (applyExitCode != 0) {
            String logOutput = output.toString();
            log.error("Terraform 실행 실패: {}", logOutput);
            
            if (hasAccessError) {
                log.error("Azure 인증 오류 발생");
                throw new RuntimeException("Azure 인증 오류: Azure 액세스 토큰이 유효하지 않거나 필요한 권한이 없습니다. " + 
                        "다시 로그인하거나 Azure 관리자에게 문의하세요.");
            }
            
            // 일반적인 오류
            throw new RuntimeException("Terraform apply failed with exit code: " + applyExitCode);
        }
        
        log.info("Terraform 실행 완료, 애플리케이션이 App Service에 배포되었습니다.");
    }

    private void updateApplicationYmlWithTerraformOutputs(Path terraformDir) throws IOException, InterruptedException {
        // Get Terraform outputs
        ProcessBuilder outputProcess = new ProcessBuilder("terraform", "output", "-json")
            .directory(terraformDir.toFile());
        Process output = outputProcess.start();

        StringBuilder outputJson = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(output.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                outputJson.append(line);
            }
        }
        int exitCode = output.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Failed to get Terraform outputs");
        }

        // Parse outputs and update application.yml
        Map<String, Object> outputs = new ObjectMapper().readValue(outputJson.toString(), Map.class);
        
        // Update Redis configuration
        if (outputs.containsKey("redis_hostname")) {
            updateRedisConfig(outputs);
        }
        
        // Update Kafka configuration
        if (outputs.containsKey("kafka_connection_string")) {
            updateKafkaConfig(outputs);
        }
        
        // Update MongoDB configuration
        if (outputs.containsKey("mongodb_connection_string")) {
            updateMongoDBConfig(outputs);
        }
        
        // Update Database configuration
        if (outputs.containsKey("mysql_server_fqdn") || outputs.containsKey("postgres_server_fqdn")) {
            updateDatabaseConfig(outputs);
        }
    }

    private void updateRedisConfig(Map<String, Object> outputs) {
        // Update Redis configuration with actual values
        Map<String, Object> redisConfig = new HashMap<>();
        redisConfig.put("host", outputs.get("redis_hostname"));
        redisConfig.put("port", outputs.get("redis_ssl_port"));
        redisConfig.put("password", outputs.get("redis_primary_access_key"));
        // Update application.yml with these values
    }

    private void updateKafkaConfig(Map<String, Object> outputs) {
        // Update Kafka configuration with actual values
        Map<String, Object> kafkaConfig = new HashMap<>();
        kafkaConfig.put("bootstrap-servers", outputs.get("kafka_connection_string"));
        // Update application.yml with these values
    }

    private void updateMongoDBConfig(Map<String, Object> outputs) {
        // Update MongoDB configuration with actual values
        Map<String, Object> mongodbConfig = new HashMap<>();
        mongodbConfig.put("uri", outputs.get("mongodb_connection_string"));
        // Update application.yml with these values
    }

    private void updateDatabaseConfig(Map<String, Object> outputs) {
        // Update Database configuration with actual values
        Map<String, Object> datasource = new HashMap<>();
        if (outputs.containsKey("mysql_server_fqdn")) {
            datasource.put("url", "jdbc:mysql://" + outputs.get("mysql_server_fqdn") + "/" + outputs.get("mysql_database_name"));
            datasource.put("username", outputs.get("mysql_admin_username"));
            datasource.put("password", outputs.get("mysql_admin_password"));
        } else if (outputs.containsKey("postgres_server_fqdn")) {
            datasource.put("url", "jdbc:postgresql://" + outputs.get("postgres_server_fqdn") + "/" + outputs.get("postgres_database_name"));
            datasource.put("username", outputs.get("postgres_admin_username"));
            datasource.put("password", outputs.get("postgres_admin_password"));
        }
        // Update application.yml with these values
    }

    private void updateContainerImage(Path terraformDir, String fullImageName) throws IOException {
        Path tfvarsPath = terraformDir.resolve("terraform.tfvars");
        String content = Files.readString(tfvarsPath);
        content = content.replace("container_image = \"\"", "container_image = \"" + fullImageName + "\"");
        Files.write(tfvarsPath, content.getBytes());
    }
}