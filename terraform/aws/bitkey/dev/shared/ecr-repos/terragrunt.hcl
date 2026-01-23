include "root" {
  path = find_in_parent_folders()
}

terraform {
  source = "${get_path_to_repo_root()}/modules//models/ecr-repos"
}

inputs = {
  repos = [
    "partnerships-cash-app-key-rotator",
    "wallet-api",
    "web-site",
    "wsm-api",
    "wsm-enclave",
    "bitkey-reproducible-android-builder",
    # Nix base images for CI caching (pre-warmed Nix shells)
    "nix-base-ci-wsm-build",
    "nix-base-ci-core-test",
    "nix-base-ci-jvm-rust",
    "nix-base-ci-android-build",
    "nix-base-lambda-build",
  ]
  image_tag_mutability = "MUTABLE"
}
