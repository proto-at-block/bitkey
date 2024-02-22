#pragma once

// Force a crash. Always prefer this over an infinite loop
// if the code path is sensitive to fault injection, because
// a fault can skip out of the loop.
#define FATAL() *(volatile uint32_t*)0;
