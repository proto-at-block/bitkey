package build.wallet.frost

import build.wallet.rust.core.SharePackage as FfiSharePackage

data class SharePackageImpl(
  val ffiSharePackage: FfiSharePackage,
) : SharePackage
