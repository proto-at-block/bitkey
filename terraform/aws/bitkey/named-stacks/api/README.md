# API Named Stack

This is a namespaced stack for the API (fromagerie, wsm). It allows you to deploy your own set of
resources for testing into a separate environment. This stack must be run in 2 phases due to some nuances
to how Terraform resolves variables.

## Routes

API routes for the stack will be as following:
* fromagerie-api.`stack name`.dev.bitkeydevelopment.com
* api.`stack name`.dev.bitkeydevelopment.com
* wsm.`stack name`.dev.bitkeydevelopment.com	

## Deployment

To deploy a stack with your own image based on the local state and including the WSM, plase run `just stack-up` in this repository's `server` directory. To tear down the deployed stack, run `just stack-down`.

To deploy this stack:

1. Get the image tag for the fromagerie container you'd like to deploy. The latest builds are at https://github.com/squareup/wallet/actions/workflows/server.yml?query=branch%3Amain
2. Download the auth lambdas

    ```shell
    cd $REPO_ROOT/terraform/dev/deploy/auth
    just download_artifacts
    ```
   This will download the artifacts to `$REPO_ROOT/terraform/dev/deploy/auth/build`
3. Deploy fromagerie base
    ```shell
    export NAMESPACE=$USER
    terragrunt apply \
      -var fromagerie_image_tag=$IMAGE_TAG \
      -var auth_lambdas_dir=$REPO_ROOT/terraform/dev/deploy/auth/assets \
      -target module.fromagerie_base
    ```
   The path provided to auth_lambdas_dir must be an absolute path.

4. Deploy the rest of the resources (same as above without the `-target` flag)
    ```shell
    export NAMESPACE=$USER
    terragrunt apply \
      -var fromagerie_image_tag=$IMAGE_TAG \
      -var auth_lambdas_dir=$REPO_ROOT/terraform/dev/deploy/auth/assets
    ```

To destroy the named stack:

1. Destroy the stack
    ```shell
    export NAMESPACE=$USER
    terragrunt destroy
    ```
