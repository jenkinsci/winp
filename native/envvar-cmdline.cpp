#include "stdafx.h"
#include "winp.h"
#include "java-interface.h"
#include "auto_handle.h"

// 32bit pointer type for wow64
// TODO: there are probably better ways to define this
#define PTR32(T)	DWORD

// Max size of error messages in the methods. Defines static buffer sizes
#define ERRMSG_SIZE 128

struct UNICODE_STRING {
	USHORT	Length;
	USHORT	MaxLength;
	union {
		PWSTR	Buffer;
		DWORD	_Buffer;
	};
};

struct UNICODE_STRING32 {// used on wow64
	USHORT			Length;
	USHORT			MaxLength;
	PTR32(PWSTR)	Buffer;
};


// http://msdn.microsoft.com/en-us/library/aa813741(VS.85).aspx
struct RTL_USER_PROCESS_PARAMETERS {
	BYTE dwFiller[16];
	PVOID	dwFiller2[10];
	UNICODE_STRING ImagePathName;
	UNICODE_STRING CommandLine;
	//TODO: It goes beyond the documented structure and likely causes https://github.com/kohsuke/winp/issues/29
	LPCWSTR env;
};

struct RTL_USER_PROCESS_PARAMETERS32 {// used on wow64
	BYTE dwFiller[16];
	PTR32(PVOID)	dwFiller2[10];
	UNICODE_STRING32 ImagePathName;
	UNICODE_STRING32 CommandLine;
	//TODO: It goes beyond the documented structure and likely causes https://github.com/kohsuke/winp/issues/29
	PTR32(LPCWSTR) env;
};


#ifdef _WIN64
	struct PEB  {
		BYTE dwFiller[24];
		PVOID dwFiltter2;
		RTL_USER_PROCESS_PARAMETERS* dwInfoBlockAddress;
	};
	struct PEB32 {
		DWORD dwFiller[4];
		PTR32(RTL_USER_PROCESS_PARAMETERS32*) dwInfoBlockAddress;

	};
#else
	struct PEB {
		DWORD dwFiller[4];
		RTL_USER_PROCESS_PARAMETERS* dwInfoBlockAddress;
	};
#endif

struct PROCESS_BASIC_INFORMATION {
	NTSTATUS ExitStatus;
	PEB* PebBaseAddress;
	PULONG AffinityMask;
	LONG BasePriority;
	PULONG UniqueProcessId;
	PULONG InheritedFromUniqueProcessId;
};

#ifdef _WIN64
static inline LPVOID Ptr32ToPtr64(PTR32(LPVOID) v) {
	return UlongToPtr(v);
}
#endif

jstring getCmdLineAndEnvVars(JNIEnv* pEnv, jclass clazz, jint pid, jint retrieveEnvVars);

JNIEXPORT jstring JNICALL Java_org_jvnet_winp_Native_getCmdLine(
	JNIEnv* pEnv, jclass clazz, jint pid) {
	return getCmdLineAndEnvVars(pEnv, clazz, pid, 0);
}

JNIEXPORT jstring JNICALL Java_org_jvnet_winp_Native_getCmdLineAndEnvVars(
	JNIEnv* pEnv, jclass clazz, jint pid) {
	return getCmdLineAndEnvVars(pEnv, clazz, pid, 1);
}

jstring getCmdLineAndEnvVars(
	JNIEnv* pEnv, jclass clazz, jint pid, jint retrieveEnvVars){
	
	char errorBuffer[ERRMSG_SIZE];
	bool isWoW64 = false;

	// see http://msdn2.microsoft.com/en-us/library/ms674678%28VS.85%29.aspx
	// for kernel string functions

	auto_handle hProcess = OpenProcess(PROCESS_QUERY_INFORMATION | PROCESS_VM_READ, FALSE, pid);
	if(!hProcess) {
		sprintf_s<ERRMSG_SIZE>(errorBuffer, "Failed to open process pid=%d for QUERY and VM_READ", pid);
		reportError(pEnv, errorBuffer);
		return NULL;
	}

	// Ensure the process is running, do not waste time otherwise
	DWORD exitCode;
	if (!GetExitCodeProcess(hProcess, &exitCode)) {
		sprintf_s<ERRMSG_SIZE>(errorBuffer, "Failed to check status of the process with pid=%d", pid);
		reportError(pEnv, errorBuffer);
		return NULL;
	}

	if (exitCode != STILL_ACTIVE) {
		sprintf_s<ERRMSG_SIZE>(errorBuffer, "Process with pid=%d has already stopped. Exit code is %d", pid, exitCode);
		reportErrorWithCode(pEnv, 1, errorBuffer);
		return NULL;
	}

	SIZE_T sRead;
	auto_localmem<LPWSTR> pszCmdLine;
	LPCWSTR pEnvStr;	// value of RTL_USER_PROCESS_PARAMETERS.env

#ifdef _WIN64
	LPVOID peb32 = NULL;
	if (!NT_SUCCESS(ZwQueryInformationProcess(hProcess, ProcessWow64Information, &peb32, sizeof(LPVOID), &sRead))) {
		reportError(pEnv, "Failed to ZWQueryInformationProcess(ProcessWow64Information)");
		return NULL;
	}

	if (peb32!=NULL) {
		// target process is a 32bit process running in wow64 environment
		isWoW64 = true;

		// read PEB
		PEB32 ProcPEB;
		if(!ReadProcessMemory(hProcess, peb32, &ProcPEB, sizeof(ProcPEB), &sRead)) {
			reportError(pEnv, "Failed to read PEB32 (mode=WoW64)");
			return NULL;
		}

		// then to INFOBLOCK
		RTL_USER_PROCESS_PARAMETERS32 ProcBlock;
		if(!ReadProcessMemory(hProcess, Ptr32ToPtr64(ProcPEB.dwInfoBlockAddress), &ProcBlock, sizeof(ProcBlock), &sRead)) {
			reportError(pEnv, "Failed to read RT_USER_PROCESS_PARAMETERS32 (mode=WoW64)");
			return NULL;
		}

		// now read command line aguments
		pszCmdLine.allocate(ProcBlock.CommandLine.Length + 2);
		if(!pszCmdLine) {
			reportError(pEnv, "Failed to allocate memory for reading command line (mode=WoW64)");
			return NULL;
		}

		if(!ReadProcessMemory(hProcess, Ptr32ToPtr64(ProcBlock.CommandLine.Buffer), pszCmdLine, ProcBlock.CommandLine.Length, &sRead)) {
			reportError(pEnv, "Failed to read command line arguments (mode=WoW64)");
			return NULL;
		}

		pEnvStr = reinterpret_cast<LPCWSTR>(Ptr32ToPtr64(ProcBlock.env));
	} else {
#else
	// We are running in 32 bit DLL, accept only WoW64 processes
	// There is a risk that somebody starts the 32bit DLL in x64 process, but within WinP JAR it must not happen.
	// TODO: Consider adding defensive logic just in case
	BOOL procIsWow64 = FALSE;
	if (fnIsWow64Process != NULL)
	{
		if (!fnIsWow64Process(hProcess, &procIsWow64))
		{
			reportError(pEnv, "Failed to determine if the process is a 32bit or 64bit executable");
			return NULL;
		}

		if (!procIsWow64) {
			// We are trying to query a 64-bit process from a 32-bit DLL
			sprintf_s<ERRMSG_SIZE>(errorBuffer, "Process with pid=%d is not a 32bit process (or it is not running). Cannot query it from a 32bit library", pid);
			reportErrorWithCode(pEnv, 2, errorBuffer);
			return NULL;
		}
	}

#endif

	// obtain PROCESS_BASIC_INFORMATION
	PROCESS_BASIC_INFORMATION ProcInfo;
	if(!NT_SUCCESS(ZwQueryInformationProcess(hProcess, ProcessBasicInformation, &ProcInfo, sizeof(ProcInfo), &sRead))) {
		reportError(pEnv, "Failed to ZWQueryInformationProcess");
		return NULL;
	}

	// from there to PEB
	PEB ProcPEB;
	if(!ReadProcessMemory(hProcess, ProcInfo.PebBaseAddress, &ProcPEB, sizeof(ProcPEB), &sRead)) {
#ifndef _WIN64
		if (fnIsWow64Process == NULL) {
			// We are unable to determine it, no API call available
			reportError(pEnv, "Failed to read PEB. Probably the process is 64bit, which cannot be read from the 32bit WinP DLL");
		}
		else {
#endif
			reportError(pEnv, "Failed to read PEB");
#ifndef _WIN64
		}
#endif
		return NULL;
	}

	// then to INFOBLOCK
	RTL_USER_PROCESS_PARAMETERS ProcBlock;
	if(!ReadProcessMemory(hProcess, ProcPEB.dwInfoBlockAddress, &ProcBlock, sizeof(ProcBlock), &sRead)) {
		reportError(pEnv, "Failed to read RT_USER_PROCESS_PARAMETERS");
		return NULL;
	}

	// now read command line aguments
	size_t pszCmdLineSize = ProcBlock.CommandLine.Length + 2;
	pszCmdLine.allocate(pszCmdLineSize);
	if(!pszCmdLine) {
		sprintf_s<ERRMSG_SIZE>(errorBuffer, "Failed to allocate memory for reading command line (size=%d)", pszCmdLineSize);
		reportError(pEnv, errorBuffer);
		return NULL;
	}

	if(!ReadProcessMemory(hProcess, ProcBlock.CommandLine.Buffer, pszCmdLine, ProcBlock.CommandLine.Length, &sRead)) {
		// on some processes, I noticed that the value of dwCmdLineAddress doesn't have 0x20000 bias
		// that seem to be there for any other processes. This results in err=299.
		// so retry with this address.
		ProcBlock.CommandLine._Buffer |= 0x20000;
		if(!ReadProcessMemory(hProcess, ProcBlock.CommandLine.Buffer, pszCmdLine, ProcBlock.CommandLine.Length, &sRead)) {
			reportError(pEnv, "Failed to read command line arguments");
			return NULL;
		}
	}

	pEnvStr = ProcBlock.env;

#if	_WIN64
	}	// end of !wow64 code
#endif

	int cmdLineLen = lstrlen(pszCmdLine);
	if (retrieveEnvVars == 0) {
		// No need to retrieve Environment Variables
		jstring packedStr = pEnv->NewString((jchar*)(LPWSTR)pszCmdLine, cmdLineLen);
		return packedStr;
	}

	// Figure out the size of the Process memory block and ensure it is readable
	MEMORY_BASIC_INFORMATION info;
	if(VirtualQueryEx(hProcess, pEnvStr, &info, sizeof(info))==0) {
		sprintf_s<ERRMSG_SIZE>(errorBuffer, "VirtualQueryEx failed, Cannot read process memory information (base=%p, mode=%s)", pEnvStr, isWoW64 ? "WoW64" : "Normal");
		reportError(pEnv, errorBuffer);
		return NULL;
	}
	
	//TODO: set error codes for all the checks below?
	if (info.State != MBI_REGION_STATE::Allocated) {
		sprintf_s<ERRMSG_SIZE>(errorBuffer, "Process memory region has not been allocated yet (base=%p, size=%d, state=0x%X)", pEnvStr, info.RegionSize, info.State);
		reportError(pEnv, errorBuffer);
		//TODO: Technically it is not a fatal failure, the caller should retry the method after the delay
		return NULL;
	}
	
	if (info.Protect == MBI_REGION_PROTECT::NoAccessToCheck || info.Protect & MBI_REGION_PROTECT::NoAccess || info.Protect & MBI_REGION_PROTECT::ExecuteOnly) {
		sprintf_s<ERRMSG_SIZE>(errorBuffer, "No READ access to the process memory region (base=%p, size=%d, protect mode=0x%X, mode=%s)", pEnvStr, info.RegionSize, info.Protect, isWoW64 ? "WoW64" : "Normal");
		reportError(pEnv, errorBuffer);
		return NULL;
	}

	if (pEnvStr < info.BaseAddress) {
		sprintf_s<ERRMSG_SIZE>(errorBuffer, "Process memory header has been read incorrectly. Environment Table points to the address lower than the start address of the region (base=%p, envPointer=%p)", pEnvStr, pEnvStr);
		reportError(pEnv, errorBuffer);
		return NULL;
	}

	size_t bytesBeforeEnv = (char*)pEnvStr - (char*)info.BaseAddress;
	if (bytesBeforeEnv > info.RegionSize) {
		sprintf_s<ERRMSG_SIZE>(errorBuffer, "Process memory header has been read incorrectly. Environment Table points to the address beyond the max address of the region (base=%p, size=%d, envPointer=%p)", pEnvStr, info.RegionSize, pEnvStr);
		reportError(pEnv, errorBuffer);
		return NULL;
	}
	size_t envSize = info.RegionSize - bytesBeforeEnv;
	
	size_t bufferSize = ((size_t)cmdLineLen + 1/*for \0*/) * 2 + envSize;
	auto_localmem<LPWSTR> buf(bufferSize);
	if(!buf) {
		sprintf_s<ERRMSG_SIZE>(errorBuffer, "Environment Variable Table buffer allocation failed (size=%d)", bufferSize);
		reportError(pEnv, errorBuffer);
		return NULL;
	}
	lstrcpy(buf, pszCmdLine);

	// Checking the read size is more likely to result in success than checking the BOOL that is
	// returned by ReadProcessMemory, on newer versions of Windows. That said, neither approach
	// is particularly reliable past Vista. This approach differs from checking the BOOL in that
	// it will succeed for partial reads that read _some_ data, where the other approach fails.
	ReadProcessMemory(hProcess, pEnvStr, buf+cmdLineLen+1, envSize, &sRead);
	if(!sRead) {
		sprintf_s<ERRMSG_SIZE>(errorBuffer, "Failed to read the process memory region with the Environment Variable Table (base=%p, size=%d, mode=%s)", pEnvStr, envSize, isWoW64 ? "WoW64" : "Normal");
		reportError(pEnv, errorBuffer);
		return NULL;
	}

	jstring packedStr = pEnv->NewString((jchar*)(LPWSTR)buf, cmdLineLen + 1 + jsize(sRead) / 2);

	return packedStr;
}

JNIEXPORT jint JNICALL Java_org_jvnet_winp_Native_getProcessId(JNIEnv* pEnv, jclass clazz, jint handle) {
	HANDLE hProcess = (HANDLE)handle;
	PROCESS_BASIC_INFORMATION ProcInfo;
	SIZE_T sRead;
	if(!NT_SUCCESS(ZwQueryInformationProcess(hProcess, ProcessBasicInformation, &ProcInfo, sizeof(ProcInfo), &sRead))) {
		reportError(pEnv,"Failed to ZWQueryInformationProcess");
		return -1;
	}
	
	return (jint)ProcInfo.UniqueProcessId;
/*	ULONG id=0;

	if(!ReadProcessMemory(hProcess, ProcInfo.UniqueProcessId, &id, sizeof(ULONG), &_)) {
		reportError(pEnv,"Failed to read process ID");
		return NULL;
	}

	return id;*/
}
