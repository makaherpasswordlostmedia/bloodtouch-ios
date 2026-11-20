"""Print the engine process id. 'pidof -s com.bloodtouch' returns the launcher or nothing - the
render loop lives in the ':game' process, so the name has to be quoted for the shell."""
import re
import subprocess

print(re.sub(r"\D", "", subprocess.run(
    ["adb", "shell", "pidof -s 'com.bloodtouch:game'"], capture_output=True, text=True).stdout))
