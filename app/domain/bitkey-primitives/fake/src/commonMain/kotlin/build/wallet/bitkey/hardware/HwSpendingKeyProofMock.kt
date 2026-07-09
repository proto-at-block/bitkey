package build.wallet.bitkey.hardware

val HwSpendingKeyProofMock =
  HwSpendingKeyProof(
    signature = HwSpendingKeyAttestationSignature("sig-base64"),
    certChain =
      listOf(
        HwAttestationCertificate("identity-cert-base64"),
        HwAttestationCertificate("batch-cert-base64")
      )
  )
