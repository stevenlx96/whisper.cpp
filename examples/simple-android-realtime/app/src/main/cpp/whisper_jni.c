#include <jni.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <stdlib.h>
#include <string.h>
#include "whisper.h"

#define TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// Asset reading functions
static size_t asset_read(void *ctx, void *output, size_t read_size) {
    return AAsset_read((AAsset *) ctx, output, read_size);
}

static bool asset_is_eof(void *ctx) {
    return AAsset_getRemainingLength64((AAsset *) ctx) <= 0;
}

static void asset_close(void *ctx) {
    AAsset_close((AAsset *) ctx);
}

// Initialize Whisper context from asset
JNIEXPORT jlong JNICALL
Java_com_whisper_realtime_WhisperLib_initContextFromAsset(
        JNIEnv *env, jclass clazz, jobject asset_manager, jstring asset_path) {

    const char *path = (*env)->GetStringUTFChars(env, asset_path, NULL);
    LOGI("Loading model from asset: %s", path);

    AAssetManager *mgr = AAssetManager_fromJava(env, asset_manager);
    AAsset *asset = AAssetManager_open(mgr, path, AASSET_MODE_STREAMING);

    (*env)->ReleaseStringUTFChars(env, asset_path, path);

    if (!asset) {
        LOGE("Failed to open asset");
        return 0;
    }

    struct whisper_model_loader loader = {
        .context = asset,
        .read = asset_read,
        .eof = asset_is_eof,
        .close = asset_close
    };

    struct whisper_context *ctx = whisper_init_with_params(&loader, whisper_context_default_params());

    if (ctx) {
        LOGI("Whisper context initialized successfully");
    } else {
        LOGE("Failed to initialize Whisper context");
    }

    return (jlong) ctx;
}

// Initialize Whisper context from file path
JNIEXPORT jlong JNICALL
Java_com_whisper_realtime_WhisperLib_initContext(
        JNIEnv *env, jclass clazz, jstring model_path) {

    const char *path = (*env)->GetStringUTFChars(env, model_path, NULL);
    LOGI("Loading model from file: %s", path);

    struct whisper_context *ctx = whisper_init_from_file_with_params(
        path,
        whisper_context_default_params()
    );

    (*env)->ReleaseStringUTFChars(env, model_path, path);

    if (ctx) {
        LOGI("Whisper context initialized successfully");
    } else {
        LOGE("Failed to initialize Whisper context");
    }

    return (jlong) ctx;
}

// Free Whisper context
JNIEXPORT void JNICALL
Java_com_whisper_realtime_WhisperLib_freeContext(
        JNIEnv *env, jclass clazz, jlong ctx_ptr) {

    struct whisper_context *ctx = (struct whisper_context *) ctx_ptr;
    if (ctx) {
        whisper_free(ctx);
        LOGI("Whisper context freed");
    }
}

// Transcribe audio data
JNIEXPORT jint JNICALL
Java_com_whisper_realtime_WhisperLib_fullTranscribe(
        JNIEnv *env, jclass clazz, jlong ctx_ptr, jint num_threads, jfloatArray audio_data) {

    struct whisper_context *ctx = (struct whisper_context *) ctx_ptr;
    if (!ctx) {
        LOGE("Invalid context");
        return -1;
    }

    jfloat *audio_arr = (*env)->GetFloatArrayElements(env, audio_data, NULL);
    jsize audio_len = (*env)->GetArrayLength(env, audio_data);

    LOGI("Transcribing %d audio samples with %d threads", audio_len, num_threads);

    // Set up parameters
    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    params.language = "auto";  // Auto-detect language
    params.n_threads = num_threads;
    params.offset_ms = 0;
    params.no_context = true;
    params.single_segment = false;

    // Run transcription
    int result = whisper_full(ctx, params, audio_arr, audio_len);

    (*env)->ReleaseFloatArrayElements(env, audio_data, audio_arr, JNI_ABORT);

    if (result != 0) {
        LOGE("Failed to run whisper_full");
        return -1;
    }

    LOGI("Transcription completed successfully");
    return 0;
}

// Transcribe audio data with context preservation (for streaming)
JNIEXPORT jint JNICALL
Java_com_whisper_realtime_WhisperLib_fullTranscribeWithContext(
        JNIEnv *env, jclass clazz, jlong ctx_ptr, jint num_threads, jfloatArray audio_data, jboolean keep_context) {

    struct whisper_context *ctx = (struct whisper_context *) ctx_ptr;
    if (!ctx) {
        LOGE("Invalid context");
        return -1;
    }

    jfloat *audio_arr = (*env)->GetFloatArrayElements(env, audio_data, NULL);
    jsize audio_len = (*env)->GetArrayLength(env, audio_data);

    LOGI("Transcribing %d audio samples with %d threads (keep_context=%d)", audio_len, num_threads, keep_context);

    // Set up parameters
    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    params.language = "auto";
    params.n_threads = num_threads;
    params.offset_ms = 0;
    params.no_context = true;  // Don't keep context (like stream.cpp default)
    params.single_segment = false;  // Allow multiple segments for faster output

    // Run transcription
    int result = whisper_full(ctx, params, audio_arr, audio_len);

    (*env)->ReleaseFloatArrayElements(env, audio_data, audio_arr, JNI_ABORT);

    if (result != 0) {
        LOGE("Failed to run whisper_full");
        return -1;
    }

    LOGI("Transcription completed successfully");
    return 0;
}

// Get number of text segments
JNIEXPORT jint JNICALL
Java_com_whisper_realtime_WhisperLib_getTextSegmentCount(
        JNIEnv *env, jclass clazz, jlong ctx_ptr) {

    struct whisper_context *ctx = (struct whisper_context *) ctx_ptr;
    if (!ctx) {
        return 0;
    }

    return whisper_full_n_segments(ctx);
}

// Get text segment at index
JNIEXPORT jstring JNICALL
Java_com_whisper_realtime_WhisperLib_getTextSegment(
        JNIEnv *env, jclass clazz, jlong ctx_ptr, jint index) {

    struct whisper_context *ctx = (struct whisper_context *) ctx_ptr;
    if (!ctx) {
        return (*env)->NewStringUTF(env, "");
    }

    const char *text = whisper_full_get_segment_text(ctx, index);
    return (*env)->NewStringUTF(env, text);
}

// Get system info
JNIEXPORT jstring JNICALL
Java_com_whisper_realtime_WhisperLib_getSystemInfo(
        JNIEnv *env, jclass clazz) {

    const char *info = whisper_print_system_info();
    return (*env)->NewStringUTF(env, info);
}
