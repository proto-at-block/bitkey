#!/usr/bin/env sh

# move-device-cohort.sh
#
# External customers and team members receive updates via different Memfault "cohorts".
# This script moves a device between those cohorts.
#
# For example, we may release a new firmware version to the internal team before releasing it to the public.
# If you want to get that update, you can use this script to move to that cohort.

# Exit immediately if a command exits with a non-zero status, and print commands and their arguments as they are executed
set -euo pipefail

# Usage function to display help
usage() {
  echo "Usage: $0 -d <device_serial> -c <cohort_name>"
  echo "Valid cohorts are: bitkey-team, bitkey-external-beta"
  echo "The 'bitkey-external-beta' cohort is actually for all external customers, not just beta."
  exit 1
}

# Initialize parameters
device_serial=""
cohort_name=""

# Parse command line options
while getopts ":d:c:" opt; do
  case ${opt} in
    d )
      device_serial=$OPTARG
      ;;
    c )
      cohort_name=$OPTARG
      ;;
    \? )
      echo "Invalid option: $OPTARG" 1>&2
      usage
      ;;
    : )
      echo "Invalid option: $OPTARG requires an argument" 1>&2
      usage
      ;;
  esac
done
shift $((OPTIND -1))

# Validate required parameters
if [ -z "${device_serial}" ] || [ -z "${cohort_name}" ]; then
    echo "Missing required parameters."
    usage
fi

# Fetch and encode the auth token
auth_token=$(AWS_PROFILE=w1-development--admin aws secretsmanager get-secret-value --region us-west-2 \
  --secret-id memfault_dev_comms_token | jq -r '.SecretString | fromjson | .memfault_dev_comms_token')
encoded_token=$(printf ":%s" "$auth_token" | base64)

# Prepare the API endpoint
url="https://api.memfault.com/api/v0/organizations/block-wallet/projects/w1a/devices/${device_serial}"

# Make the PATCH request and capture the output and status
output=$(curl -s -X PATCH "${url}" \
  -H 'Content-Type: application/json' \
  -H 'Cache-Control: no-cache' \
  -H "Authorization: Basic ${encoded_token}" \
  -d "{
        \"cohort\": \"${cohort_name}\"
      }")

status=$?

# Check response status
if [ "$status" -ne 0 ]; then
    echo "Request failed with curl exit status $status"
    echo "$output"
    exit 1
else
    echo "$output"
fi