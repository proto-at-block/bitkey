variable "namespace" {
  type        = string
  description = "A namespace to depoy all resources under, prefixed to all names to avoid collision"
}

variable "enclave_role_arn" {
  type        = string
  description = "Role of the user that will be encrypting / decrypting using the key"
}

variable "enclave_attestation_pcr0" {
  type        = string
  description = "Value of the PCR0 register for the application deployed to the nitro enclave"
  default     = "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
}

