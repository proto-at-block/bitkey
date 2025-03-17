package build.wallet.cloud.backup.csek

interface CsekGenerator {
  suspend fun generate(): Csek
}
