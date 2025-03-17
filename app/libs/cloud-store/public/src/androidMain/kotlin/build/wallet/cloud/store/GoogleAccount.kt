package build.wallet.cloud.store

import com.google.android.gms.auth.api.signin.GoogleSignInAccount

data class GoogleAccount(
  val credentials: GoogleSignInAccount,
) : CloudStoreAccount
