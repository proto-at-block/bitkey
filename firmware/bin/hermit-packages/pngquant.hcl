description = "pngquant — lossy PNG compressor"
test = "pngquant --version"
binaries = ["pngquant"]

platform "darwin" {
  source = "file://${HERMIT_ENV}/third-party/pngquant/pngquant-darwin-${version}.tar.bz2"
  strip = 0
}

platform "linux" "amd64" {
  source = "file://${HERMIT_ENV}/third-party/pngquant/pngquant-linux-${version}.tar.bz2"
  strip = 0
}

sha256sums = {
  "file://${HERMIT_ENV}/third-party/pngquant/pngquant-darwin-3.0.3.tar.bz2": "68c32e4988d3f99f79f0642a94900756b7a2a463166067c5862826ced1e0ce1e",
  "file://${HERMIT_ENV}/third-party/pngquant/pngquant-linux-3.0.3.tar.bz2": "25d09032f48760d34397155f3267ffadfbbbaeb2b8b39496022fc2ef1d82529e",
}

version "3.0.3" {}
