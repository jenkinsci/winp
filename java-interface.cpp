#include "stdafx.h"
#include "java-interface.h"
#include "winp.h"

JNIEXPORT jboolean JNICALL Java_org_jvnet_winp_Native_kill(JNIEnv* env, jclass clazz, jint pid, jboolean recursive) {
	return KillProcessEx(pid,recursive);
}
