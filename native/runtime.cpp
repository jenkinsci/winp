#include "stdafx.h"
#include "winp.h"
#include <vector>

HANDLE              hDllInst;
LPFN_ISWOW64PROCESS fnIsWow64Process;

extern "C"
BOOL WINAPI DllMain(HANDLE hInst, ULONG dwReason, LPVOID lpReserved) {
	hDllInst = hInst;
	fnIsWow64Process = (LPFN_ISWOW64PROCESS)GetProcAddress(
		GetModuleHandle(TEXT("kernel32")), "IsWow64Process");
	return TRUE;
}

std::wstring GetDllFilename() {
  std::vector<wchar_t> pathBuf;
  DWORD copied = 0;
  do {
    pathBuf.resize(pathBuf.size() + MAX_PATH);
    copied = GetModuleFileNameW(static_cast<HMODULE>(hDllInst), &pathBuf[0], static_cast<DWORD>(pathBuf.size()));
  }
  while (copied >= pathBuf.size());

  return std::wstring(pathBuf.begin(), pathBuf.begin() + copied);
}

void error( JNIEnv* env, const char* file, int line, const char* msg ) {
	DWORD errorCode = GetLastError();

	jclass winpException = env->FindClass("org/jvnet/winp/WinpException");
	if(winpException==0)
		env->FatalError("Failed to find WinpException");

	jmethodID winpExceptionConstructor = env->GetMethodID(
		winpException,"<init>","(Ljava/lang/String;ILjava/lang/String;I)V");
	if(winpExceptionConstructor==0)
		env->FatalError("Failed to find constructor");

	env->ExceptionClear();
	env->Throw(
		(jthrowable)env->NewObject( winpException, winpExceptionConstructor,
			env->NewStringUTF(msg), errorCode, env->NewStringUTF(file), line ));
}
