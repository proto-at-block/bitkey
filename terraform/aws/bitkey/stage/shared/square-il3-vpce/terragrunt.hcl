include {
  path = find_in_parent_folders()
}

terraform {
  source = "${get_path_to_repo_root()}/modules//models/square-il3-vpce"
}

inputs = {
  vpc_name        = "bitkey"
  api_gateway_arn = "arn:aws:execute-api:us-west-2:150521361774:ewxz0owxdb"
}
