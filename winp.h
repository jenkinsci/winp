#pragma once
#include "stdafx.h"

BOOL WINAPI KillProcessEx( IN DWORD dwProcessId, IN BOOL bTree );

#define reportError(env,msg)	error(env,__FILE__,__LINE__,msg);
void error( JNIEnv* env, const char* file, int line, const char* msg );
