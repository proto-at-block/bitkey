package build.wallet.frost

import build.wallet.rust.core.SharePackage as FfiSharePackage

class SharePackageImpl(
  val ffiSharePackage: FfiSharePackage,
) : SharePackage
