from invoke import task

from bitkey.meson import MesonBuild

from .lib.paths import *
from .lib.platforms import *


@task(default=True,
      help={
          "run_all": "Run unit tests after building",
          "target": "Build and run a specific test target",
          "debug": "Debug a specific unit test executable specified by target",
          "verbose": "Set to true for more build output",
      })
def test(c, run_all=True, target="", verbose=False, debug=False):
    """Builds and runs unit tests"""

    m = MesonBuild(c, "posix", BUILD_HOST_DIR)
    m.setup()
    m.build_tests(target, verbose, debug)

    if target:
        run_all = False

    if run_all:
        with c.cd(BUILD_HOST_DIR):
            c.run(f"meson test -v")


@task(help={
    "report": "Run the reporting server after tests completes",
    "testcase": "Name of single test to collect",
})
def automation(c, report=False, testcase=""):
    """Runs the automation test suite"""
    with c.cd(AUTOMATION_DIR):
        pytest_cmd = f"pytest --alluredir=allure --ignore=mfg_test"
        if testcase:
            pytest_cmd += " -k %s" % testcase
        c.run(pytest_cmd)

        if report:
            c.run("allure serve allure")


@task(help={
    "report": "Run the reporting server after tests completes",
    "testcase": "Name of single test to collect",
})
def automation_mfgtest(c, report=False, testcase=""):
    """Runs the mfg-test automation test suite"""
    with c.cd(AUTOMATION_DIR):
        pytest_cmd = f"pytest mfg_test --alluredir=allure"
        c.run(pytest_cmd)

        if report:
            c.run("allure serve allure")


@task()
def report(c):
    """Runs the allure report server for the latest test run"""
    with c.cd(AUTOMATION_DIR):
        c.run("allure serve allure")


@task()
def clear_reports(c):
    """Clears all previous runs from allure for a clean report"""
    with c.cd(AUTOMATION_DIR):
        c.run("rm -rf allure")
