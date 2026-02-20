config_dir = '../../../config/'

lvgl_build_root = meson.project_build_root() / 'third-party'
lvgl_build_src = lvgl_build_root / 'lvgl'
lvgl_source_root = meson.current_source_dir() / '../../lvgl'
lvgl_dir = lvgl_build_src / 'src'
lvgl_root = lvgl_build_src

# Files modified by patches (used for dependency tracking)
lvgl_patched_files = [
  'src/indev/lv_indev.c',
  'src/indev/lv_indev.h',
  'src/draw/lv_draw_label.c',
  'src/widgets/label/lv_label.c',
]

lvgl_patches = [
  'indev-timestamp.patch',
  'label-trailing-space-centering.patch',
]

lvgl_patch_files = []
foreach patch_name : lvgl_patches
  lvgl_patch_files += [meson.current_source_dir() / patch_name]
endforeach

lvgl_needs_recopy = false

lvgl_src_exists = run_command('test', '-d', lvgl_build_src, check: false)
if lvgl_src_exists.returncode() != 0
  lvgl_needs_recopy = true
  message('LVGL build directory missing')
else
  sentinel_file = lvgl_build_src / lvgl_patched_files[0]
  foreach patch_file : lvgl_patch_files
    patch_newer = run_command('test', patch_file, '-nt', sentinel_file, check: false)
    if patch_newer.returncode() == 0
      lvgl_needs_recopy = true
      message('LVGL patch updated: @0@'.format(patch_file))
      break
    endif
  endforeach

  if not lvgl_needs_recopy
    submodule_commit_stamp = lvgl_build_src / '.submodule_commit'
    current_commit_result = run_command('git', '-C', lvgl_source_root, 'rev-parse', 'HEAD',
                                        check: false, capture: true)

    if current_commit_result.returncode() == 0
      current_commit = current_commit_result.stdout().strip()
      stored_commit = ''

      stamp_exists = run_command('test', '-f', submodule_commit_stamp, check: false)
      if stamp_exists.returncode() == 0
        stored_commit_result = run_command('cat', submodule_commit_stamp, check: false, capture: true)
        if stored_commit_result.returncode() == 0
          stored_commit = stored_commit_result.stdout().strip()
        endif
      endif

      if current_commit != stored_commit
        lvgl_needs_recopy = true
        if stored_commit == ''
          message('LVGL submodule commit not tracked')
        else
          message('LVGL submodule changed: @0@ -> @1@'.format(
            stored_commit.substring(0, 7),
            current_commit.substring(0, 7)
          ))
        endif
      endif
    endif
  endif
endif

if lvgl_needs_recopy
  if lvgl_src_exists.returncode() == 0
    run_command('rm', '-rf', lvgl_build_src, check: true, capture: false)
  endif

  copy_cmd = run_command('cp', '-r', lvgl_source_root, lvgl_build_src,
                         check: true, capture: false)
  message('Copied LVGL sources to build directory')

  foreach patch_name : lvgl_patches
    patch_file = meson.current_source_dir() / patch_name
    patch_cmd = run_command('patch', '-p1', '-d', lvgl_build_src,
                            '-i', patch_file, check: true, capture: false)
    message('Applied LVGL patch to build copy: ' + patch_name)
  endforeach

  current_commit_result = run_command('git', '-C', lvgl_source_root, 'rev-parse', 'HEAD',
                                      check: false, capture: true)
  if current_commit_result.returncode() == 0
    current_commit = current_commit_result.stdout().strip()
    submodule_commit_stamp = lvgl_build_src / '.submodule_commit'
    run_command('sh', '-c', 'echo "@0@" > "@1@"'.format(current_commit, submodule_commit_stamp),
                check: true, capture: false)
    message('Recorded submodule commit: @0@'.format(current_commit.substring(0, 7)))
  endif
else
  message('LVGL sources already up-to-date (skipping copy and patch)')
endif

lvgl_patched_targets = []
foreach patched_file : lvgl_patched_files
  target_name = patched_file.underscorify()
  source_file = lvgl_source_root / patched_file
  build_file = lvgl_build_src / patched_file

  patched_target = custom_target(target_name,
    input: [source_file] + lvgl_patch_files,
    output: target_name + '.stamp',
    command: ['touch', '@OUTPUT@'],
    build_by_default: true,
  )

  lvgl_patched_targets += [patched_target]
endforeach

# Base LVGL sources (common to all platforms)
lvgl_base_srcs = [

  # Core initialization
  lvgl_dir / 'lv_init.c',

  # Core object system
  lvgl_dir / 'core/lv_group.c',
  lvgl_dir / 'core/lv_obj_class.c',
  lvgl_dir / 'core/lv_obj_draw.c',
  lvgl_dir / 'core/lv_obj_event.c',
  lvgl_dir / 'core/lv_obj_id_builtin.c',
  lvgl_dir / 'core/lv_obj_pos.c',
  lvgl_dir / 'core/lv_obj_property.c',
  lvgl_dir / 'core/lv_obj_scroll.c',
  lvgl_dir / 'core/lv_obj_style_gen.c',
  lvgl_dir / 'core/lv_obj_style.c',
  lvgl_dir / 'core/lv_obj_tree.c',
  lvgl_dir / 'core/lv_obj.c',
  lvgl_dir / 'core/lv_refr.c',

  # Display management
  lvgl_dir / 'display/lv_display.c',

  # DMA2D draw engine - not needed
  # lvgl_dir / 'draw/dma2d/lv_draw_dma2d_fill.c',
  # lvgl_dir / 'draw/dma2d/lv_draw_dma2d_img.c',
  # lvgl_dir / 'draw/dma2d/lv_draw_dma2d.c',
  # lvgl_dir / 'draw/lv_draw_3d.c',

  # Core drawing functions
  lvgl_dir / 'draw/lv_draw_arc.c',
  lvgl_dir / 'draw/lv_draw_buf.c',
  lvgl_dir / 'draw/lv_draw_image.c',
  lvgl_dir / 'draw/lv_draw_label.c',
  lvgl_dir / 'draw/lv_draw_line.c',
  lvgl_dir / 'draw/lv_draw_mask.c',
  lvgl_dir / 'draw/lv_draw_rect.c',
  lvgl_dir / 'draw/lv_draw_triangle.c',
  #lvgl_dir / 'draw/lv_draw_vector.c',
  lvgl_dir / 'draw/lv_draw.c',
  lvgl_dir / 'draw/lv_image_decoder.c',
  lvgl_dir / 'draw/convert/lv_draw_buf_convert.c',

  # NemaGFX draw engine - not needed
  # lvgl_dir / 'draw/nema_gfx/lv_draw_nema_gfx_arc.c',
  # lvgl_dir / 'draw/nema_gfx/lv_draw_nema_gfx_border.c',
  # lvgl_dir / 'draw/nema_gfx/lv_draw_nema_gfx_fill.c',
  # lvgl_dir / 'draw/nema_gfx/lv_draw_nema_gfx_img.c',
  # lvgl_dir / 'draw/nema_gfx/lv_draw_nema_gfx_label.c',
  # lvgl_dir / 'draw/nema_gfx/lv_draw_nema_gfx_layer.c',
  # lvgl_dir / 'draw/nema_gfx/lv_draw_nema_gfx_line.c',
  # lvgl_dir / 'draw/nema_gfx/lv_draw_nema_gfx_stm32_hal.c',
  # lvgl_dir / 'draw/nema_gfx/lv_draw_nema_gfx_triangle.c',
  # lvgl_dir / 'draw/nema_gfx/lv_draw_nema_gfx_utils.c',
  # lvgl_dir / 'draw/nema_gfx/lv_draw_nema_gfx.c',
  # lvgl_dir / 'draw/nema_gfx/lv_nema_gfx_path.c',

  # Software renderer - default backend
  #lvgl_dir / 'draw/sw/blend/lv_draw_sw_blend_to_al88.c',
  #lvgl_dir / 'draw/sw/blend/lv_draw_sw_blend_to_argb8888_premultiplied.c',
  lvgl_dir / 'draw/sw/blend/lv_draw_sw_blend_to_argb8888.c',
  #lvgl_dir / 'draw/sw/blend/lv_draw_sw_blend_to_i1.c',
  #lvgl_dir / 'draw/sw/blend/lv_draw_sw_blend_to_l8.c',
  lvgl_dir / 'draw/sw/blend/lv_draw_sw_blend_to_rgb565_swapped.c',
  lvgl_dir / 'draw/sw/blend/lv_draw_sw_blend_to_rgb565.c',
  #lvgl_dir / 'draw/sw/blend/lv_draw_sw_blend_to_rgb888.c',
  lvgl_dir / 'draw/sw/blend/lv_draw_sw_blend.c',
  lvgl_dir / 'draw/sw/lv_draw_sw_arc.c',
  lvgl_dir / 'draw/sw/lv_draw_sw_border.c',
  lvgl_dir / 'draw/sw/lv_draw_sw_box_shadow.c',
  lvgl_dir / 'draw/sw/lv_draw_sw_fill.c',
  lvgl_dir / 'draw/sw/lv_draw_sw_grad.c',
  lvgl_dir / 'draw/sw/lv_draw_sw_img.c',
  lvgl_dir / 'draw/sw/lv_draw_sw_letter.c',
  lvgl_dir / 'draw/sw/lv_draw_sw_line.c',
  lvgl_dir / 'draw/sw/lv_draw_sw_mask_rect.c',
  lvgl_dir / 'draw/sw/lv_draw_sw_mask.c',
  lvgl_dir / 'draw/sw/lv_draw_sw_transform.c',
  lvgl_dir / 'draw/sw/lv_draw_sw_triangle.c',
  lvgl_dir / 'draw/sw/lv_draw_sw_utils.c',
  #lvgl_dir / 'draw/sw/lv_draw_sw_vector.c',
  lvgl_dir / 'draw/sw/lv_draw_sw.c',

  # Font loading
  # lvgl_dir / 'font/lv_binfont_loader.c',
  lvgl_dir / 'font/lv_font_fmt_txt.c',
  # Montserrat fonts - USED IN UI
  lvgl_dir / 'font/lv_font_montserrat_10.c',
  lvgl_dir / 'font/lv_font_montserrat_12.c',
  lvgl_dir / 'font/lv_font_montserrat_14_aligned.c',
  lvgl_dir / 'font/lv_font_montserrat_14.c',
  lvgl_dir / 'font/lv_font_montserrat_16.c',
  lvgl_dir / 'font/lv_font_montserrat_18.c',
  lvgl_dir / 'font/lv_font_montserrat_20.c',
  lvgl_dir / 'font/lv_font_montserrat_22.c',
  lvgl_dir / 'font/lv_font_montserrat_24.c',
  lvgl_dir / 'font/lv_font_montserrat_26.c',
  lvgl_dir / 'font/lv_font_montserrat_28_compressed.c',
  lvgl_dir / 'font/lv_font_montserrat_28.c',
  lvgl_dir / 'font/lv_font_montserrat_30.c',
  lvgl_dir / 'font/lv_font_montserrat_32.c',
  lvgl_dir / 'font/lv_font_montserrat_34.c',
  lvgl_dir / 'font/lv_font_montserrat_36.c',
  lvgl_dir / 'font/lv_font_montserrat_38.c',
  lvgl_dir / 'font/lv_font_montserrat_40.c',
  lvgl_dir / 'font/lv_font_montserrat_42.c',
  lvgl_dir / 'font/lv_font_montserrat_44.c',
  lvgl_dir / 'font/lv_font_montserrat_46.c',
  lvgl_dir / 'font/lv_font_montserrat_48.c',
  lvgl_dir / 'font/lv_font_montserrat_8.c',
  # CJK and special fonts
  # lvgl_dir / 'font/lv_font_simsun_14_cjk.c',
  # lvgl_dir / 'font/lv_font_simsun_16_cjk.c',
  # lvgl_dir / 'font/lv_font_source_han_sans_sc_14_cjk.c',
  # lvgl_dir / 'font/lv_font_source_han_sans_sc_16_cjk.c',
  # lvgl_dir / 'font/lv_font_unscii_16.c',
  # lvgl_dir / 'font/lv_font_unscii_8.c',
  lvgl_dir / 'font/lv_font.c',  # Font core

  # Input device handling
  lvgl_dir / 'indev/lv_indev_gesture.c',
  lvgl_dir / 'indev/lv_indev_scroll.c',
  lvgl_dir / 'indev/lv_indev.c',

  # Layout engines
  lvgl_dir / 'layouts/flex/lv_flex.c',
  lvgl_dir / 'layouts/grid/lv_grid.c',
  lvgl_dir / 'layouts/lv_layout.c',

  # External libraries
  # lvgl_dir / 'libs/barcode/code128.c',
  # lvgl_dir / 'libs/barcode/lv_barcode.c',
  lvgl_dir / 'libs/bin_decoder/lv_bin_decoder.c',
  # lvgl_dir / 'libs/bmp/lv_bmp.c',
  # lvgl_dir / 'libs/expat/xmlparse.c',
  # lvgl_dir / 'libs/expat/xmlrole.c',
  # lvgl_dir / 'libs/expat/xmltok_impl.c',
  # lvgl_dir / 'libs/expat/xmltok_ns.c',
  # lvgl_dir / 'libs/expat/xmltok.c',
  # lvgl_dir / 'libs/ffmpeg/lv_ffmpeg.c',
  # lvgl_dir / 'libs/freetype/lv_freetype_glyph.c',
  # lvgl_dir / 'libs/freetype/lv_freetype_image.c',
  # lvgl_dir / 'libs/freetype/lv_freetype_outline.c',
  # lvgl_dir / 'libs/freetype/lv_freetype.c',
  # lvgl_dir / 'libs/freetype/lv_ftsystem.c',
  # lvgl_dir / 'libs/fsdrv/lv_fs_cbfs.c',
  # lvgl_dir / 'libs/fsdrv/lv_fs_fatfs.c',
  # lvgl_dir / 'libs/fsdrv/lv_fs_littlefs.c',
  # lvgl_dir / 'libs/fsdrv/lv_fs_memfs.c',
  # lvgl_dir / 'libs/fsdrv/lv_fs_posix.c',
  # lvgl_dir / 'libs/fsdrv/lv_fs_stdio.c',
  # lvgl_dir / 'libs/fsdrv/lv_fs_uefi.c',
  # lvgl_dir / 'libs/fsdrv/lv_fs_win32.c',
  # lvgl_dir / 'libs/gif/gifdec.c',
  # lvgl_dir / 'libs/gif/lv_gif.c',
  # lvgl_dir / 'libs/libjpeg_turbo/lv_libjpeg_turbo.c',
  # lvgl_dir / 'libs/libpng/lv_libpng.c',
  # lvgl_dir / 'libs/lodepng/lodepng.c',
  # lvgl_dir / 'libs/lodepng/lv_lodepng.c',
  # lvgl_dir / 'libs/lz4/lz4.c',
  lvgl_dir / 'libs/qrcode/lv_qrcode.c',
  lvgl_dir / 'libs/qrcode/qrcodegen.c',
  # lvgl_dir / 'libs/rle/lv_rle.c',
  # lvgl_dir / 'libs/rlottie/lv_rlottie.c',
  # lvgl_dir / 'libs/svg/lv_svg_decoder.c',
  # lvgl_dir / 'libs/svg/lv_svg_parser.c',
  # lvgl_dir / 'libs/svg/lv_svg_render.c',
  # lvgl_dir / 'libs/svg/lv_svg_token.c',
  # lvgl_dir / 'libs/svg/lv_svg.c',
  # lvgl_dir / 'libs/tiny_ttf/lv_tiny_ttf.c',
  # lvgl_dir / 'libs/tjpgd/lv_tjpgd.c',
  # lvgl_dir / 'libs/tjpgd/tjpgd.c',

  # Core utilities and data structures
  lvgl_dir / 'misc/cache/class/lv_cache_lru_ll.c',
  lvgl_dir / 'misc/cache/class/lv_cache_lru_rb.c',
  lvgl_dir / 'misc/cache/instance/lv_image_cache.c',
  lvgl_dir / 'misc/cache/instance/lv_image_header_cache.c',
  lvgl_dir / 'misc/cache/lv_cache_entry.c',
  lvgl_dir / 'misc/cache/lv_cache.c',
  lvgl_dir / 'misc/lv_anim_timeline.c',
  lvgl_dir / 'misc/lv_anim.c',
  lvgl_dir / 'misc/lv_area.c',
  lvgl_dir / 'misc/lv_array.c',
  lvgl_dir / 'misc/lv_async.c',
  lvgl_dir / 'misc/lv_bidi.c',
  lvgl_dir / 'misc/lv_circle_buf.c',
  lvgl_dir / 'misc/lv_color_op.c',
  lvgl_dir / 'misc/lv_color.c',
  lvgl_dir / 'misc/lv_event.c',
  lvgl_dir / 'misc/lv_fs.c',
  lvgl_dir / 'misc/lv_grad.c',
  lvgl_dir / 'misc/lv_iter.c',
  lvgl_dir / 'misc/lv_ll.c',
  lvgl_dir / 'misc/lv_log.c',
  lvgl_dir / 'misc/lv_lru.c',
  lvgl_dir / 'misc/lv_math.c',
  lvgl_dir / 'misc/lv_matrix.c',
  lvgl_dir / 'misc/lv_palette.c',
  lvgl_dir / 'misc/lv_profiler_builtin.c',
  lvgl_dir / 'misc/lv_rb.c',
  lvgl_dir / 'misc/lv_style_gen.c',
  lvgl_dir / 'misc/lv_style.c',
  lvgl_dir / 'misc/lv_templ.c',
  lvgl_dir / 'misc/lv_text_ap.c',
  lvgl_dir / 'misc/lv_text.c',
  lvgl_dir / 'misc/lv_timer.c',
  lvgl_dir / 'misc/lv_tree.c',
  lvgl_dir / 'misc/lv_utils.c',

  # OSAL
  lvgl_dir / 'osal/lv_os_none.c',
  lvgl_dir / 'osal/lv_os.c',

  # Memory management and standard library
  lvgl_dir / 'stdlib/builtin/lv_mem_core_builtin.c',
  lvgl_dir / 'stdlib/builtin/lv_sprintf_builtin.c',
  lvgl_dir / 'stdlib/builtin/lv_string_builtin.c',
  lvgl_dir / 'stdlib/builtin/lv_tlsf.c',
  lvgl_dir / 'stdlib/lv_mem.c',

  # Themes
  lvgl_dir / 'themes/default/lv_theme_default.c',
  lvgl_dir / 'themes/lv_theme.c',
  lvgl_dir / 'themes/mono/lv_theme_mono.c',
  lvgl_dir / 'themes/simple/lv_theme_simple.c',

  # System tick
  lvgl_dir / 'tick/lv_tick.c',

  # Observer/Subject pattern
  lvgl_dir / 'others/observer/lv_observer.c',

  # Unused widgets
  # lvgl_dir / 'widgets/3dtexture/lv_3dtexture.c',
  # lvgl_dir / 'widgets/animimage/lv_animimage.c',

  # Widgets
  lvgl_dir / 'widgets/arc/lv_arc.c',
  lvgl_dir / 'widgets/bar/lv_bar.c',
  lvgl_dir / 'widgets/button/lv_button.c',
  lvgl_dir / 'widgets/buttonmatrix/lv_buttonmatrix.c',
  # lvgl_dir / 'widgets/calendar/lv_calendar_chinese.c',
  # lvgl_dir / 'widgets/calendar/lv_calendar_header_arrow.c',
  # lvgl_dir / 'widgets/calendar/lv_calendar_header_dropdown.c',
  # lvgl_dir / 'widgets/calendar/lv_calendar.c',
  lvgl_dir / 'widgets/canvas/lv_canvas.c',
  lvgl_dir / 'widgets/chart/lv_chart.c',
  # lvgl_dir / 'widgets/checkbox/lv_checkbox.c',
  # lvgl_dir / 'widgets/dropdown/lv_dropdown.c',
  lvgl_dir / 'widgets/image/lv_image.c',
  # lvgl_dir / 'widgets/imagebutton/lv_imagebutton.c',
  # lvgl_dir / 'widgets/keyboard/lv_keyboard.c',
  lvgl_dir / 'widgets/label/lv_label.c',
  # lvgl_dir / 'widgets/led/lv_led.c',
  lvgl_dir / 'widgets/line/lv_line.c',
  # lvgl_dir / 'widgets/list/lv_list.c',
  # lvgl_dir / 'widgets/lottie/lv_lottie.c',
  lvgl_dir / 'widgets/menu/lv_menu.c',
  # lvgl_dir / 'widgets/msgbox/lv_msgbox.c',
  # lvgl_dir / 'widgets/objx_templ/lv_objx_templ.c',
  # Widget properties
  # lvgl_dir / 'widgets/property/lv_animimage_properties.c',
  # lvgl_dir / 'widgets/property/lv_dropdown_properties.c',
  # lvgl_dir / 'widgets/property/lv_image_properties.c',
  # lvgl_dir / 'widgets/property/lv_keyboard_properties.c',
  # lvgl_dir / 'widgets/property/lv_label_properties.c',
  # lvgl_dir / 'widgets/property/lv_obj_properties.c',
  # lvgl_dir / 'widgets/property/lv_roller_properties.c',
  # lvgl_dir / 'widgets/property/lv_slider_properties.c',
  # lvgl_dir / 'widgets/property/lv_style_properties.c',
  # lvgl_dir / 'widgets/property/lv_textarea_properties.c',
  # lvgl_dir / 'widgets/roller/lv_roller.c',
  # lvgl_dir / 'widgets/scale/lv_scale.c',
  lvgl_dir / 'widgets/slider/lv_slider.c',
  # lvgl_dir / 'widgets/span/lv_span.c',
  # lvgl_dir / 'widgets/spinbox/lv_spinbox.c',
  # lvgl_dir / 'widgets/spinner/lv_spinner.c',
  # lvgl_dir / 'widgets/switch/lv_switch.c',
  # lvgl_dir / 'widgets/table/lv_table.c',
  # lvgl_dir / 'widgets/tabview/lv_tabview.c',
  # lvgl_dir / 'widgets/textarea/lv_textarea.c',
  # lvgl_dir / 'widgets/tileview/lv_tileview.c',
  # lvgl_dir / 'widgets/win/lv_win.c',
]

# SDL driver sources (only for simulator)
lvgl_sdl_srcs = [
  lvgl_dir / 'drivers/sdl/lv_sdl_keyboard.c',
  lvgl_dir / 'drivers/sdl/lv_sdl_mouse.c',
  lvgl_dir / 'drivers/sdl/lv_sdl_mousewheel.c',
  lvgl_dir / 'drivers/sdl/lv_sdl_window.c',
]

lvgl_incs = [
  include_directories(config_dir),
]

lvgl_inc_args = [
  '-I' + lvgl_build_root,
  '-I' + lvgl_dir,
  '-I' + (lvgl_dir / 'core'),
  '-I' + (lvgl_dir / 'display'),
  '-I' + (lvgl_dir / 'draw'),
  '-I' + (lvgl_dir / 'font'),
  '-I' + (lvgl_dir / 'indev'),
  '-I' + (lvgl_dir / 'layouts'),
  '-I' + (lvgl_dir / 'layouts/flex'),
  '-I' + (lvgl_dir / 'layouts/grid'),
]

lvgl_srcs = lvgl_base_srcs
lvgl_c_args = [
  '-DLV_CONF_INCLUDE_SIMPLE',
  '-Wno-unused-parameter',
  '-Wno-type-limits',
  '-Wno-unused-function',
] + lvgl_inc_args
lvgl_deps = []
lvgl_extra_incs = []

# Build SDL-enabled LVGL on darwin/linux (for simulator support)
if system == 'darwin' or system == 'linux'
  sdl2_dep = dependency('sdl2', required: false)
  if sdl2_dep.found()
    lvgl_srcs += lvgl_sdl_srcs
    lvgl_c_args += '-DLV_USE_SDL=1'
    lvgl_deps += sdl2_dep
  else
    message('SDL2 not found, building without SDL support')
  endif
endif

# Build LVGL library
lvgl_lib = library('lvgl',
  lvgl_srcs + lvgl_patched_targets,
  c_args : lvgl_c_args,
  include_directories : [lvgl_incs, lvgl_extra_incs],
  dependencies : lvgl_deps,
)

lvgl_dep = declare_dependency(
  link_with : lvgl_lib,
  include_directories : lvgl_incs,
  compile_args : lvgl_c_args,
  dependencies : lvgl_deps,
)

# Build test-enabled LVGL with lodepng and snapshot support
# Meson won't build this library unless it's linked by an executable,
# so no platform guard is needed here.
lvgl_test_srcs = lvgl_srcs + [
  lvgl_dir / 'libs/lodepng/lodepng.c',
  lvgl_dir / 'libs/lodepng/lv_lodepng.c',
  lvgl_dir / 'others/snapshot/lv_snapshot.c',
  lvgl_dir / 'libs/fsdrv/lv_fs_stdio.c',
]

lvgl_test_c_args = lvgl_c_args + [
  '-DLV_USE_LODEPNG=1',
  '-DLV_USE_SNAPSHOT=1',
  '-DLV_USE_FS_STDIO=1',
  '-DLV_FS_STDIO_LETTER=\'/\'',
  # Increase memory pool for tests - lodepng needs ~868KB per decoded PNG image.
  # With the current snapshot set and memory fragmentation, we need significant headroom.
  '-DLV_MEM_SIZE=(64*1024*1024U)',
]

lvgl_test_lib = library('lvgl_test',
  lvgl_test_srcs + lvgl_patched_targets,
  c_args : lvgl_test_c_args,
  include_directories : [lvgl_incs, lvgl_extra_incs],
  dependencies : lvgl_deps,
)

lvgl_test_dep = declare_dependency(
  link_with : lvgl_test_lib,
  include_directories : lvgl_incs,
  compile_args : lvgl_test_c_args,
  dependencies : lvgl_deps,
)
