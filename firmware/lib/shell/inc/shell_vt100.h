#pragma once

/* Source: https://gist.github.com/fnky/458719343aabd01cfb17a3a4f7296797 */

/* Control characters */
#define SHELL_VT100_ASCII_ESC    (0x1b)
#define SHELL_VT100_ASCII_CTRL_C (0x03)
#define SHELL_VT100_ASCII_BSPACE (0x08)
#define SHELL_VT100_ASCII_UP     ('A')
#define SHELL_VT100_ASCII_DOWN   ('B')

/* Colour codes */
#define SHELL_COLOUR_BLACK   "30"
#define SHELL_COLOUR_RED     "31"
#define SHELL_COLOUR_GREEN   "32"
#define SHELL_COLOUR_YELLOW  "33"
#define SHELL_COLOUR_BLUE    "34"
#define SHELL_COLOUR_MAGENTA "35"
#define SHELL_COLOUR_CYAN    "36"
#define SHELL_COLOUR_WHITE   "30"
#define SHELL_COLOUR_RESET   "\033[0m"
#define SHELL_COLOUR(COLOR)  "\033[0;" COLOR "m"
#define SHELL_BOLD(COLOR)    "\033[1;" COLOR "m"
