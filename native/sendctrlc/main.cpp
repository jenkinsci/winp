
#define STRICT
#define WIN32_LEAN_AND_MEAN
#include <Windows.h>

#include <cstdlib>
#include <cstring>

int main(int argc, char** argv) {
  if (argc < 2) {
    return 2;
  }

  int  pid = atoi(argv[1]);

  FreeConsole();
  if (!AttachConsole(pid)) {
    return 1;
  }

  SetConsoleCtrlHandler(NULL, TRUE);
  GenerateConsoleCtrlEvent(CTRL_C_EVENT, 0);
  return 0;
}
