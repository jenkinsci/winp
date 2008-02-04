#include "stdafx.h"
#include "java-interface.h"
#include "kill-tree.h"

JNIEXPORT jboolean JNICALL Java_org_jvnet_winp_Native_kill(JNIEnv* env, jclass clazz, jint pid, jboolean recursive) {
	return KillProcessEx(pid,recursive);
}
