#include "stdafx.h"
#include "java-interface.h"

// see http://msdn2.microsoft.com/en-us/library/aa489609.aspx
#define NT_SUCCESS(Status) ((NTSTATUS)(Status) >= 0)

enum PROCESSINFOCLASS {
	// see http://msdn2.microsoft.com/en-us/library/ms687420(VS.85).aspx
	ProcessBasicInformation = 0
};

typedef NTSTATUS (NTAPI *ZWQueryInformationProcess)(HANDLE hProcess, PROCESSINFOCLASS infoType, /*out*/ PVOID pBuf, /*sizeof pBuf*/ ULONG lenBuf, PULONG returnLength); 

struct INFOBLOCK {
	DWORD dwFiller[16];
	WORD wLength;
	WORD wMaxLength;
	LPCWSTR dwCmdLineAddress;
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
	if(hProcess==NULL)
		return NULL;
	
	HMODULE hModule = GetModuleHandle(_T("ntdll"));
	ZWQueryInformationProcess queryInformationProcess = (ZWQueryInformationProcess)GetProcAddress(hModule, "ZwQueryInformationProcess");
	if(queryInformationProcess==NULL)
		return NULL;

	// obtain PROCESS_BASIC_INFORMATION
	PROCESS_BASIC_INFORMATION ProcInfo;
	ULONG _;
	if(!NT_SUCCESS(queryInformationProcess(hProcess, ProcessBasicInformation, &ProcInfo, sizeof(ProcInfo), &_)))
		return NULL;

	// from there to PEB
	PEB ProcPEB;
	if(!ReadProcessMemory(hProcess, ProcInfo.PebBaseAddress, &ProcPEB, sizeof(ProcPEB), &_))
		return NULL;

	// then to INFOBLOCK
	INFOBLOCK ProcBlock;
	if(!ReadProcessMemory(hProcess, ProcPEB.dwInfoBlockAddress, &ProcBlock, sizeof(ProcBlock), &_))
		return NULL;

	// now read command line aguments
	LPWSTR pszCmdLine = (LPWSTR)::LocalAlloc(LMEM_FIXED,ProcBlock.wMaxLength);
	if(!ReadProcessMemory(hProcess, ProcBlock.dwCmdLineAddress, pszCmdLine, ProcBlock.wMaxLength, &_))
		return NULL;

	// figure out the size of the env var block
	MEMORY_BASIC_INFORMATION info;
	::VirtualQueryEx(hProcess, ProcBlock.env, &info, sizeof(info));


	int cmdLineLen = lstrlen(pszCmdLine);
	LPWSTR buf = (LPWSTR)LocalAlloc(LMEM_FIXED,(cmdLineLen+1/*for \0*/)*2+info.RegionSize);
	lstrcpy(buf,pszCmdLine);

	if(!ReadProcessMemory(hProcess, ProcBlock.env, buf+cmdLineLen+1, info.RegionSize, &_))
		return NULL;

	CloseHandle(hProcess);

	jstring packedStr = pEnv->NewString((jchar*)buf,cmdLineLen+1+(info.RegionSize)/2);

	LocalFree(pszCmdLine);
	LocalFree(buf);

	return packedStr;
}