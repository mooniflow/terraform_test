resource "azurerm_mysql_flexible_server" "mysql" {
  name                = var.mysql_name
  location            = var.location
  resource_group_name = var.resource_group

  sku_name = "B_Standard_B1ms"

  storage {
    size_gb = 20
  }

  backup_retention_days        = 7
  geo_redundant_backup_enabled = false

  administrator_login    = var.mysql_admin_username
  administrator_password = var.mysql_admin_password
  version               = "5.7"
}

resource "azurerm_mysql_flexible_database" "mysql" {
  name                = var.mysql_database_name
  resource_group_name = var.resource_group
  server_name         = azurerm_mysql_flexible_server.mysql.name
  charset             = "utf8"
  collation           = "utf8_unicode_ci"
}

output "mysql_server_fqdn" {
  value = azurerm_mysql_flexible_server.mysql.fqdn
}

output "mysql_database_name" {
  value = azurerm_mysql_flexible_database.mysql.name
} 