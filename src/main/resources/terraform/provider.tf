terraform {
  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = ">= 3.64.0"
    }
  }
  required_version = ">= 1.4.0"
}

# 환경 변수로 인증 정보를 제공합니다:
# - ARM_ACCESS_TOKEN: 액세스 토큰 (필수)
# - ARM_SUBSCRIPTION_ID: 구독 ID (필수)
# - ARM_TENANT_ID: 테넌트 ID (필수)
# 
# 다른 인증 방식 비활성화:
# - ARM_USE_CLI=false
# - ARM_USE_AZURECLI_AUTH=false 
# - ARM_USE_MSI=false
# - ARM_USE_OIDC=false
provider "azurerm" {
  features {}

  subscription_id            = var.subscription_id
  tenant_id                  = var.tenant_id
  skip_provider_registration = true
}
