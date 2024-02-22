base = '../../secp256k1/src'

secp256k1_incs = include_directories(
  '../../secp256k1/include',
  base,
  base/'modules/schnorrsig',
  base/'modules/ecdh',
  base/'modules/extrakeys',
)

secp256k1_srcs = [
  base/'secp256k1.c',
  base/'precomputed_ecmult_gen.c',
  base/'precomputed_ecmult.c',
]

secp256k1_lib = library('secp256k1',
  secp256k1_srcs,
  c_args : [
    '-Wno-unused-function',
    '-DENABLE_MODULE_EXTRAKEYS',
    '-DENABLE_MODULE_SCHNORRSIG',
    '-DECMULT_WINDOW_SIZE=2',  # TODO: What do I want here?
  ],
  include_directories : secp256k1_incs,
)

secp256k1_dep = declare_dependency(
  link_with : secp256k1_lib,
  include_directories : secp256k1_incs,
)
