#include "stdafx.h"

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


int __cdecl _tmain(int argc, _TCHAR* argv[])
{
	HMODULE hModule = GetModuleHandle(_T("ntdll"));
	
	ZWQueryInformationProcess queryInformationProcess = (ZWQueryInformationProcess)GetProcAddress(hModule, "ZwQueryInformationProcess");
	if(queryInformationProcess==NULL) 
		exit(1);

	HANDLE hProcess = GetCurrentProcess();
	// HANDLE hProcess = OpenProcess(PROCESS_QUERY_INFORMATION | PROCESS_VM_READ, FALSE, 2012);

	// obtain PROCESS_BASIC_INFORMATION
	PROCESS_BASIC_INFORMATION ProcInfo;
	ULONG _;
	if(!NT_SUCCESS(queryInformationProcess(hProcess, ProcessBasicInformation, &ProcInfo, sizeof(ProcInfo), &_)))
		exit(1);

	// from there to PEB
	PEB ProcPEB;
	if(!ReadProcessMemory(hProcess, ProcInfo.PebBaseAddress, &ProcPEB, sizeof(ProcPEB), &_))
		exit(1);

	// then to INFOBLOCK
	INFOBLOCK ProcBlock;
	if(!ReadProcessMemory(hProcess, ProcPEB.dwInfoBlockAddress, &ProcBlock, sizeof(ProcBlock), &_))
		exit(1);

	// now read command line aguments
	LPWSTR pszCmdLine = (LPWSTR) new BYTE[ProcBlock.wMaxLength];
	if(!ReadProcessMemory(hProcess, ProcBlock.dwCmdLineAddress, pszCmdLine, ProcBlock.wMaxLength, &_))
		exit(1);

	// figure out the size of the env var block
	MEMORY_BASIC_INFORMATION info;
	::VirtualQueryEx(hProcess, ProcBlock.env, &info, sizeof(info));
	LPWSTR pszEnvVars = (LPWSTR) new BYTE[info.RegionSize];

	if(!ReadProcessMemory(hProcess, ProcBlock.env, pszEnvVars, ProcBlock.wMaxLength, &_))
		exit(1);

	CloseHandle(hProcess);

	// dump for test
	_putws(pszCmdLine);
	_putws(pszEnvVars);

 
	delete pszCmdLine;
	delete pszEnvVars;

	return 0;
}