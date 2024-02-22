#!/usr/bin/env bash

set -euo pipefail

GITHUB_RUN_NUMBER="${GITHUB_RUN_NUMBER:-$USER-dev}"
GITHUB_BASE_REF="${GITHUB_BASE_REF:-$USER-dev}"
# Set to enable enclave debug mode
ENCLAVE_DEBUG_MODE="${ENCLAVE_DEBUG_MODE:-}"
# Set to deploy a new version of the enclave
# If not set, download the currently live enclave image from CodeBuild and re-deploy that
ENCLAVE_DEPLOY_NEW="${ENCLAVE_DEPLOY_NEW:-}"

rm -rf build/deploy_bundle/config/
mkdir -p build/deploy_bundle/config/
cp config/default.toml build/deploy_bundle/config/
cp config/release.toml build/deploy_bundle/config/
cp -r wsm-infra/deployment/* build/deploy_bundle/
cp build/wsm-enclave.eif build/deploy_bundle/
cp build/wsm-api-bin build/deploy_bundle/
cp build/socat build/deploy_bundle/
cd build/deploy_bundle

export ENV_NAMESPACE=$1
export KEY_NAME=wsm-$GITHUB_RUN_NUMBER-$(git rev-parse HEAD).zip
SSM_PREFIX=""
APP_PREFIX=""
if [[ "${ENV_NAMESPACE}" != "default" ]]; then
  SSM_PREFIX="/$ENV_NAMESPACE"
  APP_PREFIX="${ENV_NAMESPACE}-"
fi

export BUCKET_NAME=$(aws ssm get-parameter --name "${SSM_PREFIX}"/wsm/artifact_bucket | jq -r '.Parameter.Value')
export KMS_KEY_ARN=$(aws ssm get-parameter --name "${SSM_PREFIX}"/wsm/key_arn | jq -r '.Parameter.Value')
export DEK_TABLE=$(aws ssm get-parameter --name "${SSM_PREFIX}"/wsm/dek_table | jq -r '.Parameter.Value')
export KEYS_TABLE=$(aws ssm get-parameter --name "${SSM_PREFIX}"/wsm/customer_server_keys_table | jq -r '.Parameter.Value')
  cat <<EOF >> config/release.toml
dekTableName = "$DEK_TABLE"
customerKeysTableName = "$KEYS_TABLE"
cmkId = "${KMS_KEY_ARN}"
EOF


if [[ -z "${ENCLAVE_DEBUG_MODE}" ]]; then
  ( set -euo pipefail
    echo "Disabling enclave debug mode"
    cd scripts
    sed "s/ --debug-mode//" wsm-enclave.service > wsm-enclave.tmp
    mv wsm-enclave.tmp wsm-enclave.service
  )
fi

if [[ -z "${ENCLAVE_DEPLOY_NEW}" ]]; then
  # Download the artifacts for the last successful deploy
  # Extract the wsm-enclave image and put it in the deploy bundle so that we re-deploy the current enclave
  # Other components like wsm-api and socat always deploy the new version
  ( set -euo pipefail
    cd ..
    deployment_id=$(aws deploy get-deployment-group --application-name ${APP_PREFIX}wsm-deploy --deployment-group-name ${APP_PREFIX}wsm-deploy --query 'deploymentGroupInfo.lastSuccessfulDeployment.deploymentId' --output text)
    if [[ $? -ne 0 ]]; then
      echo "failed to get active deployment: ${deployment_id}"
    fi
    key=$(aws deploy get-deployment --deployment-id ${deployment_id} --query 'deploymentInfo.revision.s3Location.key' --output text)
    if [[ $? -ne 0 ]]; then
      echo "failed to get artifact location: ${key}"
    fi
    echo "Downloading enclave image from active deploy ${deployment_id}"
    aws s3 cp s3://$BUCKET_NAME/$key active_deploy.zip
    rm -rf active_deploy
    mkdir -p active_deploy
    unzip -d active_deploy active_deploy.zip
    mv active_deploy/wsm-enclave.eif deploy_bundle/wsm-enclave.eif
  )
fi

# build zip file with deployment artifacts, scripts, and appconfig. Ship that to S3, and register the revision with CodeDeploy
aws deploy push --application-name=${APP_PREFIX}wsm-deploy --description="wsm deployment for $GITHUB_BASE_REF" --s3-location=s3://$BUCKET_NAME/$KEY_NAME
# tell code-deploy to deploy the new rev to our deployment group
export DEPLOYMENT_ID=$(aws deploy create-deployment --application-name ${APP_PREFIX}wsm-deploy --s3-location bucket=$BUCKET_NAME,key=$KEY_NAME,bundleType=zip --deployment-group-name ${APP_PREFIX}wsm-deploy --description "Deploying build for ref $GITHUB_BASE_REF and run $GITHUB_RUN_NUMBER" | jq -r .deploymentId)

echo ""
echo "Deploy ${DEPLOYMENT_ID} started, waiting for deploy to complete."
echo "https://${AWS_REGION}.console.aws.amazon.com/codesuite/codedeploy/deployments/${DEPLOYMENT_ID}"
if aws deploy wait deployment-successful --deployment-id $DEPLOYMENT_ID; then
  echo "Deploy successful!"
else
  echo "Deploy failed"
  exit 1
fi
