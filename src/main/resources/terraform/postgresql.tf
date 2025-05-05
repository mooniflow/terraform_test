resource "azurerm_postgresql_server" "postgres" {
  name                = var.postgres_name
  location            = var.location
  resource_group_name = var.resource_group

  sku_name = "B_Gen5_1"

  storage_mb                   = 51200
  backup_retention_days        = 7
  geo_redundant_backup_enabled = false
  auto_grow_enabled           = true

  administrator_login          = var.postgres_admin_username
  administrator_login_password = var.postgres_admin_password
  version                     = "11"
  ssl_enforcement_enabled     = true
}

resource "azurerm_postgresql_database" "postgres" {
  name                = var.postgres_database_name
  resource_group_name = var.resource_group
  server_name         = azurerm_postgresql_server.postgres.name
  charset             = "UTF8"
  collation           = "en_US.utf8"
}

output "postgres_server_fqdn" {
  value = azurerm_postgresql_server.postgres.fqdn
}

output "postgres_database_name" {
  value = azurerm_postgresql_database.postgres.name
} 