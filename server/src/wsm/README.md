# WSM - The W1 Security Module

## Running WSM on your desktop for development/testing
1. Use the aws CLI to create a local DDB table called `wsm_customer_keys` with a partition key called `root_key_id`.
2. in one terminal window (or tmux frame or whatever floats your boat) do `ROCKET_PROFILE=test ROCKET_PORT=7446 cargo run --bin wsm-enclave`. The `test` profile uses a mocked KMS.
3. in another terminal window (or screen frame or whatever floats your boat) do `ROCKET_PROFILE=test ROCKET_PORT=8446 AWS_PROFILE=bitkey-development--admin AWS_REGION=us-west-2 cargo run --bin wsm-api`
4. in a third terminal window (or emacs `ansi-term` frame or whatever floats your boat) do `curl -H 'Content-Type: application/json' -d '{"root_key_id": "hello", "network": "signet"}' localhost:8446/create-key` and you will see that your fake wsm has created a key!

## Running WSM Tests
To run tests on your changes to WSM, run `cargo +nightly test`. By default, when running `wsm-enclave` tests it will talk to the dev deployment of `wsm-api`. To override it to talk to your local `wsm-api`, you will need to add the following enviornment variable: `SERVER_WSM_ENDPOINT="http://localhost:8080"`.

## Debugging a WSM instance
Once we have (log shipping)[https://github.com/squareup/wallet/issues/957] implemented, you'll be able to go into cloudwatch logs and pull wsm-api logs. Until then, the easiest way to debug is _on_ the instance. Here are some helpful tips:
- go into the AWS console, find the autoscaling group for your WSM deployment, navigate to the Launch Configuration, and add your ssh key. Then update the autoscaling group to use the new launchconfig. Finally, go kill the WSM EC2 instance. Autoscaling will make a new one with your SSH key. handy! Add a security group rule to allow SSH from your IP and you can SSH in.
- on the instance, run `journalctl -xef -n 50 -t wsm-api` to follow the wsm-api logs (it's like `tail -f` but for journald logs)
- on the instance, run `nitro-cli console --enclave-id $(nitro-cli describe-enclaves | jq -r '.[0].EnclaveID')` to follow the log of the enclave. This *ONLY* works if the enclave is running in `debug` mode. If its not running in debug mode, stop the running enclave and run `/usr/bin/nitro-cli run-enclave --cpu-count=2 --memory=512 --eif-path=/opt/wsm/wsm-enclave.eif --enclave-cid=1234 --debug-mode`
- you can start/stop/restart the api server and the enclave application with systemctl. For example, `systemctl restart wsm-api` or `systemctl stop wsm-enclave`

## Verifying wsm-enclave images
### Build or download the kmstool artifacts
todo...
### Download the image to be verified
todo...
### Verify the image
`./verify_enclave_container.sh`

This will take a long time. On the order of 10-15 minutes. That's because there's a docker container running in a VM doing user-space x86 emulation to build the rust binary. It's slow.
### (If it's for PROD) Sign the PCRs
todo...

## Runtime config

### Specifying dependency identifiers
there are things like DDB tables that need names wired in. See wsm/wsm-api/src/settings.rs:7 for the fields that can be set. The config loader will look (in order) at:
- `config/default.toml`
- `config/$ROCKET_PROFILE.toml`
- `WSM_API_$name` env variables (for example, `WSM_API_DEK_TABLE_NAME`)

## Frequently Asked Questions (FAQ)

