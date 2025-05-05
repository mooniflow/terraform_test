FROM --platform=linux/amd64 eclipse-temurin:17-jdk as build
WORKDIR /workspace/app

COPY build.gradle settings.gradle gradlew ./
COPY gradle gradle
COPY src src

RUN ./gradlew bootJar -x test
RUN mkdir -p build/dependency && (cd build/dependency; jar -xf ../libs/*.jar)

# Ubuntu 22.04 기반 이미지로 변경 (amd64 아키텍처 명시)
FROM --platform=linux/amd64 ubuntu:22.04

# 필요한 패키지 설치
RUN apt-get update && apt-get install -y \
    openjdk-17-jre-headless \
    curl \
    gnupg \
    lsb-release \
    unzip \
    apt-transport-https \
    ca-certificates \
    git \
    docker.io \
    software-properties-common

# Azure CLI 설치
RUN curl -sL https://aka.ms/InstallAzureCLIDeb | bash

# Terraform 설치 (바이너리 직접 다운로드 방식)
RUN curl -fsSL https://releases.hashicorp.com/terraform/1.7.0/terraform_1.7.0_linux_amd64.zip -o terraform.zip \
    && unzip terraform.zip -d /usr/local/bin/ \
    && rm terraform.zip \
    && chmod +x /usr/local/bin/terraform

# 애플리케이션 디렉토리 설정
VOLUME /tmp
ARG DEPENDENCY=/workspace/app/build/dependency
COPY --from=build ${DEPENDENCY}/BOOT-INF/lib /app/lib
COPY --from=build ${DEPENDENCY}/META-INF /app/META-INF
COPY --from=build ${DEPENDENCY}/BOOT-INF/classes /app

# 작업 디렉토리 생성 및 권한 설정
RUN mkdir -p /tmp/terraform && chmod 777 /tmp/terraform

# 환경변수 설정
ENV PATH="/usr/local/bin:${PATH}"
ENV LANG=C.UTF-8

# 애플리케이션 실행
ENTRYPOINT ["java","-cp","app:app/lib/*","com.kt.terraform.TerraformApplication"]