include {
  path = find_in_parent_folders()
}

terraform {
  source = "${get_path_to_repo_root()}/modules//models/gha-build-cache"
}

inputs = {
  name = "000000000000-bitkey-gha-build-cache"
  bucket_access_principals = [
    "arn:aws:iam::289402952023:role/mobuild-worker",
    "arn:aws:iam::336763625996:role/mobuild-worker",
    "arn:aws:iam::872569563541:role/mobuild-worker",
  ]
}
