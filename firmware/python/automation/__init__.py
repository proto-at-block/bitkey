try:
  import os
  import tasks
except ImportError:
  import sys
  sys.path.append(os.path.dirname(
      os.path.abspath(__file__)) + "/../../")
  import tasks
