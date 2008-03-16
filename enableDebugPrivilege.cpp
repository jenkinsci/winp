#include "stdafx.h"
#include "java-interface.h"

// TODO: error check improvements
JNIEXPORT void JNICALL Java_org_jvnet_winp_Native_enableDebugPrivilege(JNIEnv* env, jclass _) {
	HANDLE hToken = NULL;
	if(!OpenProcessToken( GetCurrentProcess(),
		TOKEN_ADJUST_PRIVILEGES | TOKEN_QUERY, &hToken ))
		return ;

	LUID sedebugnameValue;
	if(!LookupPrivilegeValue( NULL, SE_DEBUG_NAME, &sedebugnameValue ))
		return;

	TOKEN_PRIVILEGES tkp;
	tkp.PrivilegeCount = 1;
	tkp.Privileges[0].Luid = sedebugnameValue;
	tkp.Privileges[0].Attributes = SE_PRIVILEGE_ENABLED;

	if(!AdjustTokenPrivileges( hToken, FALSE, &tkp, sizeof tkp, NULL, NULL ))
		return;

	CloseHandle( hToken );
}

