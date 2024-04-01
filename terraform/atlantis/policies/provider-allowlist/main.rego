package main

provider_allowlist = {
    "registry.terraform.io/cloudflare/cloudflare",
    "registry.terraform.io/datadog/datadog",
    "registry.terraform.io/hashicorp/archive",
    "registry.terraform.io/hashicorp/aws",
    "registry.terraform.io/hashicorp/github",
    "registry.terraform.io/hashicorp/local",
    "registry.terraform.io/hashicorp/null",
    "registry.terraform.io/hashicorp/random",
    "registry.terraform.io/hashicorp/time",
}

denied_providers[name] {
    provider := input.provider_schemas[name]
    not provider_allowlist[name]
}

deny[msg] {
	count(denied_providers) > 0
	msg := sprintf("found providers not in allowlist: %s", [denied_providers])
}
