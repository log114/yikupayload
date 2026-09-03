#include <jni.h>
#include <stdint.h>
#include <string.h>
#include <stdlib.h>
#include "aecm/echo_control_mobile.h"
#include "aecm/aecm_defines.h"

// 每个实例保存一个 AECM 句柄
struct AecmInstance {
    void* aecmInst;
    int sampleRate;
    int frameLen;  // 8kHz 下 = 80
};

JNIEXPORT jlong JNICALL
Java_com_yiku_yikupayloadSDK_util_AecmProcessor_nativeCreate(JNIEnv* env, jobject thiz) {
    struct AecmInstance* inst = malloc(sizeof(struct AecmInstance));
    if (!inst) {
        return 0;  // Java 层判 handle == 0 抛异常
    }
    inst->aecmInst = WebRtcAecm_Create();
    if (!inst->aecmInst) {
        free(inst);
        return 0;
    }
    return (jlong)(intptr_t)inst;
}

JNIEXPORT jint JNICALL
Java_com_yiku_yikupayloadSDK_util_AecmProcessor_nativeInit(JNIEnv* env, jobject thiz,
                                      jlong handle, jint sampleRate) {
    struct AecmInstance* inst = (struct AecmInstance*)(intptr_t)handle;
    int ret = WebRtcAecm_Init(inst->aecmInst, sampleRate);
    inst->sampleRate = sampleRate;
    inst->frameLen = sampleRate / 100;  // 10ms 帧长

    // 配置：echoMode=4 最激进，cngMode=1 开启舒适噪声
    AecmConfig config;
    config.echoMode = 4;
    config.cngMode  = AecmTrue;
    WebRtcAecm_set_config(inst->aecmInst, config);
    return ret;
}

JNIEXPORT void JNICALL
Java_com_yiku_yikupayloadSDK_util_AecmProcessor_nativeBufferFarend(JNIEnv* env, jobject thiz,
                                              jlong handle, jshortArray far,
                                              jint samples) {
    struct AecmInstance* inst = (struct AecmInstance*)(intptr_t)handle;
    jshort* far_ptr = (*env)->GetShortArrayElements(env, far, NULL);
    WebRtcAecm_BufferFarend(inst->aecmInst, far_ptr, samples);
    (*env)->ReleaseShortArrayElements(env, far, far_ptr, JNI_ABORT);
}

JNIEXPORT void JNICALL
Java_com_yiku_yikupayloadSDK_util_AecmProcessor_nativeProcess(JNIEnv* env, jobject thiz,
                                         jlong handle,
                                         jshortArray near,
                                         jshortArray out,
                                         jint samples,
                                         jint msInSndCardBuf) {
    struct AecmInstance* inst = (struct AecmInstance*)(intptr_t)handle;
    jshort* near_ptr = (*env)->GetShortArrayElements(env, near, NULL);
    jshort* out_ptr  = (*env)->GetShortArrayElements(env, out, NULL);

    WebRtcAecm_Process(inst->aecmInst, near_ptr, NULL,
                       out_ptr, samples, msInSndCardBuf);

    (*env)->ReleaseShortArrayElements(env, near, near_ptr, JNI_ABORT);
    (*env)->ReleaseShortArrayElements(env, out, out_ptr, 0);
}

JNIEXPORT void JNICALL
Java_com_yiku_yikupayloadSDK_util_AecmProcessor_nativeFree(JNIEnv* env, jobject thiz,
                                      jlong handle) {
    struct AecmInstance* inst = (struct AecmInstance*)(intptr_t)handle;
    WebRtcAecm_Free(inst->aecmInst);
    free(inst);
}