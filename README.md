# Terraform 인프라 자동화 서비스

이 프로젝트는 Azure Kubernetes Service(AKS)에서 실행되며, GitHub 저장소의 코드를 기반으로 Terraform을 사용하여 Azure 인프라를 자동으로 프로비저닝하는 서비스입니다.

## 목차

1. [사전 요구사항](#사전-요구사항)
2. [서비스 빌드 및 배포](#서비스-빌드-및-배포)
3. [파드 접속 및 Azure 인증](#파드-접속-및-azure-인증)
4. [Terraform 테스트](#terraform-테스트)
5. [트러블슈팅](#트러블슈팅)

## 사전 요구사항

다음 도구와 계정이 필요합니다:

- Java 17 이상
- Docker
- kubectl
- Azure CLI (az)
- Azure 계정 및 구독
- Azure Container Registry (ACR) 접근 권한
- Kubernetes 클러스터 접근 권한

## 서비스 빌드 및 배포

### 1. 환경 설정

먼저 Azure CLI를 사용하여 로그인합니다:

```bash
# Azure 계정 로그인
az login

# 구독 설정 (필요한 경우)
az account set --subscription <구독ID>

# ACR 로그인
az acr login --name <ACR이름>
```

### 2. 애플리케이션 빌드

프로젝트 루트 디렉토리에서 Gradle을 사용하여 애플리케이션을 빌드합니다:

```bash
# Gradle 빌드 (테스트 제외)
./gradlew clean build -x test
```

### 3. Docker 이미지 빌드 및 푸시

```bash
# Linux/macOS용 아키텍처 설정
docker buildx create --name builder --driver docker-container --use
docker buildx inspect --bootstrap

# 이미지 빌드 및 푸시 (amd64 아키텍처용)
docker buildx build --platform linux/amd64 -t <ACR이름>.azurecr.io/terraform-service:latest --push .
```

### 4. Kubernetes 시크릿 생성 (최초 배포 시)

ACR 인증을 위한 시크릿을 생성합니다:

```bash
# 네임스페이스 생성 (없는 경우)
kubectl create namespace terraform-service

# ACR 시크릿 생성
kubectl create secret docker-registry acr-auth \
  --namespace terraform-service \
  --docker-server=<ACR이름>.azurecr.io \
  --docker-username=<ACR사용자명> \
  --docker-password=<ACR비밀번호>
```

### 5. Kubernetes 배포

배포 매니페스트를 적용합니다:

```bash
# ConfigMap, Secret, Deployment, Service 등 모든 리소스 배포
kubectl apply -f kubernetes/deployment.yaml
```

### 6. 배포 상태 확인

```bash
# 파드 상태 확인
kubectl get pods -n terraform-service

# 서비스 확인
kubectl get services -n terraform-service

# 로그 확인
kubectl logs -n terraform-service <파드명>
```

## 파드 접속 및 Azure 인증

파드 내부에서 직접 Azure 인증이 필요한 경우, 다음 단계를 따릅니다:

### 1. 파드에 쉘로 접속

```bash
# 파드 이름 확인
kubectl get pods -n terraform-service

# 파드에 접속
kubectl exec -it <파드명> -n terraform-service -- /bin/bash
```

### 2. Azure CLI 설치 (파드 내부에 미리 설치되어 있지 않은 경우)

```bash
# 필요한 패키지 설치
apt-get update && apt-get install -y curl apt-transport-https lsb-release gnupg

# Microsoft 서명 키 추가
curl -sL https://packages.microsoft.com/keys/microsoft.asc | gpg --dearmor | tee /etc/apt/trusted.gpg.d/microsoft.gpg > /dev/null

# Azure CLI 저장소 추가
AZ_REPO=$(lsb_release -cs)
echo "deb [arch=amd64] https://packages.microsoft.com/repos/azure-cli/ $AZ_REPO main" | tee /etc/apt/sources.list.d/azure-cli.list

# Azure CLI 설치
apt-get update && apt-get install -y azure-cli
```

### 3. Device Code 로그인 수행

```bash
# Device Code 로그인 시작
az login --use-device-code

# 출력된 지침을 따릅니다:
# 1. 표시된 URL(https://microsoft.com/devicelogin)로 이동
# 2. 출력된 코드를 입력
# 3. Microsoft 계정으로 로그인하여 인증 완료
```

### 4. 액세스 토큰 범위 확장 (필요한 경우)

특정 Azure 서비스에 대한 액세스 토큰이 필요한 경우:

```bash
# Microsoft Graph 권한 범위로 로그인
az login --scope https://graph.microsoft.com//.default --use-device-code

# Azure Resource Manager 권한 범위로 로그인
az login --scope https://management.azure.com//.default --use-device-code
```

### 5. 구독 확인 및 설정

```bash
# 사용 가능한 구독 확인
az account list --output table

# 특정 구독 사용
az account set --subscription <구독ID 또는 이름>
```

### 6. 인증 테스트

```bash
# 계정 정보 확인
az account show

# 리소스 그룹 목록 조회 테스트
az group list --output table
```

파드 내에서 위 단계를 완료하면 Azure 인증이 설정되어 Terraform이 Azure API에 접근할 수 있습니다.

## Terraform 테스트

### 1. 포트 포워딩 설정

로컬 테스트를 위해 Kubernetes 파드로 포트 포워딩을 설정합니다:

```bash
# 현재 실행 중인 파드 확인
kubectl get pods -n terraform-service

# 포트 포워딩 설정 (로컬 8090 포트를 파드의 8080 포트로 포워딩)
kubectl port-forward -n terraform-service <파드명> 8090:8080
```

### 2. Azure 액세스 토큰 가져오기

```bash
# Azure 액세스 토큰 가져오기
TOKEN=$(az account get-access-token --scope https://management.azure.com//.default --query accessToken -o tsv)
```

### 3. 테스트 요청 파일 생성

```bash
# 테스트 요청 JSON 파일 생성
cat > test-terraform-request.json << EOF
{
  "githubRepoUrl": "https://github.com/your-org/your-repo.git",
  "applicationYml": "c3ByaW5nOgogIGRhdGFzb3VyY2U6CiAgICB1cmw6IGpkYmM6aDI6bWVtOnRlc3QKICAgIHVzZXJuYW1lOiBzYQogICAgcGFzc3dvcmQ6IHBhc3N3b3JkCiAgICBkcml2ZXItY2xhc3MtbmFtZTogb3JnLmgyLkRyaXZlcgogIGpwYToKICAgIGRhdGFiYXNlLXBsYXRmb3JtOiBvcmcuaGliZXJuYXRlLmRpYWxlY3QuSDJEaWFsZWN0CiAgICBzaG93LXNxbDogdHJ1ZQogICAgaGliZXJuYXRlOgogICAgICBkZGwtYXV0bzogdXBkYXRl",
  "dockerfilePath": "Dockerfile",
  "accessToken": "${TOKEN}"
}
EOF

# 토큰을 실제 값으로 대체
sed -i '' "s|\${TOKEN}|${TOKEN}|g" test-terraform-request.json
```

### 4. API 호출로 Terraform 배포 테스트

```bash
# API 호출로 테스트
curl -X POST http://localhost:8090/deploy-with-token \
  -H "Content-Type: application/json" \
  -H "accept: */*" \
  -d @test-terraform-request.json
```

### 5. 로그 확인

```bash
# 파드 로그 확인
kubectl logs -n terraform-service <파드명> --follow
```

## 트러블슈팅

### 오류: Azure 인증 문제

오류 메시지: `Azure 인증 오류: Azure 액세스 토큰이 유효하지 않거나 필요한 권한이 없습니다.`

**해결 방법:**
1. Azure CLI를 사용하여 다시 로그인
2. 올바른 권한을 가진 구독 선택
3. ConfigMap에서 `ARM_USE_MSI` 값이 `false`로 설정되어 있는지 확인

```bash
kubectl edit configmap terraform-config -n terraform-service
```

다음 값들이 올바르게 설정되어 있는지 확인:
```yaml
AZURE_USE_CLI_AUTH: "false"
ARM_USE_MSI: "false"
ARM_USE_CLI: "false"
```

### 오류: 파드 시작 실패

**해결 방법:**
1. 파드 상태 및 이벤트 확인
```bash
kubectl describe pod <파드명> -n terraform-service
```

2. 이미지 Pull 오류인 경우 ACR 시크릿 확인
```bash
kubectl get secret acr-auth -n terraform-service -o yaml
```

3. 로그 확인
```bash
kubectl logs <파드명> -n terraform-service
```

### 오류: Git Clone 실패

**해결 방법:**
1. 제공된 GitHub 저장소 URL이 올바른지 확인
2. 저장소가 공개(Public)인지 확인
3. Private 저장소의 경우 인증 토큰 제공 필요
