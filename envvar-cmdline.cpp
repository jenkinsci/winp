#include "stdafx.h"
#include "winp.h"
#include "java-interface.h"


struct INFOBLOCK {
	DWORD dwFiller[16];
	WORD wLength;
	WORD wMaxLength;
	union {
		LPCWSTR dwCmdLineAddress;
		DWORD _dwCmdLineAddress;
	};
	LPCWSTR env;
};

struct PEB  {
	DWORD dwFiller[4];
	INFOBLOCK* dwInfoBlockAddress;
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

	HANDLE hProcess = ::OpenProcess(PROCESS_QUERY_INFORMATION|PROCESS_VM_READ,FALSE,pid);
	if(hProcess==NULL) {
		reportError(pEnv,"Failed to open process");
		return NULL;
	}

	// obtain PROCESS_BASIC_INFORMATION
	PROCESS_BASIC_INFORMATION ProcInfo;
	SIZE_T _;
	if(!NT_SUCCESS(ZwQueryInformationProcess(hProcess, ProcessBasicInformation, &ProcInfo, sizeof(ProcInfo), &_))) {
		reportError(pEnv,"Failed to ZWQueryInformationProcess");
		return NULL;
	}

	// from there to PEB
	PEB ProcPEB;
	if(!ReadProcessMemory(hProcess, ProcInfo.PebBaseAddress, &ProcPEB, sizeof(ProcPEB), &_)) {
		reportError(pEnv,"Failed to read PEB");
		return NULL;
	}

	// then to INFOBLOCK
	INFOBLOCK ProcBlock;
	if(!ReadProcessMemory(hProcess, ProcPEB.dwInfoBlockAddress, &ProcBlock, sizeof(ProcBlock), &_)) {
		reportError(pEnv,"Failed to read INFOBLOCK");
		return NULL;
	}

	// now read command line aguments
	LPWSTR pszCmdLine = (LPWSTR)::LocalAlloc(LMEM_FIXED|LMEM_ZEROINIT,ProcBlock.wLength+2);
	if(pszCmdLine==NULL) {
		reportError(pEnv,"Failed to allocate memory for reading command line");
		return NULL;
	}

	if(!ReadProcessMemory(hProcess, ProcBlock.dwCmdLineAddress, pszCmdLine, ProcBlock.wLength, &_)) {
		// on some processes, I noticed that the value of dwCmdLineAddress doesn't have 0x20000 bias
		// that seem to be there for any other processes. This results in err=299.
		// so retry with this address.
		ProcBlock._dwCmdLineAddress |= 0x20000;
		if(!ReadProcessMemory(hProcess, ProcBlock.dwCmdLineAddress, pszCmdLine, ProcBlock.wLength, &_)) {
			reportError(pEnv,"Failed to read command line arguments");
			return NULL;
		}
	}

	// figure out the size of the env var block
	MEMORY_BASIC_INFORMATION info;
	if(::VirtualQueryEx(hProcess, ProcBlock.env, &info, sizeof(info))==0) {
		reportError(pEnv,"VirtualQueryEx failed");
		return NULL;
	}

	int cmdLineLen = lstrlen(pszCmdLine);
	LPWSTR buf = (LPWSTR)LocalAlloc(LMEM_FIXED,(cmdLineLen+1/*for \0*/)*2+info.RegionSize);
	if(buf==NULL) {
		reportError(pEnv,"Buffer allocation failed");
		return NULL;
	}
	lstrcpy(buf,pszCmdLine);

	if(!ReadProcessMemory(hProcess, ProcBlock.env, buf+cmdLineLen+1, info.RegionSize, &_)) {
		reportError(pEnv,"Failed to read environment variable table");
		return NULL;
	}

	CloseHandle(hProcess);

	jstring packedStr = pEnv->NewString((jchar*)buf,cmdLineLen+1+jsize(info.RegionSize)/2);

	LocalFree(pszCmdLine);
	LocalFree(buf);

	return packedStr;
}

JNIEXPORT jint JNICALL Java_org_jvnet_winp_Native_getProcessId(JNIEnv* pEnv, jclass clazz, jint handle) {
	HANDLE hProcess = (HANDLE)handle;
	PROCESS_BASIC_INFORMATION ProcInfo;
	ULONG _;
	if(!NT_SUCCESS(ZwQueryInformationProcess(hProcess, ProcessBasicInformation, &ProcInfo, sizeof(ProcInfo), &_))) {
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
