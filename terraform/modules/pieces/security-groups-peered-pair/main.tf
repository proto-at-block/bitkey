# The security-groups-peered-pair module creates a peered pair of
# security groups, where ENIs with the target security group applied
# will allow ingress from ENIs with the source security group. This
# allows us to allow ingress from specific services with the source
# security group attached instead of from IP lists.

resource "aws_security_group" "source" {
  name   = "${var.name_prefix}-source"
  vpc_id = var.vpc_id
}

resource "aws_security_group" "target" {
  name   = "${var.name_prefix}-target"
  vpc_id = var.vpc_id
}

# On the source, allow egress to target
resource "aws_security_group_rule" "source_egress" {
  security_group_id = aws_security_group.source.id

  type                     = "egress"
  from_port                = var.port
  protocol                 = var.protocol
  to_port                  = var.port
  source_security_group_id = aws_security_group.target.id
}

# On the target, allow ingress from source
resource "aws_security_group_rule" "target_ingress" {
  security_group_id = aws_security_group.target.id

  type                     = "ingress"
  from_port                = var.port
  protocol                 = var.protocol
  to_port                  = var.port
  source_security_group_id = aws_security_group.source.id
}