# Defines ACM certificates for external domains managed in https://github.com/squareup/tf-external-dns
#
# Each item is a separate certificate we want ACM to issue. The terraform output
# will display CNAME records that we need to add to the tf-external-dns repo to
# validate the certificate. ACM will issue the certificate automatically after
# the validation CNAME records are live.
#
# After the certificates are issued, they can be added to ALBs as alternate certs.

include {
  path = find_in_parent_folders()
}

terraform {
  source = "git::https://github.com/terraform-aws-modules/terraform-aws-acm//wrappers?ref=v4.3.2"
}

inputs = {
  defaults = {
    create_route53_records = false
    validation_method      = "DNS"
    wait_for_validation    = false
  }
  items = {
    "api" = {
      domain_name = "api.bitkey.build"
    },
    "links" = {
      domain_name = "links.bitkey.build"
    },
    "beta" = {
      domain_name = "beta.bitkey.build"
    },
    "support" = {
      domain_name = "support.bitkey.build"
    },
    "site" = {
      domain_name = "bitkey.world"
    },
    "www_bitkey_world" = {
      domain_name = "www.bitkey.world"
    },
    "links_bitkey_world" = {
      domain_name = "links.bitkey.world"
    },
    "track_bitkey_world" = {
      domain_name = "track.bitkey.world"
    },
    "returns_bitkey_world" = {
      domain_name = "returns.bitkey.world"
    },
    "blog_bitkey_world" = {
      domain_name = "blog.bitkey.world"
    }
  }
}
