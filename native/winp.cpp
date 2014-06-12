// taken from http://www.alexfedotov.com/articles/killproc.asp
//

#include "stdafx.h"
#include "winp.h"
#include "auto_handle.h"

//---------------------------------------------------------------------------
// KillProcess
//
//  Terminates the specified process.
//
//  Parameters:
//	  dwProcessId - identifier of the process to terminate
//
//  Returns:
//	  TRUE, if successful, FALSE - otherwise.
//
BOOL WINAPI KillProcess(DWORD dwProcessId) {
	// first try to obtain handle to the process without the use of any
	// additional privileges
	auto_handle hProcess = OpenProcess(PROCESS_TERMINATE, FALSE, dwProcessId);
	if (!hProcess)
		return FALSE;

	// terminate the process
	if (!TerminateProcess(hProcess, (UINT)-1)) {
		return FALSE;
	}

	// completed successfully
	return TRUE;
}


typedef LONG	KPRIORITY;

typedef struct _CLIENT_ID {
    DWORD	    UniqueProcess;
    DWORD	    UniqueThread;
} CLIENT_ID;

typedef struct _UNICODE_STRING {
    USHORT	    Length;
    USHORT	    MaximumLength;
    PWSTR	    Buffer;
} UNICODE_STRING;

typedef struct _VM_COUNTERS {
    SIZE_T	    PeakVirtualSize;
    SIZE_T	    VirtualSize;
    ULONG	    PageFaultCount;
    SIZE_T	    PeakWorkingSetSize;
    SIZE_T	    WorkingSetSize;
    SIZE_T	    QuotaPeakPagedPoolUsage;
    SIZE_T	    QuotaPagedPoolUsage;
    SIZE_T	    QuotaPeakNonPagedPoolUsage;
    SIZE_T	    QuotaNonPagedPoolUsage;
    SIZE_T	    PagefileUsage;
    SIZE_T	    PeakPagefileUsage;
} VM_COUNTERS;

typedef struct _SYSTEM_THREADS {
    LARGE_INTEGER   KernelTime;
    LARGE_INTEGER   UserTime;
    LARGE_INTEGER   CreateTime;
    ULONG			WaitTime;
    PVOID			StartAddress;
    CLIENT_ID	    ClientId;
    KPRIORITY	    Priority;
    KPRIORITY	    BasePriority;
    ULONG			ContextSwitchCount;
    LONG			State;
    LONG			WaitReason;
} SYSTEM_THREADS, * PSYSTEM_THREADS;

// Note that the size of the SYSTEM_PROCESSES structure is different on
// NT 4 and Win2K, but we don't care about it, since we don't access neither
// IoCounters member nor Threads array

typedef struct _SYSTEM_PROCESSES {
    ULONG			NextEntryDelta;
    ULONG			ThreadCount;
    ULONG			Reserved1[6];
    LARGE_INTEGER   CreateTime;
    LARGE_INTEGER   UserTime;
    LARGE_INTEGER   KernelTime;
    UNICODE_STRING  ProcessName;
    KPRIORITY	    BasePriority;
    ULONG			ProcessId;
    ULONG			InheritedFromProcessId;
    ULONG			HandleCount;
    ULONG			Reserved2[2];
    VM_COUNTERS	    VmCounters;
#if _WIN32_WINNT >= 0x500
    IO_COUNTERS	    IoCounters;
#endif
    SYSTEM_THREADS  Threads[1];
} SYSTEM_PROCESSES, * PSYSTEM_PROCESSES;

//---------------------------------------------------------------------------
// KillProcessTreeNtHelper
//
//  This is a recursive helper function that terminates all the processes
//  started by the specified process and them terminates the process itself
//
//  Parameters:
//	  pInfo       - processes information
//	  dwProcessId - identifier of the process to terminate
//
//  Returns:
//	  Win32 error code.
//
BOOL WINAPI KillProcessTreeNtHelper(PSYSTEM_PROCESSES pInfo, DWORD dwProcessId) {
	_ASSERTE(pInfo != NULL);

    PSYSTEM_PROCESSES p = pInfo;

    // kill all children first
    for (;;)
    {
		if (p->InheritedFromProcessId == dwProcessId)
			KillProcessTreeNtHelper(pInfo, p->ProcessId);

		if (p->NextEntryDelta == 0)
			break;

		// find the address of the next process structure
		p = (PSYSTEM_PROCESSES)(((LPBYTE)p) + p->NextEntryDelta);
    }

	// kill the process itself
    if (!KillProcess(dwProcessId))
		return GetLastError();

	return ERROR_SUCCESS;
}

//---------------------------------------------------------------------------
// KillProcessTreeWinHelper
//
//  This is a recursive helper function that terminates all the processes
//  started by the specified process and them terminates the process itself
//
//  Parameters:
//	  dwProcessId - identifier of the process to terminate
//
//  Returns:
//	  Win32 error code.
//
BOOL WINAPI KillProcessTreeWinHelper(DWORD dwProcessId) {
	// create a snapshot
	auto_handle hSnapshot = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
	if (!hSnapshot)
		return GetLastError();

	auto_localmem<PROCESSENTRY32*> pEntry(sizeof(PROCESSENTRY32));

	pEntry->dwSize = sizeof(PROCESSENTRY32);
	if (!Process32First(hSnapshot, pEntry))
	{
		return GetLastError();
	}

	// kill all children first
	do
	{
		// there was a report of infinite recursion, so watching out for the obvious self-loop possibility
		DWORD pid = pEntry->th32ProcessID;
		if (pEntry->th32ParentProcessID == dwProcessId && dwProcessId!=pid)
			KillProcessTreeWinHelper(pid);
	}
	while (Process32Next(hSnapshot, pEntry));

	// kill the process itself
    if (!KillProcess(dwProcessId))
		return GetLastError();

	return ERROR_SUCCESS;
}

//---------------------------------------------------------------------------
// KillProcessEx
//
//  Terminates the specified process and, optionally, all processes started
//	from the specified process (the so-called process tree).
//
//  Parameters:
//	  dwProcessId - identifier of the process to terminate
//	  bTree		  - specifies whether the entire process tree should be
//					terminated
//
//  Returns:
//	  TRUE, if successful, FALSE - otherwise.
//
BOOL WINAPI KillProcessEx(DWORD dwProcessId, BOOL bTree) {
	if (!bTree)
		return KillProcess(dwProcessId);

	OSVERSIONINFO osvi;
	DWORD dwError;

	// determine operating system version
	osvi.dwOSVersionInfoSize = sizeof(osvi);
	GetVersionEx(&osvi);

	if (osvi.dwPlatformId == VER_PLATFORM_WIN32_NT &&
		osvi.dwMajorVersion < 5)
	{
		// obtain a handle to the default process heap
		HANDLE hHeap = GetProcessHeap();
    
		NTSTATUS Status;
		ULONG cbBuffer = 0x8000;
		PVOID pBuffer = NULL;

		// it is difficult to say a priory which size of the buffer 
		// will be enough to retrieve all information, so we start
		// with 32K buffer and increase its size until we get the
		// information successfully
		do
		{
			pBuffer = HeapAlloc(hHeap, 0, cbBuffer);
			if (pBuffer == NULL)
				return SetLastError(ERROR_NOT_ENOUGH_MEMORY), FALSE;

			Status = ZwQuerySystemInformation(
							SystemProcessesAndThreadsInformation,
							pBuffer, cbBuffer, NULL);

			if (Status == STATUS_INFO_LENGTH_MISMATCH)
			{
				HeapFree(hHeap, 0, pBuffer);
				cbBuffer *= 2;
			}
			else if (!NT_SUCCESS(Status))
			{
				HeapFree(hHeap, 0, pBuffer);
				return SetLastError(Status), NULL;
			}
		}
		while (Status == STATUS_INFO_LENGTH_MISMATCH);

		// call the helper function
		dwError = KillProcessTreeNtHelper((PSYSTEM_PROCESSES)pBuffer, 
										  dwProcessId);
		
		HeapFree(hHeap, 0, pBuffer);
	}
	else
	{
		// call the helper function
		dwError = KillProcessTreeWinHelper(dwProcessId);
	}

	SetLastError(dwError);
	return dwError == ERROR_SUCCESS;
}
