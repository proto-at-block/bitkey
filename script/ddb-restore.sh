#!/bin/bash

# Function to display usage information
display_usage() {
  echo "Usage: $0 [options]"
  echo "Options:"
  echo "  --original-table-name <table-name>   Original DynamoDB table name (required)"
  echo "  --region <aws-region>                AWS region (default: us-west-2)"
  echo "  --dry-run                            Enable dry-run mode (no changes will be made)"
  echo "  --restore-date-time <timestamp>      Timestamp for point-in-time restore (ISO 8601 format)"
  echo "  -h, --help                           Display this usage information"
  exit 1
}

# Default Configuration
original_table_name=""
region="us-west-2"
dry_run=false
restore_date_time=""
use_latest_restorable_time="true"

restore_backup_table() {
  restored_table_name="$original_table_name-restore"
  echo "Restoring backup table as $restored_table_name..."
  if [ "$use_latest_restorable_time" = "true" ]; then
    aws dynamodb restore-table-to-point-in-time --source-table-name "$original_table_name" --target-table-name "$restored_table_name" --region "$region" --use-latest-restorable-time --no-cli-pager
  else
    aws dynamodb restore-table-to-point-in-time --source-table-name "$original_table_name" --target-table-name "$restored_table_name" --region "$region" --restore-date-time "$restore_date_time" --no-cli-pager
  fi

  echo "Restoring backup to $restored_table_name. This might take a few minutes... If it times out, try it again"
  aws dynamodb wait table-exists --table-name "$restored_table_name" --region "$region"
}

copy_missing_items() {
  echo "Copying missing items from backup to original..."

  # Scan both tables and extract item keys
  aws dynamodb scan --table-name "$original_table_name" --region "$region" | jq -c '.Items[]' >original_table_items.json
  aws dynamodb scan --table-name "$restored_table_name" --region "$region" | jq -c '.Items[]' >restored_table_items.json

  # Find missing items
  missing_items=()
  while IFS= read -r item; do
    if ! grep -q "$item" original_table_items.json; then
      missing_items+=("$item")
    fi
  done <restored_table_items.json

  if [ "$dry_run" = true ]; then
    echo "Dry run mode - items would be copied:"
    echo "${missing_items[@]}" | jq .
  else
    # Copy missing items
    for item in "${missing_items[@]}"; do
      aws dynamodb put-item --table-name "$original_table_name" --item "$item" --region "$region"
    done
    echo "Items copied successfully."
  fi

  rm original_table_items.json
  rm restored_table_items.json
}

# Main entrypoint, cli args and dispatch
while [ $# -gt 0 ]; do
  case "$1" in
    --original-table-name)
      original_table_name="$2"
      shift 2
      ;;
    --region)
      region="$2"
      shift 2
      ;;
    --dry-run)
      dry_run=true
      shift
      ;;
    --restore-date-time)
      restore_date_time="$2"
      use_latest_restorable_time="false"
      shift 2
      ;;
    -h|--help)
      display_usage
      ;;
    *)
      echo "Error: Invalid option: $1"
      display_usage
      ;;
  esac
done

if [ -z "$original_table_name" ]; then
  echo "Error: --original-table-name is required."
  display_usage
  exit 1
fi

if [ "$dry_run" = true ]; then
  echo "Dry run mode enabled. No changes will be made."
fi

restore_backup_table
copy_missing_items

echo "Script execution complete."
