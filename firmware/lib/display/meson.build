srcs = [
  'ui/ui.c',
  'ui/screens.c',
  'ui/display_send.c',
  'ui/display_action.c',
  'ui/gesture_tx.c',
  'ui/widgets/top_back.c',
  'ui/widgets/page_indicator.c',
  'ui/widgets/orbital_dots_animation.c',
  'ui/widgets/mfg_starfield_fps.c',
  'ui/widgets/mfg_burnin_grid.c',
  'ui/widgets/mfg_scrolling_h.c',
  'ui/widgets/top_menu.c',
  'ui/widgets/hold_ring.c',
  'ui/widgets/hold_cancel.c',
  'ui/widgets/address_display.c',
  'ui/screens/screen_about.c',
  'ui/screens/screen_money_movement.c',
  'ui/screens/screen_scan.c',
  'ui/screens/screen_onboarding.c',
  'ui/screens/screen_brightness.c',
  'ui/screens/screen_regulatory.c',
  'ui/screens/screen_locked.c',
  'ui/screens/screen_menu.c',
  'ui/screens/screen_menu_fingerprints.c',
  'ui/screens/screen_firmware_update.c',
  'ui/screens/screen_test_gesture.c',
  'ui/screens/screen_test_scroll.c',
  'ui/screens/screen_test_pin_pad.c',
  'ui/screens/screen_test_carousel.c',
  'ui/screens/screen_test_slider.c',
  'ui/screens/screen_test_progress.c',
  'ui/screens/screen_mfg.c',
  'ui/screens/screen_mfg_run_in.c',
  'ui/screens/screen_mfg_touch_test.c',
  'ui/screens/screen_fingerprint.c',
  'ui/screens/screen_privileged_action.c',
]

includes = [
  '.',
  'inc/',
  'ui/',
  'ui/screens/',
  'ui/widgets/',
  '../../third-party',
  'ui/images',
  'ui/fonts',
]

deps = [
  assert_dep,
  crypto_dep,
  langpack_dep,
  log_dep,
  lvgl_dep,
  mcu_dep,
  protos_dep,
  uc_dep,
]

python = find_program('python3')

# Font sizes we need for CashSansMono
font_sizes = [20, 22, 24, 28, 30, 34, 36, 40, 48]

# Font variants: Regular and Medium
font_variants = {
  'regular': 'CashSansMono-Regular.otf',
  # 'medium': 'CashSansMono-Medium.otf',  # Uncomment to enable Medium weight
}

lv_font_conv = find_program('lv_font_conv')

# Generate CashSansMono fonts at build time (both Regular and Medium)
foreach variant_name, font_file : font_variants
  foreach size : font_sizes
    font_target = custom_target(
      'cash_sans_mono_' + variant_name + '_' + size.to_string(),
      input : files('ui/fonts/' + font_file),
      output : ['cash_sans_mono_' + variant_name + '_' + size.to_string() + '.c'],
      command : [
        lv_font_conv,
        '--bpp', '4',
        '--size', size.to_string(),
        '--font', '@INPUT@',
        '--range', '0x20-0x7F',
        '--format', 'lvgl',
        '--no-compress',
        '--no-prefilter',
        '--force-fast-kern-format',
        '-o', '@OUTPUT@'
      ],
      depend_files : [
        meson.project_source_root() + '/lib/display/ui/fonts/' + font_file
      ],
      build_always_stale : true,
    )

    srcs += font_target
  endforeach
endforeach

image_pngs_rgb565 = [
  # Currently empty, but keeping for future RGB images
]

# Monochrome images (1-bit indexed)
image_pngs_i1 = [
  'ui/images/back_arrow.png',
  'ui/images/brightness.png',
  'ui/images/ellipsis_horizontal.png',
  'ui/images/exclamation_circle.png',
  'ui/images/fingerprint.png',
  'ui/images/info_circle.png',
  'ui/images/lock.png',
  'ui/images/locked.png',
  'ui/images/unlocked.png',
  'ui/images/paper_ribbon.png',
  'ui/images/power.png',
  'ui/images/check.png',
  'ui/images/cross.png',
  'ui/images/bitkey_qr.png',
  'ui/images/bitkey_logo_key.png',
  'ui/images/ce_logo.png',
  'ui/images/dot.png',
  'ui/images/eac_logo.png',
  'ui/images/fcc_logo.png',
  'ui/images/battery_10.png',
  'ui/images/battery_25.png',
  'ui/images/battery_50.png',
  'ui/images/battery_75.png',
  'ui/images/battery_100.png',
  'ui/images/battery_charging.png',
  'ui/images/touch.png',
  'ui/images/sleep.png',
  'ui/images/run_in.png',
]

foreach img : image_pngs_rgb565
  img_base = img.split('/')[-1].split('.')[0]
  image_target = custom_target(
    img_base + '_lvgl_image',
    input : files(img),
    output : [img_base + '.c'],
    command : [
      python,
      meson.project_source_root() + '/third-party/lvgl/scripts/LVGLImage.py',
      '--ofmt', 'C',
      '--cf',   'RGB565',
      '-o',     'lib/display/',
      '@INPUT@'
    ],
    depend_files : [
      meson.project_source_root() + '/third-party/lvgl/scripts/LVGLImage.py'
    ],
    build_always_stale : true,
  )

  srcs += image_target
endforeach

foreach img : image_pngs_i1
  img_base = img.split('/')[-1].split('.')[0]
  image_target = custom_target(
    img_base + '_lvgl_image',
    input : files(img),
    output : [img_base + '.c'],
    command : [
      python,
      meson.project_source_root() + '/third-party/lvgl/scripts/LVGLImage.py',
      '--ofmt', 'C',
      '--cf',   'I1',
      '-o',     'lib/display/',
      '@INPUT@'
    ],
    depend_files : [
      meson.project_source_root() + '/third-party/lvgl/scripts/LVGLImage.py'
    ],
    build_always_stale : true,
  )

  srcs += image_target
endforeach

display_includes = include_directories('.', 'driver')

display_ui_lib = library('display_ui',
  srcs,
  include_directories : includes,
  dependencies        : deps,
)
display_ui_dep = declare_dependency(
  link_with : display_ui_lib,
  include_directories : includes,
  dependencies : deps,
)

display_ui_mfgtest_lib = library('display_ui_mfgtest',
  srcs,
  include_directories : includes,
  dependencies        : deps,
  c_args : ['-DMFGTEST=1'],
)
display_ui_mfgtest_dep = declare_dependency(
  link_with : display_ui_mfgtest_lib,
  include_directories : includes,
  dependencies : deps,
)

# Build subdirectories (independent libraries)
subdir('driver')
