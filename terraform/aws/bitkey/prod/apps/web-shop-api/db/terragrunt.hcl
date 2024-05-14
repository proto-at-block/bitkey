include "root" {
  path = find_in_parent_folders()
}

include "common" {
  path = find_in_parent_folders("common.hcl")
}

terraform {
  source = "${get_path_to_repo_root()}/modules//pieces/aurora-mysql-db"
}

inputs = {
  name                       = "web-shop-api"
  database_name              = "web_shop_api"
  create_db_subnet_group     = true
  db_subnet_group_name       = "web-shop-api"
  engine                     = "aurora-mysql"
  engine_version             = "8.0.mysql_aurora.3.06.0"
  auto_minor_version_upgrade = false
  master_username            = "admin"
  instance_class             = "db.r7g.large"
  skip_final_snapshot        = true
  apply_immediately          = true

  instances = {
    1 = {}
    2 = {}
  }
}