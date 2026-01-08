srcs = [
  'src/shell.c',
  'src/shell_cmd.c',
  'src/shell_help.c',
  'src/shell_argparse.c',
]
private_includes = ['src']

shell_includes = include_directories('inc')

includes = [private_includes, shell_includes]

shell_configs = {
  'w1': {
    'prompt': 'W1> ',
  },
  'w3-core': {
    'prompt': 'W3-Core> ',
  },
  'w3-uxc': {
    'prompt': 'W3-UXC> ',
  },
}

shell_libs = {}
shell_deps = {}

foreach shell_platform : shell_configs.keys()
  prompt_text = shell_configs[shell_platform]['prompt']

  shell_libs += {
    shell_platform: library('shell_' + shell_platform.underscorify(),
      srcs,
      include_directories : includes,
      c_args : ['-DSHELL_PROMPT_TEXT="' + prompt_text + '"'],
      dependencies : [printf_dep, uthash_dep, helpers_dep]
    )
  }

  shell_deps += {
    shell_platform: declare_dependency(
      link_with : shell_libs[shell_platform],
      include_directories : ['inc'],
    )
  }
endforeach

shell_dep = declare_dependency(
  include_directories : ['inc'],
)
