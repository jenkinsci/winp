#include "stdafx.h"

extern "C"
BOOL WINAPI _DllMainCRTStartup(HANDLE  hDllHandle, DWORD   dwReason, LPVOID  lpreserved) {
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
