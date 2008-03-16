#include "stdafx.h"
#include "winp.h"
#include "java-interface.h"


JNIEXPORT jint JNICALL Java_org_jvnet_winp_Native_enumProcesses(JNIEnv * pEnv, jclass _, jintArray result) {
	jint* buf = pEnv->GetIntArrayElements(result,NULL);
	jint len = pEnv->GetArrayLength(result);
	
	DWORD needed=0;
	if(!::EnumProcesses((DWORD*)buf,sizeof(DWORD)*len,&needed)) {
		reportError(pEnv,"Failed to enumerate processes");
		// but return after releasing arrays
	}

	pEnv->ReleaseIntArrayElements(result,buf,0);
	return needed/sizeof(DWORD);
}
