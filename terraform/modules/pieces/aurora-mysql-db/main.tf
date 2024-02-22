module "vpc" {
  source   = "../../lookup/vpc"
  vpc_name = var.vpc_name
}

module "this" {
  source    = "../../lookup/namespacer"
  namespace = var.namespace
  name      = var.name
}

// Composing the rds aurora module to add a security group that can be applied to a service to gain access to the
// database.
module "cluster" {
  source = "git::https://github.com/terraform-aws-modules/terraform-aws-rds-aurora//?ref=4380f14c101b145e4b691bcb465b7978b0e44fdc" // Tag v8.0.2

  create                                      = var.create
  name                                        = module.this.id
  tags                                        = var.tags
  create_db_subnet_group                      = var.create_db_subnet_group
  db_subnet_group_name                        = var.db_subnet_group_name
  vpc_id                                      = module.vpc.vpc_id
  subnets                                     = module.vpc.private_subnets
  is_primary_cluster                          = var.is_primary_cluster
  cluster_use_name_prefix                     = var.cluster_use_name_prefix
  allocated_storage                           = var.allocated_storage
  allow_major_version_upgrade                 = var.allow_major_version_upgrade
  apply_immediately                           = var.apply_immediately
  availability_zones                          = var.availability_zones
  backup_retention_period                     = var.backup_retention_period
  backtrack_window                            = var.backtrack_window
  cluster_members                             = var.cluster_members
  copy_tags_to_snapshot                       = var.copy_tags_to_snapshot
  database_name                               = var.database_name
  db_cluster_instance_class                   = var.db_cluster_instance_class
  db_cluster_db_instance_parameter_group_name = var.db_cluster_db_instance_parameter_group_name
  deletion_protection                         = var.deletion_protection
  enable_global_write_forwarding              = var.enable_global_write_forwarding
  enabled_cloudwatch_logs_exports             = var.enabled_cloudwatch_logs_exports
  enable_http_endpoint                        = var.enable_http_endpoint
  engine                                      = var.engine
  engine_mode                                 = var.engine_mode
  engine_version                              = var.engine_version
  final_snapshot_identifier                   = var.final_snapshot_identifier
  global_cluster_identifier                   = var.global_cluster_identifier
  iam_database_authentication_enabled         = var.iam_database_authentication_enabled
  iops                                        = var.iops
  kms_key_id                                  = var.kms_key_id
  manage_master_user_password                 = false
  master_user_secret_kms_key_id               = var.master_user_secret_kms_key_id
  master_password                             = random_password.master.result
  master_username                             = var.master_username
  network_type                                = var.network_type
  port                                        = var.port
  preferred_backup_window                     = var.preferred_backup_window
  preferred_maintenance_window                = var.preferred_maintenance_window
  replication_source_identifier               = var.replication_source_identifier
  restore_to_point_in_time                    = var.restore_to_point_in_time
  s3_import                                   = var.s3_import
  scaling_configuration                       = var.scaling_configuration
  serverlessv2_scaling_configuration          = var.serverlessv2_scaling_configuration
  skip_final_snapshot                         = var.skip_final_snapshot
  snapshot_identifier                         = var.snapshot_identifier
  source_region                               = var.source_region
  storage_encrypted                           = var.storage_encrypted
  storage_type                                = var.storage_type
  cluster_tags                                = var.cluster_tags
  vpc_security_group_ids                      = var.vpc_security_group_ids
  cluster_timeouts                            = var.cluster_timeouts
  instances                                   = var.instances
  auto_minor_version_upgrade                  = var.auto_minor_version_upgrade
  ca_cert_identifier                          = var.ca_cert_identifier
  db_parameter_group_name                     = var.db_parameter_group_name
  instances_use_identifier_prefix             = var.instances_use_identifier_prefix
  instance_class                              = var.instance_class
  monitoring_interval                         = var.monitoring_interval
  performance_insights_enabled                = var.performance_insights_enabled
  performance_insights_kms_key_id             = var.performance_insights_kms_key_id
  performance_insights_retention_period       = var.performance_insights_retention_period
  publicly_accessible                         = var.publicly_accessible
  instance_timeouts                           = var.instance_timeouts
  endpoints                                   = var.endpoints
  iam_roles                                   = var.iam_roles
  create_monitoring_role                      = var.create_monitoring_role
  monitoring_role_arn                         = var.monitoring_role_arn
  iam_role_name                               = var.iam_role_name
  iam_role_use_name_prefix                    = var.iam_role_use_name_prefix
  iam_role_description                        = var.iam_role_description
  iam_role_path                               = var.iam_role_path
  iam_role_managed_policy_arns                = var.iam_role_managed_policy_arns
  iam_role_permissions_boundary               = var.iam_role_permissions_boundary
  iam_role_force_detach_policies              = var.iam_role_force_detach_policies
  iam_role_max_session_duration               = var.iam_role_max_session_duration
  autoscaling_enabled                         = var.autoscaling_enabled
  autoscaling_max_capacity                    = var.autoscaling_max_capacity
  autoscaling_min_capacity                    = var.autoscaling_min_capacity
  autoscaling_policy_name                     = var.autoscaling_policy_name
  predefined_metric_type                      = var.predefined_metric_type
  autoscaling_scale_in_cooldown               = var.autoscaling_scale_in_cooldown
  autoscaling_scale_out_cooldown              = var.autoscaling_scale_out_cooldown
  autoscaling_target_cpu                      = var.autoscaling_target_cpu
  autoscaling_target_connections              = var.autoscaling_target_connections
  create_security_group                       = var.create_security_group
  security_group_use_name_prefix              = var.security_group_use_name_prefix

  security_group_tags                        = var.security_group_tags
  security_group_description                 = var.security_group_description
  create_db_cluster_parameter_group          = var.create_db_cluster_parameter_group
  db_cluster_parameter_group_name            = var.db_cluster_parameter_group_name
  db_cluster_parameter_group_use_name_prefix = var.db_cluster_parameter_group_use_name_prefix
  db_cluster_parameter_group_description     = var.db_cluster_parameter_group_description
  db_cluster_parameter_group_family          = var.db_cluster_parameter_group_family
  db_cluster_parameter_group_parameters      = var.db_cluster_parameter_group_parameters
  create_db_parameter_group                  = var.create_db_parameter_group
  db_parameter_group_use_name_prefix         = var.db_parameter_group_use_name_prefix
  db_parameter_group_description             = var.db_parameter_group_description
  db_parameter_group_family                  = var.db_parameter_group_family
  db_parameter_group_parameters              = var.db_parameter_group_parameters
  putin_khuylo                               = var.putin_khuylo
  create_cloudwatch_log_group                = var.create_cloudwatch_log_group
  cloudwatch_log_group_retention_in_days     = var.cloudwatch_log_group_retention_in_days
  cloudwatch_log_group_kms_key_id            = var.cloudwatch_log_group_kms_key_id

  security_group_rules = merge(
    var.security_group_rules,
    {
      allow_ingress = {
        source_security_group_id = aws_security_group.allow_ingress.id
      }
    },
    var.allow_vpn_ingress ? {
      allow_vpn_ingress = {
        cidr_blocks = var.vpn_cidr_blocks
      }
    } : {}
  )
}

resource "aws_security_group" "allow_ingress" {
  vpc_id = module.vpc.vpc_id
  tags = {
    Name = "${module.this.id}-db-allow-ingress"
  }
}

resource "aws_security_group_rule" "allow_ingress" {
  security_group_id        = aws_security_group.allow_ingress.id
  from_port                = 3306
  to_port                  = 3306
  protocol                 = "tcp"
  type                     = "egress"
  source_security_group_id = module.cluster.security_group_id
}

resource "random_password" "master" {
  length  = 20
  special = false
}

resource "aws_secretsmanager_secret" "master_password" {
  name = "${module.this.id_slash}/db/master_password"

  tags = {
    DatabaseName : module.this.id
  }
}

resource "aws_secretsmanager_secret_version" "master_password" {
  secret_id     = aws_secretsmanager_secret.master_password.id
  secret_string = random_password.master.result
}
