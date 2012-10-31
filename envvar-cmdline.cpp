#include "stdafx.h"
#include "winp.h"
#include "java-interface.h"
#include "auto_handle.h"

struct UNICODE_STRING {
	USHORT	Length;
	USHORT	MaxLength;
	PWSTR	Buffer;
};

// http://msdn.microsoft.com/en-us/library/aa813741(VS.85).aspx
struct RTL_USER_PROCESS_PARAMETERS {
	BYTE dwFiller[16];
	PVOID	dwFiller2[10];
	UNICODE_STRING ImagePathName;
	WORD wLength;
	WORD wMaxLength;
	union {
		LPCWSTR dwCmdLineAddress;
		DWORD _dwCmdLineAddress;
	};
	LPCWSTR env;
};

struct PEB  {
#ifdef _WIN64
	BYTE dwFiller[24];
	PVOID dwFiltter2;
#else
	DWORD dwFiller[4];
#endif
	RTL_USER_PROCESS_PARAMETERS* dwInfoBlockAddress;
};

struct PROCESS_BASIC_INFORMATION {
	NTSTATUS ExitStatus;
	PEB* PebBaseAddress;
	PULONG AffinityMask;
	LONG BasePriority;
	PULONG UniqueProcessId;
	PULONG InheritedFromUniqueProcessId;
};

JNIEXPORT jstring JNICALL Java_org_jvnet_winp_Native_getCmdLineAndEnvVars(
	JNIEnv* pEnv, jclass clazz, jint pid) {
	
	// see http://msdn2.microsoft.com/en-us/library/ms674678%28VS.85%29.aspx
	// for kernel string functions

	auto_handle hProcess = OpenProcess(PROCESS_QUERY_INFORMATION | PROCESS_VM_READ, FALSE, pid);
	if(!hProcess) {
		reportError(pEnv, "Failed to open process");
		return NULL;
	}

	// obtain PROCESS_BASIC_INFORMATION
	PROCESS_BASIC_INFORMATION ProcInfo;
	SIZE_T sRead;
	if(!NT_SUCCESS(ZwQueryInformationProcess(hProcess, ProcessBasicInformation, &ProcInfo, sizeof(ProcInfo), &sRead))) {
		reportError(pEnv, "Failed to ZWQueryInformationProcess");
		return NULL;
	}

	// from there to PEB
	PEB ProcPEB;
	if(!ReadProcessMemory(hProcess, ProcInfo.PebBaseAddress, &ProcPEB, sizeof(ProcPEB), &sRead)) {
		reportError(pEnv, "Failed to read PEB");
		return NULL;
	}

	// then to INFOBLOCK
	RTL_USER_PROCESS_PARAMETERS ProcBlock;
	if(!ReadProcessMemory(hProcess, ProcPEB.dwInfoBlockAddress, &ProcBlock, sizeof(ProcBlock), &sRead)) {
		reportError(pEnv, "Failed to read RT_USER_PROCESS_PARAMETERS");
		return NULL;
	}

	// now read command line aguments
	auto_localmem<LPWSTR> pszCmdLine = LocalAlloc(LMEM_FIXED | LMEM_ZEROINIT, ProcBlock.wLength + 2);
	if(!pszCmdLine) {
		reportError(pEnv, "Failed to allocate memory for reading command line");
		return NULL;
	}

	if(!ReadProcessMemory(hProcess, ProcBlock.dwCmdLineAddress, pszCmdLine, ProcBlock.wLength, &sRead)) {
		// on some processes, I noticed that the value of dwCmdLineAddress doesn't have 0x20000 bias
		// that seem to be there for any other processes. This results in err=299.
		// so retry with this address.
		ProcBlock._dwCmdLineAddress |= 0x20000;
		if(!ReadProcessMemory(hProcess, ProcBlock.dwCmdLineAddress, pszCmdLine, ProcBlock.wLength, &sRead)) {
			reportError(pEnv, "Failed to read command line arguments");
			return NULL;
		}
	}

	// figure out the size of the env var block
	MEMORY_BASIC_INFORMATION info;
	if(VirtualQueryEx(hProcess, ProcBlock.env, &info, sizeof(info))==0) {
		reportError(pEnv, "VirtualQueryEx failed");
		return NULL;
	}

	int cmdLineLen = lstrlen(pszCmdLine);
	size_t envSize = info.RegionSize;
	auto_localmem<LPWSTR> buf = LocalAlloc(LMEM_FIXED, (cmdLineLen + 1/*for \0*/) * 2 + envSize);
	if(!buf) {
		reportError(pEnv, "Buffer allocation failed");
		return NULL;
	}
	lstrcpy(buf, pszCmdLine);

	// Checking the read size is more likely to result in success than checking the BOOL that is
	// returned by ReadProcessMemory, on newer versions of Windows. That said, neither approach
	// is particularly reliable past Vista. This approach differs from checking the BOOL in that
	// it will succeed for partial reads that read _some_ data, where the other approach fails.
	ReadProcessMemory(hProcess, ProcBlock.env, buf+cmdLineLen+1, envSize, &sRead);
	if(!sRead) {
		reportError(pEnv, "Failed to read environment variable table");
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
