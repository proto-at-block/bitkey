package build.wallet.bitkey.socrec

sealed interface SocRecPhase {
  object Enrollment : SocRecPhase

  object Recovery : SocRecPhase
}
