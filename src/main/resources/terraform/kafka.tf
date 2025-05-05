resource "azurerm_eventhub_namespace" "kafka" {
  name                = var.kafka_name
  location            = var.location
  resource_group_name = var.resource_group
  sku                 = "Standard"
  capacity            = 1

  network_rulesets {
    default_action = "Allow"
    trusted_service_access_enabled = true
  }
}

resource "azurerm_eventhub" "kafka" {
  name                = "kafka-topic"
  namespace_name      = azurerm_eventhub_namespace.kafka.name
  resource_group_name = var.resource_group
  partition_count     = 2
  message_retention   = 1
}

output "kafka_connection_string" {
  value     = azurerm_eventhub_namespace.kafka.default_primary_connection_string
  sensitive = true
}

output "kafka_namespace" {
  value = azurerm_eventhub_namespace.kafka.name
} 