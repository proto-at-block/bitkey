description = "Doxygen is the de facto standard tool for generating documentation from annotated C++ sources."
test = "doxygen --help"
binaries = ["doxygen"]

platform "darwin" {
  source = "https://www.doxygen.nl/files/Doxygen-${version}.dmg"
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
  source = "https://www.doxygen.nl/files/doxygen-${version}.linux.bin.tar.gz"
  strip = 0

  on "unpack" {
    rename {
      from = "${root}/doxygen-${version}/bin/doxygen"
      to = "${root}/doxygen"
    }
  }
}

version "1.15.0" {
  auto-version {
    github-release = "doxygen/doxygen"
  }
}

sha256sums = {
  "https://www.doxygen.nl/files/doxygen-1.15.0.linux.bin.tar.gz": "0ec2e5b2c3cd82b7106d19cb42d8466450730b8cb7a9e85af712be38bf4523a1",
  "https://www.doxygen.nl/files/Doxygen-1.15.0.dmg": "b7630eaa0d97bb50b0333929ef5dc1c18f9e38faf1e22dca3166189a9718faf0",
}
