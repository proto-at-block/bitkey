variable "vpc_id" {
  type        = string
  description = "ID of VPC to configure the VPN attachments for"
}

variable "vpn_subnet_ids" {
  type        = list(string)
  description = "List of the VPN subnet IDs to attach VPN"
}

variable "create_vpn_routes" {
  type        = bool
  description = "Create VPN routes to the TGW"
  default     = false
}

variable "create_test_ec2_instance" {
  type        = bool
  description = "Create an EC2 instance for testing connectivity"
  default     = false
}

variable "vpn_route_table_ids" {
  type        = list(string)
  description = "List of the VPN route table IDs to add routes to the TGW"
}

variable "private_route_table_ids" {
  type        = list(string)
  description = "List of the private route table IDs to add routes to the TGW"
}

variable "private_subnet_ids" {
  type        = list(string)
  description = "List of the private subnet IDs available"
}

variable "tgw_id" {
  type        = string
  description = "ID of Transite Gateway to attach VPN subnets to"
}

variable "tgw_routes" {
  type        = list(string)
  description = "List of CIDR blocks to route to the TGW"
  default     = ["100.127.228.0/24", "100.127.229.0/24"]
}