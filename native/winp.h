#pragma once
#include "stdafx.h"

// Sets bit 29 in order to keep the codes in the user space
#define reportErrorWithCode(env,code,msg)	SetLastError(code + 0x10000000); error(env,__FILE__,__LINE__,msg);
#define reportError(env,msg)	error(env,__FILE__,__LINE__,msg);
void error(JNIEnv* env, const char* file, int line, const char* msg);

std::wstring GetDllFilename();

BOOL WINAPI SendCtrlC(IN DWORD dwProcessId);

//
// Kernel32.dll
//

BOOL WINAPI KillProcessEx(IN DWORD dwProcessId, IN BOOL bTree);

// https://msdn.microsoft.com/en-us/library/ms684139.aspx
typedef BOOL(WINAPI *LPFN_ISWOW64PROCESS) (HANDLE, PBOOL);
// Reference to the IsWow64Process method.
// It is being handled via the reference, because the method is not available for the non-desktop-app mode (e.g. Windows service or AppVeyor build)
extern LPFN_ISWOW64PROCESS fnIsWow64Process;

//
// NTDLL functions
//

// see http://msdn2.microsoft.com/en-us/library/aa489609.aspx
#define NT_SUCCESS(Status) ((NTSTATUS)(Status) >= 0)

enum PROCESSINFOCLASS {
	// see http://msdn2.microsoft.com/en-us/library/ms687420(VS.85).aspx
	ProcessBasicInformation = 0,
	ProcessWow64Information = 26,
	ProcessBreakOnTermination = 29,
};

enum MBI_REGION_STATE : DWORD {
	/// For MEMORY_BASIC_IONFORMATION#State
	// https://msdn.microsoft.com/en-us/library/windows/desktop/aa366775(v=vs.85).aspx
	Allocated = MEM_COMMIT,
	Free = MEM_FREE,
	Reserved = MEM_RESERVE
};

enum MBI_REGION_PROTECT : DWORD {
	/// For MEMORY_BASIC_IONFORMATION#Protect
	// https://msdn.microsoft.com/en-us/library/windows/desktop/aa366786(v=vs.85).aspx
	NoAccessToCheck = 0,
	NoAccess = PAGE_NOACCESS,
	// Documentation does not really say it is not readable, but it seems so according to the samples in the internet and existense of PAGE_EXECUTE_READ
	ExecuteOnly = PAGE_EXECUTE
	//TODO: Add other flags on-demand
};

enum MBI_REGION_TYPE : DWORD {
	/// For MEMORY_BASIC_IONFORMATION#Type
	// https://msdn.microsoft.com/en-us/library/windows/desktop/aa366775(v=vs.85).aspx
	Image = MEM_IMAGE,
	Mapped = MEM_MAPPED,
	Private = MEM_PRIVATE
};

extern "C" NTSTATUS NTAPI ZwQueryInformationProcess(HANDLE hProcess, PROCESSINFOCLASS infoType, /*out*/ PVOID pBuf, /*sizeof pBuf*/ ULONG lenBuf, SIZE_T* /*PULONG*/ returnLength); 


#define	SystemProcessesAndThreadsInformation 5

#define STATUS_INFO_LENGTH_MISMATCH      ((NTSTATUS)0xC0000004L)

extern "C" NTSTATUS NTAPI ZwQuerySystemInformation(UINT, PVOID, ULONG, PULONG);
