variable "location" {
  description = "Azure region"
  type        = string
  default     = "koreacentral"
}

variable "resource_group" {
  description = "Azure resource group name"
  type        = string
  default     = "rg-az01-co001501-sbox-poc-team02"
}

variable "redis_name" {
  description = "Name of the Redis cache"
  type        = string
  default     = "redis-cache"
}

variable "kafka_name" {
  description = "Name of the Kafka cluster"
  type        = string
  default     = "kafka-cluster"
}

variable "mongodb_name" {
  description = "Name of the MongoDB database"
  type        = string
  default     = "mongodb-cosmos"
}

variable "mysql_name" {
  description = "Name of the MySQL server"
  type        = string
  default     = "mysql-server"
}

variable "mysql_admin_username" {
  description = "MySQL administrator username"
  type        = string
  default     = "mysqladmin"
}

variable "mysql_admin_password" {
  description = "MySQL administrator password"
  type        = string
  sensitive   = true
  default     = "P@ssw0rd1234!"
}

variable "mysql_database_name" {
  description = "Name of the MySQL database"
  type        = string
  default     = "mysqldb"
}

variable "postgres_name" {
  description = "Name of the PostgreSQL server"
  type        = string
  default     = "postgres-server"
}

variable "postgres_admin_username" {
  description = "PostgreSQL administrator username"
  type        = string
  default     = "psqladmin"
}

variable "postgres_admin_password" {
  description = "PostgreSQL administrator password"
  type        = string
  sensitive   = true
  default     = "P@ssw0rd1234!"
}

variable "postgres_database_name" {
  description = "Name of the PostgreSQL database"
  type        = string
  default     = "postgresdb"
}

# Application configuration variables
variable "database_url" {
  description = "Database connection URL"
  type        = string
  default     = ""
}

variable "database_username" {
  description = "Database username"
  type        = string
  default     = ""
}

variable "database_password" {
  description = "Database password"
  type        = string
  sensitive   = true
  default     = ""
}

variable "mongodb_uri" {
  description = "MongoDB connection URI"
  type        = string
  default     = ""
}

variable "redis_host" {
  description = "Redis host"
  type        = string
  default     = "localhost"
}

variable "redis_port" {
  description = "Redis port"
  type        = number
  default     = 6379
}

variable "redis_password" {
  description = "Redis password"
  type        = string
  sensitive   = true
  default     = ""
}

variable "kafka_bootstrap_servers" {
  description = "Kafka bootstrap servers"
  type        = string
  default     = ""
}

variable "subscription_id" {
  description = "Azure subscription ID"
  type        = string
  default     = "4793a72d-f4c1-4471-9327-e04225717278"
}

variable "tenant_id" {
  description = "Azure tenant ID"
  type        = string
  default     = "e6c9ec09-8430-4a99-bf15-242bc089b409"
}

# 앱 관련 변수
variable "app_name" {
  description = "배포할 애플리케이션의 이름"
  type        = string
  default     = "spring-app"
}

variable "container_image" {
  description = "컨테이너 이미지 주소"
  type        = string
}
