The `libs/` layer contains highly reusable, infrastructure-focused modules that provide low-level libraries and utilities, designed to be domain-agnostic and independent of specific business logic.

These modules can depend on other `libs/` modules and external libraries but must remain independent of higher layers like domain and UI.
