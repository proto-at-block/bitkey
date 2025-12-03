variable "name" {
  type        = string
  description = "The name of the build cache S3 bucket"
}

variable "bucket_access_principals" {
  type        = list(string)
  default     = []
  description = "Optional IAM principal ARNs that receive read/write + list access to the build cache bucket"
}
