package build.wallet.feature

interface FeatureFlagSyncer {
  /**
   * Syncs the local feature flags' values with updated remote values from f8e.
   *
   * It is expected that this sync function will be invoked:
   *  1. on every cold launch of the app
   *  2. on every foregrounding of the app
   *
   * Note that by design, feature flag values are only read once at time of cold launch; so if new
   * values are synced they are only actually 'used' on the next cold launch of the app.
   *
   * See
   * https://docs.google.com/document/d/1NEzikJQrQDIdvkwSSemAFJYeB3P7CJ33nQL577N5iHY for detailed
   * syncing behavior specifications.
   */
  suspend fun sync()
}
