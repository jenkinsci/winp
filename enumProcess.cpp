#include "stdafx.h"
#include "java-interface.h"


JNIEXPORT jint JNICALL Java_org_jvnet_winp_Native_enumProcesses(JNIEnv * pEnv, jclass _, jintArray result) {
	jint* buf = pEnv->GetIntArrayElements(result,NULL);
	jint len = pEnv->GetArrayLength(result);
	
	DWORD needed=0;
	if(!::EnumProcesses((DWORD*)buf,sizeof(DWORD)*len,&needed))
		needed = 0;

	pEnv->ReleaseIntArrayElements(result,buf,0);
	return needed/sizeof(DWORD);
}
