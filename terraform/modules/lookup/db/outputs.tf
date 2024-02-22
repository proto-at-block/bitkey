output "db" {
  value       = data.aws_rds_cluster.this
  description = "Raw outputs of the rds cluster"
}

output "database_name" {
  description = "The name of the default database the RDS cluster"
  value       = data.aws_rds_cluster.this.database_name
}

output "endpoint" {
  description = "The DNS address of the RDS instance"
  value       = data.aws_rds_cluster.this.endpoint
}

output "port" {
  description = "The MySQL port of the RDS instance"
  value       = data.aws_rds_cluster.this.port
}

output "reader_endpoint" {
  description = "A read-only endpoint for the Aurora cluster, automatically load-balanced across replicas"
  value       = data.aws_rds_cluster.this.reader_endpoint
}

output "master_username" {
  description = "The master username for the database"
  value       = data.aws_rds_cluster.this.master_username
}

output "master_password_secret_arn" {
  value       = data.aws_secretsmanager_secret.master_password.arn
  description = "ARN to the secrets manager secret containing the master password"
}

output "ingress_security_group_id" {
  value       = data.aws_security_group.ingress_sg.id
  description = "ID of the Security Group that allows ingress to this database, to be added to the source service"
}