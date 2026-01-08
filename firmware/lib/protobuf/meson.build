protoc = find_program('nanopb_generator',
  required : true,
)

gen = generator(protoc,
  output : ['@BASENAME@.pb.c', '@BASENAME@.pb.h'],
  arguments : [
    '--proto-path=@CURRENT_SOURCE_DIR@/protos',
    '--output-dir=@BUILD_DIR@', '@INPUT@',
  ]
)

proto_srcs = [
  'protos/display.proto',
  'protos/mfgtest.proto',
  'protos/ops-keybundle.proto',
  'protos/ops-keys.proto',
  'protos/ops-seal.proto',
  'protos/secure-channel.proto',
  'protos/test.proto',
  'protos/wallet.proto',
  'protos/uxc.proto',
]

generated = []
foreach proto : proto_srcs
  generated += gen.process(proto)
endforeach

protos_lib = library('protos',
  generated,
  c_args : ['-DPB_FIELD_16BIT'],
  dependencies : [nanopb_dep],
)

protos_dep = declare_dependency(
  link_with : [protos_lib],
  dependencies : [nanopb_dep],
  # https://mesonbuild.com/Reference-manual_returned_both_libs.html#build_tgtprivate_dir_include
  include_directories : protos_lib.private_dir_include(),
)
