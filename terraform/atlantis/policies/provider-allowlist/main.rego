package main

import rego.v1

provider_allowlist := {
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

denied_providers contains name if {
    some name in object.keys(input.provider_schemas)
    not name in provider_allowlist
}

deny contains msg if {
    count(denied_providers) > 0
    msg := sprintf("found providers not in allowlist: %s", [denied_providers])
}
