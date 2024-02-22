package build.wallet.platform.sharing

/** Provides functions for sharing content, e.g. from a "share" button. */
interface SharingManager {
  /** Shared code that triggers the platform-specific share sheet.
   *  @param text: Text to share
   *  @param title: Title of the share sheet
   *  @param completion: Callback for success or failure
   */
  fun shareText(
    text: String,
    title: String,
    completion: ((Boolean) -> Unit)?,
  )

  /**
   * Used to call the optional completion block when a share sheet action
   * has been selected.
   */
  fun completed()
}

fun SharingManager.shareInvitation(
  inviteCode: String,
  onCompletion: () -> Unit = {},
) {
  shareText(
    """Hey! I'm setting you up as a Trusted Contact in my Bitkey bitcoin wallet. If my device ever gets lost, you'd be a huge help with the recovery. 
                
Could you download the app and enter this invite code when you sign up?
                
Here's the link: https://bitkey.world/links/downloads/trusted-contact?code=$inviteCode and the invite code: $inviteCode. Thanks a ton!""",
    "BitKey Trusted Contact"
  ) { success ->
    if (success) {
      onCompletion()
    }
  }
}
