resource "azurerm_app_service_plan" "app_plan" {
  name                = "plan-${var.app_name}-${formatdate("YYYYMMDDhhmmss", timestamp())}"
  location            = var.location
  resource_group_name = var.resource_group
  
  sku {
    tier = "Standard"
    size = "S1"
  }
}

resource "azurerm_app_service" "app" {
  name                = var.app_name
  location            = var.location
  resource_group_name = var.resource_group
  app_service_plan_id = azurerm_app_service_plan.app_plan.id
  
  site_config {
    linux_fx_version = "DOCKER|${var.container_image}"
    always_on        = true
  }
  
  app_settings = {
    "WEBSITES_ENABLE_APP_SERVICE_STORAGE" = "false"
    "DOCKER_REGISTRY_SERVER_URL"          = "https://${split("/", var.container_image)[0]}"
    "SPRING_CONFIG_LOCATION"              = "file:/home/site/wwwroot/config/application.yml"
  }
}

resource "null_resource" "deploy_config" {
  depends_on = [azurerm_app_service.app]
  
  # 변경 사항이 있을 때마다 트리거되도록 동적 코드 추가
  triggers = {
    app_settings = join(",", [for k, v in azurerm_app_service.app.app_settings : "${k}=${v}"])
  }
  
  provisioner "local-exec" {
    command = <<EOT
cat > application.yml << 'EOF'
spring:
  application:
    name: ${var.app_name}
  datasource:
    url: ${var.database_url}
    username: ${var.database_username}
    password: ${var.database_password}
  data:
    mongodb:
      uri: ${var.mongodb_uri}
  redis:
    host: ${var.redis_host}
    port: ${var.redis_port}
    password: ${var.redis_password}
  kafka:
    bootstrap-servers: ${var.kafka_bootstrap_servers}
EOF

# Azure CLI를 사용해 App Service에 설정 파일 업로드
az webapp config appsettings set --resource-group ${var.resource_group} --name ${azurerm_app_service.app.name} --settings SPRING_CONFIG_EXPORTED=true
az webapp deploy --resource-group ${var.resource_group} --name ${azurerm_app_service.app.name} --src-path application.yml --target-path /home/site/wwwroot/config/application.yml
EOT
  }
}

output "app_url" {
  value = "https://${azurerm_app_service.app.default_site_hostname}"
} 