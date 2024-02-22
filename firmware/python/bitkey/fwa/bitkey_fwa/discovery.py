import unittest


def get_firmware_test_loader(prefix: str = "fwtest_") -> unittest.TestLoader:
    """Retrieve a loader that will discover tests.

    The only appreciable difference compared to the standard unittest loader is the use of a custom function prefix.
    """
    loader = unittest.TestLoader()
    loader.testMethodPrefix = prefix
    return loader
