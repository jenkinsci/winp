#include "stdafx.h"
#include "winp.h"

LPFN_ISWOW64PROCESS fnIsWow64Process;

extern "C"
BOOL WINAPI DllMain(HANDLE hInst, ULONG dwReason, LPVOID lpReserved) {
	fnIsWow64Process = (LPFN_ISWOW64PROCESS)GetProcAddress(
		GetModuleHandle(TEXT("kernel32")), "IsWow64Process");
	return TRUE;
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
