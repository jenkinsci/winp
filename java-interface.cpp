#include "stdafx.h"
#include "java-interface.h"
#include "winp.h"
#include "auto_handle.h"

JNIEXPORT jboolean JNICALL Java_org_jvnet_winp_Native_kill(JNIEnv* env, jclass clazz, jint pid, jboolean recursive) {
	return KillProcessEx(pid, recursive);
}

JNIEXPORT jint JNICALL Java_org_jvnet_winp_Native_setPriority(JNIEnv* env, jclass clazz, jint pid, jint priority) {
	auto_handle hProcess = OpenProcess(PROCESS_SET_INFORMATION, FALSE, pid);
	if(hProcess && SetPriorityClass(hProcess, priority)) {
		return ERROR_SUCCESS;
	}
	return GetLastError();
}

JNIEXPORT jboolean JNICALL Java_org_jvnet_winp_Native_exitWindowsEx(JNIEnv* env, jclass _, jint uFlags, jint reasonCode) {
	return ExitWindowsEx(uFlags, reasonCode);
}

JNIEXPORT void JNICALL Java_org_jvnet_winp_Native_noop(JNIEnv* env, jclass _) {}
