srcs = [
  'animation.c',
  'animation_defs.c'
]

deps = [log_dep, helpers_dep, assert_dep]
includes = ['inc']

animation_lib = library('animation',
  srcs,
  include_directories : includes,
  dependencies : deps,
)
animation_dep = declare_dependency(
  link_with : animation_lib,
  include_directories : includes,
  dependencies : deps,
)

animation_test = executable('animation-test',
  'animation_test.c',
  dependencies : [animation_dep, test_deps],
)
test('animation test', animation_test)
