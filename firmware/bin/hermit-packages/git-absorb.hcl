description = "git commit --fixup, but automatic"
homepage = "https://github.com/tummychow/git-absorb"
binaries = ["git-absorb"]
test = "git-absorb --version"
strip = 1

platform "darwin" {
  source = "https://github.com/tummychow/git-absorb/releases/download/${version}/git-absorb-${version}-x86_64-apple-darwin.tar.gz"
}

platform "linux" {
  source = "https://github.com/tummychow/git-absorb/releases/download/v${version}/git-absorb-${version}-x86_64-unknown-linux-musl.tar.gz"
}

version "0.6.10" {
  auto-version {
    github-release = "tummychow/git-absorb"
  }
}
