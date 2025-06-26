package build.wallet.cloud.backup.csek

class SekGeneratorMock : SekGenerator {
  var csek: Csek = CsekFake

  override suspend fun generate(): Csek = csek
}
