#!/bin/bash
set -x
set +e # make sure we dont exit on the `which` failing
PKG_CMD=`which yum 2>/dev/null`
set -e # continue with failing on error
if [ -z "$PKG_CMD" ]; then
  PKG_CMD=apt-get
else
  PKG_CMD=yum
fi
$PKG_CMD update -y
set +e # make sure we dont exit on the next command failing (we check its exit code below)
$PKG_CMD install -y ruby2.0
RUBY2_INSTALL=$?
set -e # continue with failing on error
if [ $RUBY2_INSTALL -ne 0 ]; then
  $PKG_CMD install -y ruby
fi
AWS_CLI_PACKAGE_NAME=awscli
if [ "$PKG_CMD" = "yum" ]; then
  AWS_CLI_PACKAGE_NAME=aws-cli
fi
$PKG_CMD install -y $AWS_CLI_PACKAGE_NAME
wget -O ddinstall.sh https://s3.amazonaws.com/dd-agent/scripts/install_script_agent7.sh
export AWS_DEFAULT_REGION=${region}
export DD_API_KEY=$(aws ssm get-parameter --name /shared/datadog/api-key --with-decryption --query 'Parameter.Value' --output text)
bash ./ddinstall.sh
cat << EOF >> /etc/datadog-agent/datadog.yaml
logs_enabled: true
env: ${environment}
otlp_config:
  receiver:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317
EOF
TMP_DIR=`mktemp -d`
cd $TMP_DIR
aws s3 cp s3://aws-codedeploy-${region}/latest/install . --region ${region}
chmod +x ./install
./install auto
rm -fr $TMP_DIR