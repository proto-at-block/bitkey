description = "GNU ARM Embedded GDB with Python3"
test = "arm-none-eabi-gdb-py3 --version"
binaries = ["bin/arm-none-eabi-gdb-py3"]
strip = 1

platform "darwin" "arm64" {
  source = "https://github.com/xpack-dev-tools/arm-none-eabi-gcc-xpack/releases/download/v${version}/xpack-arm-none-eabi-gcc-${version}-darwin-arm64.tar.gz"
}

platform "darwin" "amd64" {
  source = "https://github.com/xpack-dev-tools/arm-none-eabi-gcc-xpack/releases/download/v${version}/xpack-arm-none-eabi-gcc-${version}-darwin-x64.tar.gz"
}

platform "linux" "amd64" {
  source = "https://github.com/xpack-dev-tools/arm-none-eabi-gcc-xpack/releases/download/v${version}/xpack-arm-none-eabi-gcc-${version}-linux-x64.tar.gz"
}

version "11.3.1-1.1" {
  auto-version {
    github-release = "xpack-dev-tools/arm-none-eabi-gcc-xpack"
  }
}
