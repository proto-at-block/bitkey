terraform {
  required_providers {
    aws = {
      version = "~>5.0"
      source  = "hashicorp/aws"
    }
    datadog = {
      version = "~>3.26"
      source  = "DataDog/datadog"
    }
  }
}