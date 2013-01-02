#include "stdafx.h"
#include "winp.h"
#include "java-interface.h"
#include "auto_handle.h"

// TODO: error check improvements
JNIEXPORT void JNICALL Java_org_jvnet_winp_Native_enableDebugPrivilege(JNIEnv* env, jclass _) {
	auto_handle hToken;
	if(!OpenProcessToken( GetCurrentProcess(),
		TOKEN_ADJUST_PRIVILEGES | TOKEN_QUERY, &hToken )) {
		reportError(env,"Failed to open the current process");
		return;
	}

	LUID sedebugnameValue;
	if(!LookupPrivilegeValue( NULL, SE_DEBUG_NAME, &sedebugnameValue )) {
		reportError(env,"Failed to look up SE_DEBUG_NAME");
		return;
	}

	TOKEN_PRIVILEGES tkp;
	tkp.PrivilegeCount = 1;
	tkp.Privileges[0].Luid = sedebugnameValue;
	tkp.Privileges[0].Attributes = SE_PRIVILEGE_ENABLED;

	if(!AdjustTokenPrivileges( hToken, FALSE, &tkp, sizeof tkp, NULL, NULL )) {
		reportError(env,"Failed to adjust token privileges");
		return;
	}
}

