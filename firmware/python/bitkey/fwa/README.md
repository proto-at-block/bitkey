# bitkey-fwa

The Bitkey firmware analysis tool.

## Overview

This suite of firmware tests identifies security-relevant issues. The tool is intended to be used as an additional
check during production signing, CI tests, or local testing.

## Usage

```
python main.py analyze -i path/to/<application/loader>/<target_name>.elf
```

Or:

```
python main.py bulk-analyze
```
bulk-analyze will find and analyze all `elf` files within the build directory

Return values:

* 0: All filtered tests successful
* 1: Firmware analysis failed or error in command line arguments

Files created:
`<input>.report.json` is created if an output file is not specified.

### Firmware Test Authoring

Firmware tests should be designed like unit tests, where a small aspect of the firmware is analyzed and validated.

Each invocation of the tool analyzes a single firmware artifact in isolation. Information about the firmware under test
can be retrieved at runtime in the `fwut` module. See `fwut.FirmwareUnderTest` for more information.

Firmware tests must:

* be a method within a `bitkey_fwa.TestCase` derived class,
* be a method whose name starts with `fwtest_`, and
* live in a file whose name starts with `fwtest`.

This is an intentional differentiation from normal unittests to avoid confusion or accidental imports.

### Firmware Test Filtering

Tests may be filtered to limit the scope of a test to a particular set of firmware artifacts. Test filtering is
performed using Python decorators. Decorators are filters that take arguments.

Filtering is done with the following rules:

* By default, a test is applicable to any firmware under test (no decorators)
* Decorators are used to limit the scope of an individual test function
* Every specified filter must apply to a given firmware for a test to be executed
  * In other words, decorator types `AND`ed together
* At least one filter argument must apply to a given firmware for a test to be executed
  * In other words, decorator arguments are `OR`ed together

The following test will only execute for the `w1a-dvt-app-b-mfgtest-dev.signed.elf` artifact:

```python
    @bitkey_fwa.product("w1a")
    @bitkey_fwa.platform("dvt")
    @bitkey_fwa.asset("app")
    @bitkey_fwa.slot("b")
    @bitkey_fwa.environment("mfgtest")
    @bitkey_fwa.security("dev")
    @bitkey_fwa.suffix("elf")
    def fwtest_foobar(self):
        pass
```

The following test will execute for `w1a-*-dev*.elf` or `w1a-*-prod*.elf` artifacts:

```python
    @bitkey_fwa.product("w1a")
    @bitkey_fwa.security("dev")
    @bitkey_fwa.security("prod")
    @bitkey_fwa.suffix("elf")
    def fwtest_foobar(self):
        pass
```

Duplicate decorations are treated as if all arguments were passed to a single decorator. Therefore:

```python
    @bitkey_fwa.security("dev")
    @bitkey_fwa.security("prod")
    def fwtest_foobar(self):
        pass
```

is equivalent to

```python
    @bitkey_fwa.security("dev", "prod")
    def fwtest_foobar(self):
        pass
```
