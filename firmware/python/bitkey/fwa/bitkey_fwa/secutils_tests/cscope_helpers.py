import subprocess
from collections import defaultdict
import os
from macro_constants import macro_weight


def run_cscope(dir_path: str) -> None:
    """
    Runs cscope on the given directory. The function does not return anything.
    This generates cscope files that can be parsed later.
    
    Args:
        dir_path (str): The directory path.
    """
    os.chdir(dir_path)
    print(f"Running cscope on {dir_path}")
    subprocess.run(["cscope", "-Rbqk", "-I", "."], capture_output=True, text=True)


def get_function_macro_counts(dir_path: str, macros: list) -> dict[str, int]:
    """
    Returns the number of times secutils_fixed_true is used per function.
    This only outputs non zero occurences of secutils_fixed_true.
    
    Args:
        dir_path (str): The directory path.
        macros (List[str]): The list of macro names.
    
    Returns:
        Dict[str, int]: A dictionary that maps function names to the number of times secutils_fixed_true is used in that function.
    """
    os.chdir(dir_path)
    secutils_count = defaultdict(int)

    for macro_name in macros:
        result = subprocess.run(["cscope", "-RLd", "-3", macro_name], capture_output=True, text=True)
        lines = result.stdout.split('\n')
        for line in lines:
            fields = line.split()
            if len(fields) > 1:
                if fields[1] in macros:
                    continue
                function_name = fields[1]
                secutils_count[function_name] += macro_weight[macro_name]
    return secutils_count
