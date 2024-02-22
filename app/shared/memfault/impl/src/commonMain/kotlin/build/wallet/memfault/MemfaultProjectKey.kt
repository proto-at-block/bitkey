package build.wallet.memfault

// Project keys are not considered secrets, but IMO it's best to not release them directly.
// It's still easy to reverse the app to find this.
// https://docs.memfault.com/docs/platform/data-routes/
// https://docs.memfault.com/docs/ci/authentication/#project-key
class MemfaultProjectKey {
  companion object {
    const val MEMFAULT_PROJECT_KEY =
      "cuMF7SryHhQQcs2gcuEaHqDWV0Z43ha4"
  }
}
