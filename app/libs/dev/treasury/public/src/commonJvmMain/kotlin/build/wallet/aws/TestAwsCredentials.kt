package build.wallet.aws

import aws.sdk.kotlin.runtime.auth.credentials.ProfileCredentialsProvider
import aws.sdk.kotlin.runtime.client.AwsSdkClientConfig
import aws.sdk.kotlin.runtime.config.AbstractAwsSdkClientFactory
import aws.smithy.kotlin.runtime.auth.awscredentials.CredentialsProviderConfig
import aws.smithy.kotlin.runtime.client.SdkClient
import aws.smithy.kotlin.runtime.client.SdkClientConfig

/**
 * Configure an AWS client with default credentials for testing. If environment variables are
 * provided (e.g. in CI), allow them to override these defaults.
 */
suspend fun <
  TConfig,
  TConfigBuilder,
  TClient : SdkClient,
  TClientBuilder : SdkClient.Builder<TConfig, TConfigBuilder, TClient>,
> AbstractAwsSdkClientFactory<TConfig, TConfigBuilder, TClient, TClientBuilder>.fromEnvironmentWithTestDefaults(
  block: (TConfigBuilder.() -> Unit)? = null,
)
    where
          TConfig : SdkClientConfig,
          TConfig : AwsSdkClientConfig,
          TConfigBuilder : SdkClientConfig.Builder<TConfig>,
          TConfigBuilder : AwsSdkClientConfig.Builder,
          TConfigBuilder : CredentialsProviderConfig.Builder =
  fromEnvironment {
    if (System.getenv("AWS_PROFILE") == null && System.getenv("AWS_ACCESS_KEY_ID") == null) {
      @Suppress("OPT_IN_USAGE_FUTURE_ERROR")
      credentialsProvider = ProfileCredentialsProvider(profileName = "bitkey-development--admin")
    }
    if (System.getenv("AWS_REGION") == null) {
      region = "us-west-2"
    }
    block?.invoke(this)
  }
