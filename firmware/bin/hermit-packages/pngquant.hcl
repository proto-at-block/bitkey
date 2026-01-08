description = "pngquant — lossy PNG compressor"
test = "pngquant --version"
binaries = ["pngquant"]

platform "darwin" {
  source = "https://pngquant.org/pngquant.tar.bz2"
  strip = 0
}

platform "linux" "amd64" {
  source = "https://pngquant.org/pngquant-linux.tar.bz2"
  strip = 0
}

version "3.0.3" {}