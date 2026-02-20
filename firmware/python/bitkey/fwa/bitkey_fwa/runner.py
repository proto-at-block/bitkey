import copy
import io
import json
import os
import sys
import unittest
from pathlib import Path
from typing import Optional

from . import constants, discovery, firmware_tests, fwut, reporter
from .fwut import root_fw_dir


def _run_tests(
    tests: unittest.TestSuite,
    input: Path,
    output: Optional[Path],
    signer_env: str,
    stack_name: Optional[str],
    dry_run: bool,
    verbose: bool,
    stream: io.TextIOWrapper = sys.stdout,
) -> bool:
    """Execute the given tests for the given input and parameters"""
    if stream is None:
        stream = open(os.devnull, "w")

    # Setup environment
    fwut.FirmwareUnderTest.load(input)

    fwut.FirmwareUnderTest.signer_env = signer_env
    fwut.FirmwareUnderTest.stack_name = stack_name
    fwut.FirmwareUnderTest.dry_run = dry_run

    # Translate true/false to the weird unittest verbosity system
    verbosity = 1
    if verbose:
        verbosity = 2

    # Run the tests
    runner = unittest.TextTestRunner(
        stream=stream, verbosity=verbosity, resultclass=reporter.TestResult
    )
    result = runner.run(tests)
    report = reporter.generate_report(result)

    if not output:
        output = input.name + ".report.json"
    if output:
        with open(output, "w") as f:
            json.dump(report, f, indent=2)

    # Print test failures/errors to console
    for t in result.failures + result.errors:
        print("%s: %s: %s" % (t.status, t.test, t.detail))
        print(t.traceback)

    return result.wasSuccessful()


def run_analysis(
    input: Path,
    output: Optional[Path],
    signer_env: constants.SIGNER_ENVS,
    stack_name: Optional[str],
    dry_run: bool,
    verbose: bool,
) -> bool:
    """Execute firmware analysis.

    NOTE: The original file name, as produced by the bitkey build system, must be preserved.

    Args:
        input: Path to the file to analyze
        output: Path to store test results
        signer_env: Signer environment
        stack_name: Stack name
        dry_run: Set to True to only print the tests that would be performed and skip execution
        verbose: Verbose console output

    Returns:
        True if all tests were successful, False otherwise
    """

    # Get the tests
    loader = discovery.get_firmware_test_loader()
    tests = loader.discover(firmware_tests.directory, pattern="fwtest*.py")

    return _run_tests(
        tests=tests,
        input=input,
        output=output,
        signer_env=signer_env,
        stack_name=stack_name,
        dry_run=dry_run,
        verbose=verbose,
    )


def run_bulk_analysis(stream: io.TextIOWrapper = sys.stdout):
    """Execute analysis over all supported artifacts in the root fw dir.

    Args:
        stream (file|None): File-like object to write the analysis report to

    Note that general status and progress messages are always written to stdout.
    """

    # artifact matches cannot contain these strings
    denylist = ["nometa"]

    # Get the tests
    loader = discovery.get_firmware_test_loader()
    tests = loader.discover(firmware_tests.directory, pattern="fwtest*.py")

    glob_pattern = "build/firmware/**/app/**/**/*.elf"
    artifacts = root_fw_dir.glob(glob_pattern)
    to_analyze = [a for a in artifacts if not any(b in a.name for b in denylist)]
    total = len(to_analyze)

    print("Performing bulk firmware analysis.")
    if total == 0:
        print(
            f"ERROR: No firmware artifacts found (pattern: {glob_pattern}).",
            file=sys.stderr,
        )
        return False

    results = []
    for count, (filename) in enumerate(to_analyze):
        print(f"[{count + 1}/{total}] Analyzing {filename}")
        input = filename
        result = _run_tests(
            tests=copy.deepcopy(tests),
            input=input,
            output=None,  # Will create based on input file name
            signer_env=constants.SIGNER_PERSONAL,
            stack_name=None,
            dry_run=False,
            verbose=False,
            stream=stream,
        )
        results.append(result)

    return False not in results
