package build.wallet.cloud.backup.csek

class CsekGeneratorMock : CsekGenerator {
  var csek: Csek = CsekFake

  override suspend fun generate(): Csek = csek
}
