libwally_base = 'libwally/'
libwally_src = libwally_base + 'src'

# Copy config.h to libwally directories during build
configure_file(
  input: '../libwally_config.h',
  output: 'config.h',
  copy: true,
  install: false,
)

# Also copy to src/ directory for libwally includes
configure_file(
  input: '../libwally_config.h',
  output: 'src_config.h',
  copy: true,
  install: false,
  install_dir: libwally_src,
)

libwally_srcs = [
  libwally_src / 'address.c',
  libwally_src / 'base_58.c',
  libwally_src / 'base_64.c',
  libwally_src / 'bech32.c',
  libwally_src / 'hex_.c',
  libwally_src / 'hmac.c',
  libwally_src / 'internal.c',
  libwally_src / 'pbkdf2.c',
  libwally_src / 'aes.c',
  libwally_src / 'scrypt.c',
  libwally_src / 'bip32.c',
  libwally_src / 'psbt.c',
  libwally_src / 'pullpush.c',
  libwally_src / 'script.c',
  libwally_src / 'sign.c',
  libwally_src / 'transaction.c',
  libwally_src / 'tx_io.c',
  libwally_src / 'wif.c',
  libwally_src / 'map.c',
  libwally_src / 'symmetric.c',
  libwally_src / 'ecdh.c',
  libwally_src / 'ccan/ccan/base64/base64.c',
  libwally_src / 'ccan/ccan/crypto/ripemd160/ripemd160.c',
  libwally_src / 'ccan/ccan/crypto/sha256/sha256.c',
  libwally_src / 'ccan/ccan/crypto/sha512/sha512.c',
  libwally_src / 'ccan/ccan/str/hex/hex.c',
]

libwally_includes = [
  include_directories(libwally_base + 'include'),
  include_directories(libwally_base + 'src'),
  libew_secp256k1_include_dir, # Integrator must set this
  include_directories(libwally_base + 'src/ccan'),
  include_directories(libwally_base),
  include_directories('.'),
]

c_args = [
  '-DHAVE_CONFIG_H',
  '-DBUILD_STANDARD_SECP=1',
  # Warning suppressions
  '-Wno-missing-variable-declarations',
  '-Wno-implicit-int-conversion',
  '-Wno-switch-default',
  '-Wno-unused-function',
  '-Wno-unused-parameter',
  '-Wno-sign-conversion',
  '-Wno-cast-qual',
  '-Wno-reserved-macro-identifier', # For CCAN macro with _ prefix
  '-Wno-reserved-identifier', # For error: identifier '_ops' is reserved because it starts with '_' at global scope in libwally/src/internal.c
]

libwally_lib = static_library(
  'wally',
  libwally_srcs,
  include_directories: libwally_includes,
  c_args: c_args,
  install: false,
)

libwally_dep = declare_dependency(include_directories: libwally_includes, link_whole: libwally_lib)
