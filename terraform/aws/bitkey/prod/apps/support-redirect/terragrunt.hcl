include "root" {
  path = find_in_parent_folders()
}

terraform {
  source = "${get_path_to_repo_root()}/modules//models/alb-redirect"
}

inputs = {
  namespace          = "default"
  name               = "support-redirect"
  origin_domain_name = "support.bitkey.build"
  target_domain_name = "support.bitkey.world"
  certificate_arn    = "arn:aws:acm:us-west-2:000000000000:certificate/b0715acd-666f-488c-bc13-d1d3cead4316"
}
