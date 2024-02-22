module "this" {
  source    = "../namespacer"
  namespace = var.namespace
  name      = var.name
}

data "aws_rds_cluster" "this" {
  cluster_identifier = module.this.id
}

data "aws_secretsmanager_secret" "master_password" {
  name = "${module.this.id_slash}/db/master_password"
}

data "aws_security_group" "ingress_sg" {
  filter {
    name   = "tag:Name"
    values = ["${module.this.id}-db-allow-ingress"]
  }
}