#include <jni.h>
#include <cstring>
#include <cstdint>

#ifndef SPEEXDSP_TYPES_DEFINED
#define SPEEXDSP_TYPES_DEFINED
typedef int16_t  spx_int16_t;
typedef uint16_t spx_uint16_t;
typedef int32_t  spx_int32_t;
typedef uint32_t spx_uint32_t;
#endif

#ifdef __has_include
#  if __has_include(<speex/speexdsp_types.h>)
#    include <speex/speexdsp_types.h>
#  endif
#endif

#include <speex/speex_echo.h>
#include <speex/speex_preprocess.h>

struct AecCtx {
    SpeexEchoState*       echo = nullptr;
    SpeexPreprocessState* pre  = nullptr;
    int frame = 80;
};

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_yiku_yikupayloadSDK_util_SpeexAecProcessor_nativeCreate(
        JNIEnv*, jobject,
        jint sampleRate, jint frameSize, jint filterLength, jint agcFlag)
{
    auto* c = new AecCtx();
    c->frame = frameSize;

    c->echo = speex_echo_state_init(frameSize, filterLength);
    if (!c->echo) { delete c; return 0; }

    int sr = sampleRate;
    speex_echo_ctl(c->echo, SPEEX_ECHO_SET_SAMPLING_RATE, &sr);

    c->pre = speex_preprocess_state_init(frameSize, sr);
    speex_preprocess_ctl(c->pre, SPEEX_PREPROCESS_SET_ECHO_STATE, c->echo);

    int denoise = 1;
    int noiseSuppress = -40;
    int agc = (agcFlag != 0) ? 1 : 0;
    int agcTarget = 16000;
    int agcMaxGain = 10;
    int supp = -55;
    int suppActive = -35;
    int vad = 1, derev = 1;
    float dr0 = 0.45f, dr1 = 0.35f;

    speex_preprocess_ctl(c->pre, SPEEX_PREPROCESS_SET_DENOISE, &denoise);
    speex_preprocess_ctl(c->pre, SPEEX_PREPROCESS_SET_NOISE_SUPPRESS, &noiseSuppress);
    speex_preprocess_ctl(c->pre, SPEEX_PREPROCESS_SET_AGC, &agc);
    speex_preprocess_ctl(c->pre, SPEEX_PREPROCESS_SET_AGC_TARGET, &agcTarget);
    speex_preprocess_ctl(c->pre, SPEEX_PREPROCESS_SET_AGC_MAX_GAIN, &agcMaxGain);
    speex_preprocess_ctl(c->pre, SPEEX_PREPROCESS_SET_VAD, &vad);
    speex_preprocess_ctl(c->pre, SPEEX_PREPROCESS_SET_ECHO_SUPPRESS, &supp);
    speex_preprocess_ctl(c->pre, SPEEX_PREPROCESS_SET_ECHO_SUPPRESS_ACTIVE, &suppActive);
#ifdef SPEEX_PREPROCESS_SET_DEREVERB
    speex_preprocess_ctl(c->pre, SPEEX_PREPROCESS_SET_DEREVERB, &derev);
    speex_preprocess_ctl(c->pre, SPEEX_PREPROCESS_SET_DEREVERB_LEVEL, &dr0);
    speex_preprocess_ctl(c->pre, SPEEX_PREPROCESS_SET_DEREVERB_DECAY, &dr1);
#endif
    return reinterpret_cast<jlong>(c);
}

JNIEXPORT void JNICALL
Java_com_yiku_yikupayloadSDK_util_SpeexAecProcessor_nativePlayback(
        JNIEnv* env, jobject, jlong handle, jshortArray play_, jint)
{
    auto* c = reinterpret_cast<AecCtx*>(handle);
    if (!c || !play_) return;
    jshort* p = env->GetShortArrayElements(play_, nullptr);
    if (!p) return;
    speex_echo_playback(c->echo, p);
    env->ReleaseShortArrayElements(play_, p, JNI_ABORT);
}

JNIEXPORT void JNICALL
Java_com_yiku_yikupayloadSDK_util_SpeexAecProcessor_nativeBufferFarend(
        JNIEnv*, jobject, jlong, jshortArray, jint) { /* 留空 */ }

JNIEXPORT jshortArray JNICALL
Java_com_yiku_yikupayloadSDK_util_SpeexAecProcessor_nativeProcess(
        JNIEnv* env, jobject, jlong handle,
        jshortArray near_, jshortArray, jint)
{
    auto* c = reinterpret_cast<AecCtx*>(handle);
    if (!c || !near_) return nullptr;

    jsize len = env->GetArrayLength(near_);
    jshort* n = env->GetShortArrayElements(near_, nullptr);
    jshortArray out = env->NewShortArray(len);
    jshort* o = env->GetShortArrayElements(out, nullptr);

    speex_echo_capture(c->echo, n, o);
    speex_preprocess_run(c->pre, o);

    env->ReleaseShortArrayElements(near_, n, JNI_ABORT);
    env->ReleaseShortArrayElements(out, o, 0);
    return out;
}

JNIEXPORT void JNICALL
Java_com_yiku_yikupayloadSDK_util_SpeexAecProcessor_nativeRelease(
        JNIEnv*, jobject, jlong handle)
{
    auto* c = reinterpret_cast<AecCtx*>(handle);
    if (!c) return;
    if (c->pre)  speex_preprocess_state_destroy(c->pre);
    if (c->echo) speex_echo_state_destroy(c->echo);
    delete c;
}

} // extern "C"