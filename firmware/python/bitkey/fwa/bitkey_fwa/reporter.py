import collections
import datetime
import traceback

import pytz

from . import fwut

TestTuple = collections.namedtuple("TestTuple", "status test detail traceback")


def generate_report(result):
    """Generate a report object for the given results

    The report is directly JSON-able

    Args:
        result (TestResult): Firmware test result object

    Returns:
        dict: JSON-able dictionary

    In the future, we may wish to utilize a common report format across different security tools. We can update this
    once a format has been agreed upon, or make a translation tool.
    """
    # Setup the report skeleton
    report = {
        "creator": {
            "name": "bitkey_fwa",
        },
        "summary": {
            "successful": result.wasSuccessful(),
            "test_count": result.testsRun,
            "failure_count": len(result.failures),
            "error_count": len(result.errors),
            "warning_count": len(result.warnings),
            "skip_count": len(result.skipped),
            "test_start_time": result.start_time.isoformat(),
            "test_stop_time": result.stop_time.isoformat(),
        },
        "artifact": {
            "type": "bitkey firmware",
            "filename": fwut.FirmwareUnderTest.filename,
            "product": fwut.FirmwareUnderTest.product,
            "asset": fwut.FirmwareUnderTest.asset,
            "slot": fwut.FirmwareUnderTest.slot,
            "security": fwut.FirmwareUnderTest.security,
            "environment": fwut.FirmwareUnderTest.environment,
            "suffix": fwut.FirmwareUnderTest.suffix,
        },
        "tests": [],
    }

    # Indicate that this is not a real test
    if fwut.FirmwareUnderTest.dry_run:
        report["summary"]["dry_run"] = True

    # Format tests
    for t in result.tests:
        entry = {
            # Fully qualified name, in the form of module.ClassName.method_name
            "fqn": t.test.id() or "UNKNOWN",
            "description": t.test.shortDescription() or "UNKNOWN",
            "result": t.status,
        }
        if t.detail:
            entry["result_detail"] = t.detail

        report["tests"].append(entry)

    return report


class TestResult(object):
    """A test result class that logs the result to a central data store and prints a human-readable output to a stream.

    This class is compatible with unittest.TestResult, but does not extend it to avoid confusion -- all bookkeeping is
    unique to our implementation to support report generation.

    There are two output types. Verbose, and not verbose (default).

    Verbose:
        module_A.Class.fwtest_foo
        Short description foo ................................................. FAIL
          AssertionError: Description

        module_A.Class.fwtest_bar
        Short description bar ................................................. SKIP (Wrong x, y, z)

        module_B.Class.fwtest_baz
        Short description baz ................................................. WARNING
          Description

        module_C.Class.fwtest_a
        Short description a ................................................... ERROR
          UnexpectedErrorClass: Description

        module_C.Class.fwtest_b
        Short description b ................................................... OK

    Not verbose:
        Short description foo ................................................. FAIL
        Short description bar ................................................. SKIP
        Short description baz ................................................. WARNING
        Short description a ................................................... ERROR
        Short description b ................................................... OK

    Both cases will print out tracebacks for FAIL and ERROR cases after the completion of all tests.
    """

    # Used by the test runner
    separator1 = "=" * 80
    separator2 = "-" * 80

    JUST_WIDTH = 70
    VERT_SEPARATOR = "|"
    JUST_SEPARATOR = "."

    RESULT_OK = "OK"
    RESULT_SKIPPED = "SKIP"
    RESULT_WARNING = "WARNING"
    RESULT_ERROR = "ERROR"
    RESULT_FAILURE = "FAILURE"

    def __init__(self, stream=None, descriptions=None, verbosity=None):
        self.stream = stream
        self.verbose = verbosity > 1

        self.start_time = None
        self.stop_time = None

        self.tests = []

    def _format_doc_line(self, doc_line):
        # Pad with a space and truncate if necessary
        doc_line = doc_line[: self.JUST_WIDTH - 1] + " "
        return doc_line.ljust(self.JUST_WIDTH, self.JUST_SEPARATOR) + " "

    def _get_test_id(self, test):
        # Handle cases where a test errors out due to a module not loading (special failed test object)
        if test is None or not hasattr(test, "id"):
            return "Unknown"
        return test.id()

    def getDescription(self, test):
        """Get the text description"""
        doc_first_line = test.shortDescription() or ""
        return "\n".join((self._get_test_id(test), doc_first_line))

    def getReportDescription(self, test):
        """Get the report text description based on the verbosity"""
        doc_first_line = self._format_doc_line(test.shortDescription() or "")
        if self.verbose:
            return "\n".join(("", self._get_test_id(test), doc_first_line))
        return doc_first_line

    @property
    def shouldStop(self):
        return False

    def startTestRun(self):
        self.start_time = datetime.datetime.now(pytz.UTC)

    def stopTestRun(self):
        self.stop_time = datetime.datetime.now(pytz.UTC)

    def startTest(self, test):
        self.stream.write(self.getReportDescription(test))
        self.stream.flush()

    def stopTest(self, test):
        self.stream.flush()

    def addSuccess(self, test):
        self.tests.append(TestTuple(self.RESULT_OK, test, None, None))
        self.stream.writeln(self.RESULT_OK)

    def addError(self, test, err):
        """This is called whenever this is an unexpected exception raised"""
        exception_type, exception, tb = err

        # Print out warnings, but treat as success
        if exception_type == Warning:
            self.tests.append(TestTuple(self.RESULT_WARNING,
                              test, str(exception), None))
            self.stream.writeln(self.RESULT_WARNING)
            verbose_message = "  {}".format(exception)
        else:
            self.tests.append(TestTuple(self.RESULT_ERROR,
                              test, str(exception), self._tb_string(err)))
            self.stream.writeln(self.RESULT_ERROR)
            verbose_message = "  {}: {}".format(
                exception_type.__name__, exception)

        if self.verbose:
            self.stream.writeln(verbose_message)

    def addFailure(self, test, err):
        exception_type, exception, tb = err
        self.tests.append(TestTuple(self.RESULT_FAILURE, test,
                          str(exception), self._tb_string(err)))
        self.stream.writeln(self.RESULT_FAILURE)
        if self.verbose:
            self.stream.writeln("  {}: {}".format(
                exception_type.__name__, exception))

    def addSkip(self, test, reason):
        self.tests.append(TestTuple(self.RESULT_SKIPPED, test, reason, None))
        if self.verbose:
            self.stream.writeln("{} ({})".format(self.RESULT_SKIPPED, reason))
        else:
            self.stream.writeln(self.RESULT_SKIPPED)

    def addExpectedFailure(self, test, err):
        raise NotImplementedError(
            "Expected Failures results are not supported in firmware analysis testing")

    def addUnexpectedSuccess(self, test):
        raise NotImplementedError(
            "Unexpected Success results are not supported in firmware analysis testing")

    def printErrors(self):
        self.stream.writeln()
        self.printErrorList("ERROR", self.errors)
        self.printErrorList("FAIL", self.failures)

    def printErrorList(self, flavour, errors):
        for t in errors:
            self.stream.writeln(self.separator1)
            self.stream.writeln("%s: %s" %
                                (flavour, self.getDescription(t.test)))
            self.stream.writeln(self.separator2)
            self.stream.writeln("%s" % t.traceback)

    def wasSuccessful(self):
        return len(self.errors) == len(self.failures) == 0

    @property
    def testsRun(self):
        return len(self.tests)

    @property
    def warnings(self):
        """For backward compatibility"""
        return [t for t in self.tests if t.status == self.RESULT_WARNING]

    @property
    def skipped(self):
        """For backward compatibility"""
        return [t for t in self.tests if t.status == self.RESULT_SKIPPED]

    @property
    def errors(self):
        """For backward compatibility"""
        return [t for t in self.tests if t.status == self.RESULT_ERROR]

    @property
    def failures(self):
        """For backward compatibility"""
        return [t for t in self.tests if t.status == self.RESULT_FAILURE]

    @property
    def expectedFailures(self):
        """For backward compatibility"""
        return []

    @property
    def unexpectedSuccesses(self):
        """For backward compatibility"""
        return []

    def _tb_string(self, err):
        """Converts a sys.exc_info()-style tuple of values into a string.

        Note: Based on function in unittest.TestResult
        """
        exception_type, exception, tb = err
        # Skip test runner traceback levels
        while tb and self._is_relevant_tb_level(tb):
            tb = tb.tb_next

        if exception_type is AssertionError:
            # Skip assert*() traceback levels
            length = self._count_relevant_tb_levels(tb)
            message_lines = traceback.format_exception(
                exception_type, exception, tb, length)
        else:
            message_lines = traceback.format_exception(
                exception_type, exception, tb)

        return "".join(message_lines)

    def _is_relevant_tb_level(self, tb):
        return "__unittest" in tb.tb_frame.f_globals

    def _count_relevant_tb_levels(self, tb):
        length = 0
        while tb and not self._is_relevant_tb_level(tb):
            length += 1
            tb = tb.tb_next
        return length
