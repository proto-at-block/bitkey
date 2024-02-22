#!/bin/bash

CID="1234"
REGION="us-east-1"
PROXY_PORT="8000"

TOKEN=$(curl -X PUT "http://169.254.169.254/latest/api/token" -H "X-aws-ec2-metadata-token-ttl-seconds: 21600")
AUTH_BLOB=$(curl -H "X-aws-ec2-metadata-token: $TOKEN" http://169.254.169.254/latest/meta-data/iam/security-credentials/aes-enclave-dev-instance)
akid=$(echo $AUTH_BLOB | jq -r .AccessKeyId)
skid=$(echo $AUTH_BLOB | jq -r .SecretAccessKey)
token=$(echo $AUTH_BLOB | jq -r .Token)

MASTER_KEY="AQIDAHhiR5Mi+J0Im1NE4L4v+FypEErjlrJi33jhCJGgTaaZpwEIDiqH/8R3x8mn+HM0Cdo2AAAAfjB8BgkqhkiG9w0BBwagbzBtAgEAMGgGCSqGSIb3DQEHATAeBglghkgBZQMEAS4wEQQM7UkMvIGoUFxH6ExCAgEQgDvhs4TZ9iCsQFOOl37g6havJGFLvE0+eZtfLHtPz1gFiMpRoko1Fr7eLPi6LNrHLEwI0q64ypokiO63Jg=="

./wsm-enclave load-secret $CID $REGION $PROXY_PORT $akid $skid $token $MASTER_KEY