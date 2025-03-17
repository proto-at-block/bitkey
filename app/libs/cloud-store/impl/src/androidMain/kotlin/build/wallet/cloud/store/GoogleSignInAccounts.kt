package build.wallet.cloud.store

import android.accounts.Account
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.toResultOr
import com.google.android.gms.auth.api.signin.GoogleSignInAccount

internal fun GoogleSignInAccount.androidAccount(): Result<Account, GoogleDriveError> =
  account.toResultOr { GoogleDriveError(message = "GoogleSignInAccount.account is null for $this") }
