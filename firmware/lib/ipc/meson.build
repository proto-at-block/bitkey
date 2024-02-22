srcs = [
  'src/ipc.c',
]

template_files = [
  'templates/ipc_messages.jinja.h',
  'templates/ipc_port_gen.jinja.h',
  'templates/ipc_internal.jinja.c',
]

incs = ['inc', 'generated']
deps = [rtos_dep, protos_dep, log_dep, mempool_dep, sleep_dep]

generated_code = run_command('python3', 'ipc_codegen.py', 'collect-generated',
  check: true).stdout().strip().split(' ')

if generated_code.length() == 1 and generated_code[0] == ''
  error('IPC code generation failed -- no files')
endif

ipc_lib = library('ipc',
  srcs,
  generated_code,
  include_directories : [incs, 'src'],
  dependencies : deps,
)
ipc_dep = declare_dependency(
  link_with : ipc_lib,
  include_directories : [incs, ipc_lib.private_dir_include()],
  dependencies : deps,
)

ipc_test = executable('ipc-test',
  'src/ipc_test.c',
  include_directories : [incs, 'src'],
  dependencies : [ipc_dep, ringbuf_dep, test_deps],
)
test('ipc test', ipc_test)
