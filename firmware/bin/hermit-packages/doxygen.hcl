description = "Doxygen is the de facto standard tool for generating documentation from annotated C++ sources."
test = "doxygen --help"
binaries = ["doxygen"]
vars = {
  release_tag: "Release_1_15_0"
}
version "1.15.0" {}

platform "darwin" {
  source = "https://github.com/doxygen/doxygen/releases/download/${release_tag}/Doxygen-${version}.dmg"
  sha256 = "b7630eaa0d97bb50b0333929ef5dc1c18f9e38faf1e22dca3166189a9718faf0"
  strip = 0
  dest = ""
  apps = [
    "Doxygen.app"
  ]

  on "unpack" {
    rename {
      from = "${root}/Doxygen.app/Contents/Resources/doxygen"
      to = "${root}/doxygen"
    }
  }
}

platform "linux" "amd64" {
  source = "https://github.com/doxygen/doxygen/releases/download/${release_tag}/doxygen-${version}.linux.bin.tar.gz"
  sha256 = "0ec2e5b2c3cd82b7106d19cb42d8466450730b8cb7a9e85af712be38bf4523a1"
  strip = 0

  on "unpack" {
    rename {
      from = "${root}/doxygen-${version}/bin/doxygen"
      to = "${root}/doxygen"
    }
  }
}
