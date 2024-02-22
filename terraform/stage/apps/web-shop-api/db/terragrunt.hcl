include "root" {
  path = find_in_parent_folders()
}

include "common" {
  path = find_in_parent_folders("common.hcl")
}

terraform {
  source = "${get_parent_terragrunt_dir("root")}//modules/pieces/aurora-mysql-db"
}

inputs = {
  name                       = "web-shop-api"
  database_name              = "web_shop_api"
  create_db_subnet_group     = true
  db_subnet_group_name       = "web-shop-api"
  engine                     = "aurora-mysql"
  engine_version             = "8.0.mysql_aurora.3.05.1"
  auto_minor_version_upgrade = false
  master_username            = "admin"
  instance_class             = "db.t4g.medium"
  skip_final_snapshot        = true
  allow_vpn_ingress          = true

  instances = {
    1 = {}
  }
}