description = "Criterion is a cross-platform C and C++ unit testing framework."
homepage = "https://github.com/Snaipe/Criterion"
binaries = ["criterion-root"]
test = "criterion-root"

platform "darwin" {
  source = "file://${HERMIT_ENV}/bin/hermit-packages/criterion/criterion-root"
  sha256 = "07ec1df7dd4fc26da47710cff9dc1dd89e964e7edec77606dc48bb12ddc858f7"
  dont-extract = true

  on "unpack" {
    chmod {
      file = "${root}/criterion-root"
      mode = 448
    }
  }
}

platform "linux" "amd64" {
  source = "https://github.com/Snaipe/Criterion/releases/download/v${version}/criterion-${version}-linux-x86_64.tar.xz"
  sha256 = "f88ef23e2426e705a19699cd606c1283001378827c9b0c6feec0c3912165ac60"
  strip = 1
  env = {
    "CRITERION_ROOT": "${root}",
  }

  on "unpack" {
    copy {
      from = "criterion/criterion-root"
      to = "${root}/criterion-root"
      mode = 448
    }
  }
}

version "2.4.2" {}
