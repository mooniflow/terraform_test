resource "azurerm_cosmosdb_account" "mongodb" {
  name                = var.mongodb_name
  location            = var.location
  resource_group_name = var.resource_group
  offer_type          = "Standard"
  kind                = "MongoDB"

  capabilities {
    name = "EnableMongo"
  }

  consistency_policy {
    consistency_level       = "Session"
    max_interval_in_seconds = 5
    max_staleness_prefix    = 100
  }

  geo_location {
    location          = var.location
    failover_priority = 0
  }
}

resource "azurerm_cosmosdb_mongo_database" "mongodb" {
  name                = "mongodb-db"
  resource_group_name = var.resource_group
  account_name        = azurerm_cosmosdb_account.mongodb.name
}

output "mongodb_connection_string" {
  value     = "mongodb://${azurerm_cosmosdb_account.mongodb.name}:${azurerm_cosmosdb_account.mongodb.primary_key}@${azurerm_cosmosdb_account.mongodb.name}.mongo.cosmos.azure.com:10255/${azurerm_cosmosdb_mongo_database.mongodb.name}?ssl=true&replicaSet=globaldb"
  sensitive = true
}

output "mongodb_database_name" {
  value = azurerm_cosmosdb_mongo_database.mongodb.name
} 