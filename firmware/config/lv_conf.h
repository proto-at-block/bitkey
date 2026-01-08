/**
 * @file lv_conf.h
 * Configuration file based off v9.3.0
 */

/* clang-format off */

#ifndef LV_CONF_H
#define LV_CONF_H

/*====================
   COLOR SETTINGS
 *====================*/

/** Color depth: 1 (I1), 8 (L8), 16 (RGB565), 24 (RGB888), 32 (XRGB8888) */
#define LV_COLOR_DEPTH 16

/*=========================
   STDLIB WRAPPER SETTINGS
 *=========================*/

/** Possible values
 * - LV_STDLIB_BUILTIN:     LVGL's built in implementation
 * - LV_STDLIB_CLIB:        Standard C functions, like malloc, strlen, etc
 * - LV_STDLIB_MICROPYTHON: MicroPython implementation
 * - LV_STDLIB_RTTHREAD:    RT-Thread implementation
 * - LV_STDLIB_CUSTOM:      Implement the functions externally
 */
#define LV_USE_STDLIB_MALLOC    LV_STDLIB_BUILTIN

/** Possible values
 * - LV_STDLIB_BUILTIN:     LVGL's built in implementation
 * - LV_STDLIB_CLIB:        Standard C functions, like malloc, strlen, etc
 * - LV_STDLIB_MICROPYTHON: MicroPython implementation
 * - LV_STDLIB_RTTHREAD:    RT-Thread implementation
 * - LV_STDLIB_CUSTOM:      Implement the functions externally
 */
#define LV_USE_STDLIB_STRING    LV_STDLIB_BUILTIN

/** Possible values
 * - LV_STDLIB_BUILTIN:     LVGL's built in implementation
 * - LV_STDLIB_CLIB:        Standard C functions, like malloc, strlen, etc
 * - LV_STDLIB_MICROPYTHON: MicroPython implementation
 * - LV_STDLIB_RTTHREAD:    RT-Thread implementation
 * - LV_STDLIB_CUSTOM:      Implement the functions externally
 */
#define LV_USE_STDLIB_SPRINTF   LV_STDLIB_BUILTIN

#define LV_STDINT_INCLUDE       <stdint.h>
#define LV_STDDEF_INCLUDE       <stddef.h>
#define LV_STDBOOL_INCLUDE      <stdbool.h>
#define LV_INTTYPES_INCLUDE     <inttypes.h>
#define LV_LIMITS_INCLUDE       <limits.h>
#define LV_STDARG_INCLUDE       <stdarg.h>

#if LV_USE_STDLIB_MALLOC == LV_STDLIB_BUILTIN
    /** Size of memory available for `lv_malloc()` in bytes (>= 2kB) */
    #define LV_MEM_SIZE (64 * 1024U)          /**< [bytes] */

    /** Size of the memory expand for `lv_malloc()` in bytes */
    #define LV_MEM_POOL_EXPAND_SIZE 0

    /** Set an address for the memory pool instead of allocating it as a normal array. Can be in external SRAM too. */
    #define LV_MEM_ADR 0     /**< 0: unused*/
    /* Instead of an address give a memory allocator that will be called to get a memory pool for LVGL. E.g. my_malloc */
    #if LV_MEM_ADR == 0
        #undef LV_MEM_POOL_INCLUDE
        #undef LV_MEM_POOL_ALLOC
    #endif
#endif  /*LV_USE_STDLIB_MALLOC == LV_STDLIB_BUILTIN*/

/*====================
   HAL SETTINGS
 *====================*/

/** Default display refresh, input device read and animation step period. */
#define LV_DEF_REFR_PERIOD  20      /**< [ms] */

/** Default Dots Per Inch. Used to initialize default sizes such as widgets sized, style paddings.
 * (Not so important, you can adjust it to modify default sizes and spaces.) */
#define LV_DPI_DEF 300              /**< [px/inch] */

/*=================
 * OPERATING SYSTEM
 *=================*/
/** Select operating system to use. Possible options:
 * - LV_OS_NONE
 * - LV_OS_PTHREAD
 * - LV_OS_FREERTOS
 * - LV_OS_CMSIS_RTOS2
 * - LV_OS_RTTHREAD
 * - LV_OS_WINDOWS
 * - LV_OS_MQX
 * - LV_OS_SDL2
 * - LV_OS_CUSTOM */
#define LV_USE_OS   LV_OS_NONE

#if LV_USE_OS == LV_OS_CUSTOM
    #define LV_OS_CUSTOM_INCLUDE <stdint.h>
#endif
#if LV_USE_OS == LV_OS_FREERTOS
    /*
     * Unblocking an RTOS task with a direct notification is 45% faster and uses less RAM
     * than unblocking a task using an intermediary object such as a binary semaphore.
     * RTOS task notifications can only be used when there is only one task that can be the recipient of the event.
     */
    #define LV_USE_FREERTOS_TASK_NOTIFY 1
#endif

/*========================
 * RENDERING CONFIGURATION
 *========================*/

/** Align stride of all layers and images to this bytes */
#define LV_DRAW_BUF_STRIDE_ALIGN                1

/** Align start address of draw_buf addresses to this bytes*/
#define LV_DRAW_BUF_ALIGN                       4

/** Using matrix for transformations.
 * Requirements:
 * - `LV_USE_MATRIX = 1`.
 * - Rendering engine needs to support 3x3 matrix transformations. */
#define LV_DRAW_TRANSFORM_USE_MATRIX            0

/* If a widget has `style_opa < 255` (not `bg_opa`, `text_opa` etc) or not NORMAL blend mode
 * it is buffered into a "simple" layer before rendering. The widget can be buffered in smaller chunks.
 * "Transformed layers" (if `transform_angle/zoom` are set) use larger buffers
 * and can't be drawn in chunks. */

/** The target buffer size for simple layer chunks. */
#define LV_DRAW_LAYER_SIMPLE_BUF_SIZE    (24 * 1024)    /**< [bytes]*/

/* Limit the max allocated memory for simple and transformed layers.
 * It should be at least `LV_DRAW_LAYER_SIMPLE_BUF_SIZE` sized but if transformed layers are also used
 * it should be enough to store the largest widget too (width x height x 4 area).
 * Set it to 0 to have no limit. */
#define LV_DRAW_LAYER_MAX_MEMORY 0  /**< No limit by default [bytes]*/

/** Stack size of drawing thread.
 * NOTE: If FreeType or ThorVG is enabled, it is recommended to set it to 32KB or more.
 */
#define LV_DRAW_THREAD_STACK_SIZE    (8 * 1024)         /**< [bytes]*/

/** Thread priority of the drawing task.
 *  Higher values mean higher priority.
 *  Can use values from lv_thread_prio_t enum in lv_os.h: LV_THREAD_PRIO_LOWEST,
 *  LV_THREAD_PRIO_LOW, LV_THREAD_PRIO_MID, LV_THREAD_PRIO_HIGH, LV_THREAD_PRIO_HIGHEST
 *  Make sure the priority value aligns with the OS-specific priority levels.
 *  On systems with limited priority levels (e.g., FreeRTOS), a higher value can improve
 *  rendering performance but might cause other tasks to starve. */
#define LV_DRAW_THREAD_PRIO LV_THREAD_PRIO_HIGH

#define LV_USE_DRAW_SW 1
#if LV_USE_DRAW_SW == 1
    /*
     * Selectively disable color format support in order to reduce code size.
     * NOTE: some features use certain color formats internally, e.g.
     * - gradients use RGB888
     * - bitmaps with transparency may use ARGB8888
     */
    #define LV_DRAW_SW_SUPPORT_RGB565       1
    #define LV_DRAW_SW_SUPPORT_RGB565_SWAPPED       1
    #define LV_DRAW_SW_SUPPORT_RGB565A8     0
    #define LV_DRAW_SW_SUPPORT_RGB888       0
    #define LV_DRAW_SW_SUPPORT_XRGB8888     0
    #define LV_DRAW_SW_SUPPORT_ARGB8888     1
    #define LV_DRAW_SW_SUPPORT_ARGB8888_PREMULTIPLIED 0
    #define LV_DRAW_SW_SUPPORT_L8           0
    #define LV_DRAW_SW_SUPPORT_AL88         0
    #define LV_DRAW_SW_SUPPORT_A8           0
    #define LV_DRAW_SW_SUPPORT_I1           0

    /* The threshold of the luminance to consider a pixel as
     * active in indexed color format */
    #define LV_DRAW_SW_I1_LUM_THRESHOLD 127

    /** Set number of draw units.
     *  - > 1 requires operating system to be enabled in `LV_USE_OS`.
     *  - > 1 means multiple threads will render the screen in parallel. */
    #define LV_DRAW_SW_DRAW_UNIT_CNT    1

    /** Use Arm-2D to accelerate software (sw) rendering. */
    #define LV_USE_DRAW_ARM2D_SYNC      0

    /** Enable native helium assembly to be compiled. */
    #define LV_USE_NATIVE_HELIUM_ASM    0

    /**
     * - 0: Use a simple renderer capable of drawing only simple rectangles with gradient, images, text, and straight lines only.
     * - 1: Use a complex renderer capable of drawing rounded corners, shadow, skew lines, and arcs too. */
    #define LV_DRAW_SW_COMPLEX          1

    #if LV_DRAW_SW_COMPLEX == 1
        /** Allow buffering some shadow calculation.
         *  LV_DRAW_SW_SHADOW_CACHE_SIZE is the maximum shadow size to buffer, where shadow size is
         *  `shadow_width + radius`.  Caching has LV_DRAW_SW_SHADOW_CACHE_SIZE^2 RAM cost. */
        #define LV_DRAW_SW_SHADOW_CACHE_SIZE 0

        /** Set number of maximally-cached circle data.
         *  The circumference of 1/4 circle are saved for anti-aliasing.
         *  `radius * 4` bytes are used per circle (the most often used radiuses are saved).
         *  - 0: disables caching */
        #define LV_DRAW_SW_CIRCLE_CACHE_SIZE 4
    #endif

    #define  LV_USE_DRAW_SW_ASM     LV_DRAW_SW_ASM_NONE

    #if LV_USE_DRAW_SW_ASM == LV_DRAW_SW_ASM_CUSTOM
        #define  LV_DRAW_SW_ASM_CUSTOM_INCLUDE ""
    #endif

    /** Enable drawing complex gradients in software: linear at an angle, radial or conical */
    #define LV_USE_DRAW_SW_COMPLEX_GRADIENTS    0

#endif

/*=======================
 * FEATURE CONFIGURATION
 *=======================*/

/*-------------
 * Logging
 *-----------*/

/** Enable log module */
#define LV_USE_LOG 0
#if LV_USE_LOG
    /** Set value to one of the following levels of logging detail:
     *  - LV_LOG_LEVEL_TRACE    Log detailed information.
     *  - LV_LOG_LEVEL_INFO     Log important events.
     *  - LV_LOG_LEVEL_WARN     Log if something unwanted happened but didn't cause a problem.
     *  - LV_LOG_LEVEL_ERROR    Log only critical issues, when system may fail.
     *  - LV_LOG_LEVEL_USER     Log only custom log messages added by the user.
     *  - LV_LOG_LEVEL_NONE     Do not log anything. */
    #define LV_LOG_LEVEL LV_LOG_LEVEL_WARN

    /** - 1: Print log with 'printf';
     *  - 0: User needs to register a callback with `lv_log_register_print_cb()`. */
    #define LV_LOG_PRINTF 0

    /** Set callback to print logs.
     *  E.g `my_print`. The prototype should be `void my_print(lv_log_level_t level, const char * buf)`.
     *  Can be overwritten by `lv_log_register_print_cb`. */
    //#define LV_LOG_PRINT_CB

    /** - 1: Enable printing timestamp;
     *  - 0: Disable printing timestamp. */
    #define LV_LOG_USE_TIMESTAMP 1

    /** - 1: Print file and line number of the log;
     *  - 0: Do not print file and line number of the log. */
    #define LV_LOG_USE_FILE_LINE 1

    /* Enable/disable LV_LOG_TRACE in modules that produces a huge number of logs. */
    #define LV_LOG_TRACE_MEM        1   /**< Enable/disable trace logs in memory operations. */
    #define LV_LOG_TRACE_TIMER      1   /**< Enable/disable trace logs in timer operations. */
    #define LV_LOG_TRACE_INDEV      1   /**< Enable/disable trace logs in input device operations. */
    #define LV_LOG_TRACE_DISP_REFR  1   /**< Enable/disable trace logs in display re-draw operations. */
    #define LV_LOG_TRACE_EVENT      1   /**< Enable/disable trace logs in event dispatch logic. */
    #define LV_LOG_TRACE_OBJ_CREATE 1   /**< Enable/disable trace logs in object creation (core `obj` creation plus every widget). */
    #define LV_LOG_TRACE_LAYOUT     1   /**< Enable/disable trace logs in flex- and grid-layout operations. */
    #define LV_LOG_TRACE_ANIM       1   /**< Enable/disable trace logs in animation logic. */
    #define LV_LOG_TRACE_CACHE      1   /**< Enable/disable trace logs in cache operations. */
#endif  /*LV_USE_LOG*/

/*-------------
 * Asserts
 *-----------*/

/* Enable assertion failures if an operation fails or invalid data is found.
 * If LV_USE_LOG is enabled, an error message will be printed on failure. */
#define LV_USE_ASSERT_NULL          1   /**< Check if the parameter is NULL. (Very fast, recommended) */
#define LV_USE_ASSERT_MALLOC        1   /**< Checks is the memory is successfully allocated or no. (Very fast, recommended) */
#define LV_USE_ASSERT_STYLE         0   /**< Check if the styles are properly initialized. (Very fast, recommended) */
#define LV_USE_ASSERT_MEM_INTEGRITY 0   /**< Check the integrity of `lv_mem` after critical operations. (Slow) */
#define LV_USE_ASSERT_OBJ           0   /**< Check the object's type and existence (e.g. not deleted). (Slow) */

/** Add a custom handler when assert happens e.g. to restart MCU. */
#define LV_ASSERT_HANDLER_INCLUDE <stdint.h>
#define LV_ASSERT_HANDLER while(1);     /**< Halt by default */

/*-------------
 * Debug
 *-----------*/

/** 1: Draw random colored rectangles over the redrawn areas. */
#define LV_USE_REFR_DEBUG 0

/** 1: Draw a red overlay for ARGB layers and a green overlay for RGB layers*/
#define LV_USE_LAYER_DEBUG 0

/** 1: Adds the following behaviors for debugging:
 *  - Draw overlays with different colors for each draw_unit's tasks.
 *  - Draw index number of draw unit on white background.
 *  - For layers, draws index number of draw unit on black background. */
#define LV_USE_PARALLEL_DRAW_DEBUG 0

/*-------------
 * Others
 *-----------*/

/** Default cache size in bytes.
 *  Used by image decoders such as `lv_lodepng` to keep the decoded image in memory.
 *  If size is not set to 0, the decoder will fail to decode when the cache is full.
 *  If size is 0, the cache function is not enabled and the decoded memory will be
 *  released immediately after use. */
#define LV_CACHE_DEF_SIZE       0

/** Default number of image header cache entries. The cache is used to store the headers of images
 *  The main logic is like `LV_CACHE_DEF_SIZE` but for image headers. */
#define LV_IMAGE_HEADER_CACHE_DEF_CNT 0

/** Number of stops allowed per gradient. Increase this to allow more stops.
 *  This adds (sizeof(lv_color_t) + 1) bytes per additional stop. */
#define LV_GRADIENT_MAX_STOPS   2

/** Adjust color mix functions rounding. GPUs might calculate color mix (blending) differently.
 *  - 0:   round down,
 *  - 64:  round up from x.75,
 *  - 128: round up from half,
 *  - 192: round up from x.25,
 *  - 254: round up */
#define LV_COLOR_MIX_ROUND_OFS  0

/** Add 2 x 32-bit variables to each `lv_obj_t` to speed up getting style properties */
#define LV_OBJ_STYLE_CACHE      0

/** Automatically assign an ID when obj is created */
#define LV_OBJ_ID_AUTO_ASSIGN   LV_USE_OBJ_ID

/** Use builtin obj ID handler functions:
* - lv_obj_assign_id:       Called when a widget is created. Use a separate counter for each widget class as an ID.
* - lv_obj_id_compare:      Compare the ID to decide if it matches with a requested value.
* - lv_obj_stringify_id:    Return string-ified identifier, e.g. "button3".
* - lv_obj_free_id:         Does nothing, as there is no memory allocation for the ID.
* When disabled these functions needs to be implemented by the user.*/
#define LV_USE_OBJ_ID_BUILTIN   1

/** Enable property name support. */
#define LV_USE_OBJ_PROPERTY_NAME 1


/*=====================
 *  COMPILER SETTINGS
 *====================*/

/** For big endian systems set to 1 */
#define LV_BIG_ENDIAN_SYSTEM 0

/** Define a custom attribute for `lv_tick_inc` function */
#define LV_ATTRIBUTE_TICK_INC

/** Define a custom attribute for `lv_timer_handler` function */
#define LV_ATTRIBUTE_TIMER_HANDLER

/** Define a custom attribute for `lv_display_flush_ready` function */
#define LV_ATTRIBUTE_FLUSH_READY

/** Align VG_LITE buffers on this number of bytes.
 *  @note  vglite_src_buf_aligned() uses this value to validate alignment of passed buffer pointers. */
#define LV_ATTRIBUTE_MEM_ALIGN_SIZE 1

/** Will be added where memory needs to be aligned (with -Os data might not be aligned to boundary by default).
 *  E.g. __attribute__((aligned(4)))*/
#define LV_ATTRIBUTE_MEM_ALIGN

/** Attribute to mark large constant arrays, for example for font bitmaps */
#define LV_ATTRIBUTE_LARGE_CONST

/** Compiler prefix for a large array declaration in RAM */
#define LV_ATTRIBUTE_LARGE_RAM_ARRAY

/** Place performance critical functions into a faster memory (e.g RAM) */
#define LV_ATTRIBUTE_FAST_MEM

/** Export integer constant to binding. This macro is used with constants in the form of LV_<CONST> that
 *  should also appear on LVGL binding API such as MicroPython. */
#define LV_EXPORT_CONST_INT(int_value) struct _silence_gcc_warning  /**< The default value just prevents GCC warning */

/** Prefix all global extern data with this */
#define LV_ATTRIBUTE_EXTERN_DATA


/*==================
 *   FONT USAGE
 *===================*/

/* Montserrat fonts with ASCII range and some symbols using bpp = 4
 * https://fonts.google.com/specimen/Montserrat */
#define LV_FONT_MONTSERRAT_8  0
#define LV_FONT_MONTSERRAT_10 0
#define LV_FONT_MONTSERRAT_12 0
#define LV_FONT_MONTSERRAT_14 0
#define LV_FONT_MONTSERRAT_16 0
#define LV_FONT_MONTSERRAT_18 0
#define LV_FONT_MONTSERRAT_20 0
#define LV_FONT_MONTSERRAT_22 0
#define LV_FONT_MONTSERRAT_24 0
#define LV_FONT_MONTSERRAT_26 0
#define LV_FONT_MONTSERRAT_28 0
#define LV_FONT_MONTSERRAT_30 0
#define LV_FONT_MONTSERRAT_32 0
#define LV_FONT_MONTSERRAT_34 0
#define LV_FONT_MONTSERRAT_36 0
#define LV_FONT_MONTSERRAT_38 0
#define LV_FONT_MONTSERRAT_40 0
#define LV_FONT_MONTSERRAT_42 0
#define LV_FONT_MONTSERRAT_44 0
#define LV_FONT_MONTSERRAT_46 0
#define LV_FONT_MONTSERRAT_48 0

/* Demonstrate special features */
#define LV_FONT_MONTSERRAT_28_COMPRESSED    0  /**< bpp = 3 */
#define LV_FONT_DEJAVU_16_PERSIAN_HEBREW    0  /**< Hebrew, Arabic, Persian letters and all their forms */
#define LV_FONT_SIMSUN_14_CJK               0  /**< 1000 most common CJK radicals */
#define LV_FONT_SIMSUN_16_CJK               0  /**< 1000 most common CJK radicals */
#define LV_FONT_SOURCE_HAN_SANS_SC_14_CJK   0  /**< 1338 most common CJK radicals */
#define LV_FONT_SOURCE_HAN_SANS_SC_16_CJK   0  /**< 1338 most common CJK radicals */

/** Pixel perfect monospaced fonts */
#define LV_FONT_UNSCII_8  0
#define LV_FONT_UNSCII_16 0

/** Optionally declare custom fonts here.
 *
 *  You can use any of these fonts as the default font too and they will be available
 *  globally.  Example:
 *
 *  @code
 *  #define LV_FONT_CUSTOM_DECLARE   LV_FONT_DECLARE(my_font_1) LV_FONT_DECLARE(my_font_2)
 *  @endcode
 */
#define LV_FONT_CUSTOM_DECLARE \
    LV_FONT_DECLARE(cash_sans_mono_regular_20) \
    LV_FONT_DECLARE(cash_sans_mono_regular_22) \
    LV_FONT_DECLARE(cash_sans_mono_regular_24) \
    LV_FONT_DECLARE(cash_sans_mono_regular_28) \
    LV_FONT_DECLARE(cash_sans_mono_regular_30) \
    LV_FONT_DECLARE(cash_sans_mono_regular_34) \
    LV_FONT_DECLARE(cash_sans_mono_regular_36)
    /* Medium weight fonts - uncomment if enabled in lib/display/meson.build */
    /* LV_FONT_DECLARE(cash_sans_mono_medium_20) \ */
    /* LV_FONT_DECLARE(cash_sans_mono_medium_22) \ */
    /* LV_FONT_DECLARE(cash_sans_mono_medium_24) \ */
    /* LV_FONT_DECLARE(cash_sans_mono_medium_28) \ */
    /* LV_FONT_DECLARE(cash_sans_mono_medium_30) \ */
    /* LV_FONT_DECLARE(cash_sans_mono_medium_34) \ */
    /* LV_FONT_DECLARE(cash_sans_mono_medium_36) \ */

/** Always set a default font */
#define LV_FONT_DEFAULT &cash_sans_mono_regular_24

/** Enable handling large font and/or fonts with a lot of characters.
 *  The limit depends on the font size, font face and bpp.
 *  A compiler error will be triggered if a font needs it. */
#define LV_FONT_FMT_TXT_LARGE 0

/** Enables/disables support for compressed fonts. */
#define LV_USE_FONT_COMPRESSED 0

/** Enable drawing placeholders when glyph dsc is not found. */
#define LV_USE_FONT_PLACEHOLDER 1

/*=================
 *  TEXT SETTINGS
 *=================*/

/**
 * Select a character encoding for strings.
 * Your IDE or editor should have the same character encoding.
 * - LV_TXT_ENC_UTF8
 * - LV_TXT_ENC_ASCII
 */
#define LV_TXT_ENC LV_TXT_ENC_UTF8

/** While rendering text strings, break (wrap) text on these chars. */
#define LV_TXT_BREAK_CHARS " ,.;:-_)]}"

/** If a word is at least this long, will break wherever "prettiest".
 *  To disable, set to a value <= 0. */
#define LV_TXT_LINE_BREAK_LONG_LEN 0

/** Minimum number of characters in a long word to put on a line before a break.
 *  Depends on LV_TXT_LINE_BREAK_LONG_LEN. */
#define LV_TXT_LINE_BREAK_LONG_PRE_MIN_LEN 3

/** Minimum number of characters in a long word to put on a line after a break.
 *  Depends on LV_TXT_LINE_BREAK_LONG_LEN. */
#define LV_TXT_LINE_BREAK_LONG_POST_MIN_LEN 3

/*The control character to use for signaling text recoloring*/
#define LV_TXT_COLOR_CMD "#"

/*==================
 * WIDGETS
 *================*/
/* Documentation for widgets can be found here: https://docs.lvgl.io/master/details/widgets/index.html . */

#define LV_WIDGETS_HAS_DEFAULT_VALUE  1

#define LV_USE_ANIMIMG    1

#define LV_USE_ARC        1

#define LV_USE_BAR        1

#define LV_USE_BUTTON        1

#define LV_USE_BUTTONMATRIX  1

#define LV_USE_CALENDAR   0
#if LV_USE_CALENDAR
    #define LV_CALENDAR_WEEK_STARTS_MONDAY 0
    #if LV_CALENDAR_WEEK_STARTS_MONDAY
        #define LV_CALENDAR_DEFAULT_DAY_NAMES {"Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"}
    #else
        #define LV_CALENDAR_DEFAULT_DAY_NAMES {"Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"}
    #endif

    #define LV_CALENDAR_DEFAULT_MONTH_NAMES {"January", "February", "March",  "April", "May",  "June", "July", "August", "September", "October", "November", "December"}
    #define LV_USE_CALENDAR_HEADER_ARROW 1
    #define LV_USE_CALENDAR_HEADER_DROPDOWN 1
    #define LV_USE_CALENDAR_CHINESE 0
#endif  /*LV_USE_CALENDAR*/

#define LV_USE_CANVAS     1

#define LV_USE_CHART      1

#define LV_USE_CHECKBOX   0

#define LV_USE_DROPDOWN   0   /**< Requires: lv_label */

#define LV_USE_IMAGE      1   /**< Requires: lv_label */

#define LV_USE_IMAGEBUTTON     0

#define LV_USE_KEYBOARD   0

#define LV_USE_LABEL      1
#if LV_USE_LABEL
    #define LV_LABEL_TEXT_SELECTION 1   /**< Enable selecting text of the label */
    #define LV_LABEL_LONG_TXT_HINT 1    /**< Store some extra info in labels to speed up drawing of very long text */
    #define LV_LABEL_WAIT_CHAR_COUNT 3  /**< The count of wait chart */
#endif

#define LV_USE_LED        0

#define LV_USE_LINE       1

#define LV_USE_LIST       0

#define LV_USE_LOTTIE     0  /**< Requires: lv_canvas, thorvg */

#define LV_USE_MENU       1

#define LV_USE_MSGBOX     0

#define LV_USE_ROLLER     0   /**< Requires: lv_label */

#define LV_USE_SCALE      0

#define LV_USE_SLIDER     1   /**< Requires: lv_bar */

#define LV_USE_SPAN       0
#if LV_USE_SPAN
    /** A line of text can contain this maximum number of span descriptors. */
    #define LV_SPAN_SNIPPET_STACK_SIZE 64
#endif

#define LV_USE_SPINBOX    0

#define LV_USE_SPINNER    0

#define LV_USE_SWITCH     0

#define LV_USE_TABLE      0

#define LV_USE_TABVIEW    0

#define LV_USE_TEXTAREA   0   /**< Requires: lv_label */
#if LV_USE_TEXTAREA != 0
    #define LV_TEXTAREA_DEF_PWD_SHOW_TIME 1500    /**< [ms] */
#endif

#define LV_USE_TILEVIEW   0

#define LV_USE_WIN        0

#define LV_USE_3DTEXTURE  0

/*==================
 * THEMES
 *==================*/
/* Documentation for themes can be found here: https://docs.lvgl.io/master/details/common-widget-features/styles/style.html#themes . */

/** A very simple theme that is a good starting point for a custom theme */
#define LV_USE_THEME_SIMPLE 1

/*==================
 * LAYOUTS
 *==================*/
/* Documentation for layouts can be found here: https://docs.lvgl.io/master/details/common-widget-features/layouts/index.html . */

/** A layout similar to Flexbox in CSS. */
#define LV_USE_FLEX 1

/** A layout similar to Grid in CSS. */
#define LV_USE_GRID 1

/*====================
 * 3RD PARTS LIBRARIES
 *====================*/
/* Documentation for libraries can be found here: https://docs.lvgl.io/master/details/libs/index.html . */

/** QR code library */
#define LV_USE_QRCODE 1

/*==================
 * DEVICES
 *==================*/
/** Use SDL to open window on PC and handle mouse and keyboard. */
#ifndef LV_USE_SDL
    #define LV_USE_SDL              0
#endif
#if LV_USE_SDL
    #define LV_SDL_INCLUDE_PATH     <SDL2/SDL.h>
    #define LV_SDL_RENDER_MODE      LV_DISPLAY_RENDER_MODE_DIRECT   /**< LV_DISPLAY_RENDER_MODE_DIRECT is recommended for best performance */
    #define LV_SDL_BUF_COUNT        1    /**< 1 or 2 */
    #define LV_SDL_ACCELERATED      1    /**< 1: Use hardware acceleration*/
    #define LV_SDL_FULLSCREEN       0    /**< 1: Make the window full screen by default */
    #define LV_SDL_DIRECT_EXIT      1    /**< 1: Exit the application when all SDL windows are closed */
    #define LV_SDL_MOUSEWHEEL_MODE  LV_SDL_MOUSEWHEEL_MODE_ENCODER  /*LV_SDL_MOUSEWHEEL_MODE_ENCODER/CROWN*/
#endif
#endif /*LV_CONF_H*/
