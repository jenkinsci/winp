#pragma once
#include "stdafx.h"

BOOL WINAPI KillProcessEx( IN DWORD dwProcessId, IN BOOL bTree );

#define reportError(env,msg)	error(env,__FILE__,__LINE__,msg);
void error( JNIEnv* env, const char* file, int line, const char* msg );






//
//
// NTDLL functions
//
//

// see http://msdn2.microsoft.com/en-us/library/aa489609.aspx
#define NT_SUCCESS(Status) ((NTSTATUS)(Status) >= 0)

enum PROCESSINFOCLASS {
	// see http://msdn2.microsoft.com/en-us/library/ms687420(VS.85).aspx
	ProcessBasicInformation = 0,
};

extern "C" NTSTATUS NTAPI ZwQueryInformationProcess(HANDLE hProcess, PROCESSINFOCLASS infoType, /*out*/ PVOID pBuf, /*sizeof pBuf*/ ULONG lenBuf, PULONG returnLength); 


#define	SystemProcessesAndThreadsInformation 5

#define STATUS_INFO_LENGTH_MISMATCH      ((NTSTATUS)0xC0000004L)

extern "C" NTSTATUS NTAPI ZwQuerySystemInformation(UINT, PVOID, ULONG, PULONG);
