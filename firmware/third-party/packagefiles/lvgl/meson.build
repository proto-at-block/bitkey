config_dir = '../../../config/'
lvgl_dir = '../../lvgl/src'

# Base LVGL sources (common to all platforms)
lvgl_base_srcs = files(

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
)

# SDL driver sources (only for simulator)
lvgl_sdl_srcs = files(
  lvgl_dir / 'drivers/sdl/lv_sdl_keyboard.c',
  lvgl_dir / 'drivers/sdl/lv_sdl_mouse.c',
  lvgl_dir / 'drivers/sdl/lv_sdl_mousewheel.c',
  lvgl_dir / 'drivers/sdl/lv_sdl_window.c',
)

lvgl_incs = include_directories(
  lvgl_dir,
  lvgl_dir + '/core',
  lvgl_dir + '/display',
  lvgl_dir + '/draw',
  lvgl_dir + '/font',
  lvgl_dir + '/indev',
  lvgl_dir + '/layouts',
  lvgl_dir + '/layouts/flex',
  lvgl_dir + '/layouts/grid',
  config_dir,
)

lvgl_srcs = lvgl_base_srcs
lvgl_c_args = [
  '-DLV_CONF_INCLUDE_SIMPLE',
  '-Wno-unused-parameter',
  '-Wno-type-limits',
  '-Wno-unused-function',
]
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
  lvgl_srcs,
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
