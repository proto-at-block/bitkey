variable "name_prefix" {
  type        = string
  description = "Name prefix for the security groups"
}

variable "port" {
  type        = number
  default     = 443
  description = "Port to allow ingress on"
}

variable "vpc_id" {
  type        = string
  description = "VPC to create the security groups in"
}

variable "protocol" {
  type        = string
  default     = "tcp"
  description = "Protocol. If not icmp, icmpv6, tcp, udp, or all, use the protocol number"
}