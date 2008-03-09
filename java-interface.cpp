#include "stdafx.h"
#include "java-interface.h"
#include "winp.h"

JNIEXPORT jboolean JNICALL Java_org_jvnet_winp_Native_kill(JNIEnv* env, jclass clazz, jint pid, jboolean recursive) {
	return KillProcessEx(pid,recursive);
}

JNIEXPORT jint JNICALL Java_org_jvnet_winp_Native_getProcessId(JNIEnv* env, jclass clazz, jint handle) {
	return ::GetProcessId((HANDLE)handle);
}

JNIEXPORT jint JNICALL Java_org_jvnet_winp_Native_setPriority(JNIEnv* env, jclass clazz, jint pid, jint priority) {
	HANDLE hProcess = OpenProcess(PROCESS_SET_INFORMATION,FALSE,pid);
	if(hProcess==NULL || hProcess==INVALID_HANDLE_VALUE)
		return GetLastError(); 
	if(!SetPriorityClass(hProcess,priority))
		return GetLastError();
	CloseHandle(hProcess);
	return 0;
}

JNIEXPORT jboolean JNICALL Java_org_jvnet_winp_Native_exitWindowsEx(JNIEnv* env, jclass _, jint uFlags, jint reasonCode) {
	return ::ExitWindowsEx(uFlags,reasonCode);
}
