"""Decorators used to filter tests.

The decorators here are used to trigger bookkeeping activities at decoration time. This adds additional metadata to the
function, which is later used at runtime to determine if a test should be skipped or executed. A small wrapper function
is used to perform this check when a function is called.
"""

import unittest
import functools
import fnmatch
import collections
from typing import Callable, Optional

from . import constants, fwut

# Attribute added to wrapped functions to hold filter info
FILTER_DICT_ATTR = "_bfwa_filter"


def skip_unless_firmware_applies(function: Callable) -> Optional[bool]:
    """Check the decorator information for a given function against the current firmware.

    Raises:
        SkipTest if there are any filters that do not pass for the given firmware/function combo

    See the Filtering Rules section in the README.
    """
    filter_dict = getattr(function, FILTER_DICT_ATTR, None)

    # No filters? Applies to all firmware.
    if not filter_dict:
        return True

    # Map of filter type to fwut info
    filter_map = {
        constants.FILTER_TYPE_PRODUCT: fwut.FirmwareUnderTest.product,
        constants.FILTER_TYPE_PLATFORM: fwut.FirmwareUnderTest.platform,
        constants.FILTER_TYPE_ASSET: fwut.FirmwareUnderTest.asset,
        constants.FILTER_TYPE_SLOT: fwut.FirmwareUnderTest.slot,
        constants.FILTER_TYPE_SECURITY: fwut.FirmwareUnderTest.security,
        constants.FILTER_TYPE_ENVIRONMENT: fwut.FirmwareUnderTest.environment,
        constants.FILTER_TYPE_SUFFIX: fwut.FirmwareUnderTest.suffix,
    }

    # Must pass all specified filters
    failed_filter_types = []
    for filter_type in sorted(filter_dict.keys()):
        patterns = filter_dict[filter_type]
        value = filter_map[filter_type]
        if not any(fnmatch.fnmatch(value, p) for p in patterns):
            failed_filter_types.append(filter_type)

    if failed_filter_types:
        raise unittest.SkipTest("Wrong {}".format(
            ", ".join(failed_filter_types)))


def warn_only(function: Callable):
    """Turn test failures (AssertionErrors) into Warning exceptions.

    The Warning exception will be caught by the TestResult class.
    """

    @functools.wraps(function)
    def suppress_assertion_error(*args, **kwargs):
        try:
            function(*args, **kwargs)
        except AssertionError as ex:
            raise Warning(str(ex))

    return suppress_assertion_error


class FilteredFunction(object):
    """Base class to use for decorators to aid in adding metadata to a decorated function.

    A function is filtered based on the given firmware under test. A decorator is used to specify necessary attributes
    of the firmware for the given function to be executed. The firmware must pass filtering by all decorators (AND'd
    together), but each decorator can specify one or more potential patterns (OR'd together)
    """

    def __init__(self, *args):
        """Creates a decorator with given filter arguments"""
        if len(args) < 1:
            raise ValueError(
                "Filtered function decorator requires at least one argument")
        self.args = args

        # Valid value for the filter?
        filter_type = self.get_filter_type()
        for arg in self.args:
            if arg not in constants.FILTER_TYPES[filter_type]:
                raise ValueError(
                    f"Invalid argument '{arg}' for decorator '{filter_type}'")

    def __call__(self, function: Callable) -> Callable:
        """Adds metadata to the function based on the configured filter, and wraps the function if needed."""
        # If this function has already been wrapped, just update the filter dictionary
        if hasattr(function, FILTER_DICT_ATTR):
            self._add_filter(function)
            return function

        # If this function has not been wrapped yet, create the dictionary and create the wrapper
        setattr(function, FILTER_DICT_ATTR, collections.defaultdict(set))
        self._add_filter(function)

        # Note: functools.wraps copies all attributes from the original function to the new wrapper func
        @functools.wraps(function)
        def filter_check(*args, **kwargs):
            skip_unless_firmware_applies(function)
            return function(*args, **kwargs)

        return filter_check

    def _add_filter(self, function: Callable):
        filter_type = self.get_filter_type()
        dictionary = getattr(function, FILTER_DICT_ATTR)

        # Set the metadata (to be used in the discovery process)
        dictionary[filter_type].update(self.args)

    def get_filter_type(self) -> str:
        """Return the filter type for the particular decorator instance.

        To be overridden by child classes. See constants.FILTER_TYPE*.

        Returns:
            String constant for the filter type
        """
        raise NotImplementedError(
            "Derived class did not specify its filter type")


class product(FilteredFunction):
    def get_filter_type(self) -> str:
        return constants.FILTER_TYPE_PRODUCT


class platform(FilteredFunction):
    def get_filter_type(self) -> str:
        return constants.FILTER_TYPE_PLATFORM


class asset(FilteredFunction):
    def get_filter_type(self) -> str:
        return constants.FILTER_TYPE_ASSET


class slot(FilteredFunction):
    def get_filter_type(self) -> str:
        return constants.FILTER_TYPE_SLOT


class security(FilteredFunction):
    def get_filter_type(self) -> str:
        return constants.FILTER_TYPE_SECURITY


class environment(FilteredFunction):
    def get_filter_type(self) -> str:
        return constants.FILTER_TYPE_ENVIRONMENT


class suffix(FilteredFunction):
    def get_filter_type(self) -> str:
        return constants.FILTER_TYPE_SUFFIX
