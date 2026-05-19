// V4L2-Direct-Bridge für die NSP3CT-Schiebekamera ONE (drainq.one-Version).
//
// Identisch zum Smoke-Test (C:\Projekte\one-smoketest), aber JNI-Symbol-Namen
// zeigen auf com.uip.oneapp.network.internal.V4L2Camera (statt
// de.uip.nsp3ct.onesmoketest.hardware.V4L2Camera).
//
// Öffnet /dev/video0 als V4L2-Device, setzt MJPEG-Format mit 4 mmap-Buffern und
// dequeued einzelne JPEG-Frames in Java-byte[]s. Der MJPEG-Decode (JPEG→Bitmap)
// passiert auf der Java-Seite mit BitmapFactory.
//
// Voraussetzung: chmod 666 /dev/video0 muss vorher gesetzt sein (siehe Phase P7
// Permission-Strategie).
//
// Hardware: MACROSILICON MS2109 (HDMI-zu-USB-Capture, UVC-Class) — Kernel-Treiber
// uvcvideo legt /dev/video0 automatisch an.

#include <jni.h>
#include <android/log.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <linux/videodev2.h>

#define TAG "V4L2Bridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

#define MAX_BUFFERS 4

typedef struct {
    int fd;
    int n_buffers;
    struct {
        void*  start;
        size_t length;
    } buffers[MAX_BUFFERS];
} v4l2_ctx;

static int xioctl(int fd, unsigned long req, void* arg) {
    int r;
    do { r = ioctl(fd, req, arg); } while (r == -1 && errno == EINTR);
    return r;
}

// ────────────────────────── JNI-Exports ──────────────────────────
// Kotlin-Package: com.uip.oneapp.network.internal.V4L2Camera

JNIEXPORT jlong JNICALL
Java_com_uip_oneapp_network_internal_V4L2Camera_nativeOpen(
        JNIEnv* env, jobject thiz, jstring jpath) {
    const char* path = (*env)->GetStringUTFChars(env, jpath, NULL);
    int fd = open(path, O_RDWR | O_NONBLOCK, 0);
    if (fd < 0) {
        LOGE("open %s failed: %s", path, strerror(errno));
        (*env)->ReleaseStringUTFChars(env, jpath, path);
        return 0;
    }
    LOGI("Opened %s -> fd=%d", path, fd);
    (*env)->ReleaseStringUTFChars(env, jpath, path);

    v4l2_ctx* ctx = (v4l2_ctx*) calloc(1, sizeof(v4l2_ctx));
    if (!ctx) { close(fd); return 0; }
    ctx->fd = fd;
    ctx->n_buffers = 0;
    return (jlong)(uintptr_t) ctx;
}

JNIEXPORT jboolean JNICALL
Java_com_uip_oneapp_network_internal_V4L2Camera_nativeSetupMjpeg(
        JNIEnv* env, jobject thiz, jlong jctx, jint width, jint height) {
    v4l2_ctx* ctx = (v4l2_ctx*)(uintptr_t) jctx;
    if (!ctx || ctx->fd < 0) return JNI_FALSE;

    struct v4l2_format fmt = {0};
    fmt.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    fmt.fmt.pix.width       = (uint32_t) width;
    fmt.fmt.pix.height      = (uint32_t) height;
    fmt.fmt.pix.pixelformat = V4L2_PIX_FMT_MJPEG;
    fmt.fmt.pix.field       = V4L2_FIELD_NONE;
    if (xioctl(ctx->fd, VIDIOC_S_FMT, &fmt) < 0) {
        LOGE("VIDIOC_S_FMT MJPEG %dx%d failed: %s", width, height, strerror(errno));
        return JNI_FALSE;
    }
    LOGI("VIDIOC_S_FMT MJPEG %dx%d OK (driver accepted %dx%d)",
         width, height, fmt.fmt.pix.width, fmt.fmt.pix.height);

    struct v4l2_requestbuffers req = {0};
    req.count  = MAX_BUFFERS;
    req.type   = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    req.memory = V4L2_MEMORY_MMAP;
    if (xioctl(ctx->fd, VIDIOC_REQBUFS, &req) < 0) {
        LOGE("VIDIOC_REQBUFS failed: %s", strerror(errno));
        return JNI_FALSE;
    }
    if (req.count < 2) {
        LOGE("Insufficient buffers: %d", req.count);
        return JNI_FALSE;
    }
    ctx->n_buffers = req.count > MAX_BUFFERS ? MAX_BUFFERS : req.count;

    for (int i = 0; i < ctx->n_buffers; i++) {
        struct v4l2_buffer buf = {0};
        buf.type   = V4L2_BUF_TYPE_VIDEO_CAPTURE;
        buf.memory = V4L2_MEMORY_MMAP;
        buf.index  = i;
        if (xioctl(ctx->fd, VIDIOC_QUERYBUF, &buf) < 0) {
            LOGE("VIDIOC_QUERYBUF i=%d failed", i);
            return JNI_FALSE;
        }
        ctx->buffers[i].length = buf.length;
        ctx->buffers[i].start = mmap(NULL, buf.length,
                                     PROT_READ | PROT_WRITE, MAP_SHARED,
                                     ctx->fd, buf.m.offset);
        if (ctx->buffers[i].start == MAP_FAILED) {
            LOGE("mmap i=%d failed", i);
            return JNI_FALSE;
        }
    }

    for (int i = 0; i < ctx->n_buffers; i++) {
        struct v4l2_buffer buf = {0};
        buf.type   = V4L2_BUF_TYPE_VIDEO_CAPTURE;
        buf.memory = V4L2_MEMORY_MMAP;
        buf.index  = i;
        if (xioctl(ctx->fd, VIDIOC_QBUF, &buf) < 0) {
            LOGE("VIDIOC_QBUF i=%d failed", i);
            return JNI_FALSE;
        }
    }

    enum v4l2_buf_type type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    if (xioctl(ctx->fd, VIDIOC_STREAMON, &type) < 0) {
        LOGE("VIDIOC_STREAMON failed: %s", strerror(errno));
        return JNI_FALSE;
    }
    LOGI("Stream started with %d buffers", ctx->n_buffers);
    return JNI_TRUE;
}

JNIEXPORT jbyteArray JNICALL
Java_com_uip_oneapp_network_internal_V4L2Camera_nativeDequeueFrame(
        JNIEnv* env, jobject thiz, jlong jctx) {
    v4l2_ctx* ctx = (v4l2_ctx*)(uintptr_t) jctx;
    if (!ctx || ctx->fd < 0) return NULL;

    fd_set fds; FD_ZERO(&fds); FD_SET(ctx->fd, &fds);
    struct timeval tv = { .tv_sec = 0, .tv_usec = 200 * 1000 };
    int r = select(ctx->fd + 1, &fds, NULL, NULL, &tv);
    if (r <= 0) return NULL;

    struct v4l2_buffer buf = {0};
    buf.type   = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    buf.memory = V4L2_MEMORY_MMAP;
    if (xioctl(ctx->fd, VIDIOC_DQBUF, &buf) < 0) {
        if (errno != EAGAIN) LOGW("VIDIOC_DQBUF failed: %s", strerror(errno));
        return NULL;
    }

    jsize len = (jsize) buf.bytesused;
    jbyteArray arr = (*env)->NewByteArray(env, len);
    if (arr != NULL) {
        (*env)->SetByteArrayRegion(env, arr, 0, len,
                                   (const jbyte*) ctx->buffers[buf.index].start);
    }

    xioctl(ctx->fd, VIDIOC_QBUF, &buf);
    return arr;
}

JNIEXPORT void JNICALL
Java_com_uip_oneapp_network_internal_V4L2Camera_nativeClose(
        JNIEnv* env, jobject thiz, jlong jctx) {
    v4l2_ctx* ctx = (v4l2_ctx*)(uintptr_t) jctx;
    if (!ctx) return;
    if (ctx->fd >= 0) {
        enum v4l2_buf_type type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
        ioctl(ctx->fd, VIDIOC_STREAMOFF, &type);
        for (int i = 0; i < ctx->n_buffers; i++) {
            if (ctx->buffers[i].start && ctx->buffers[i].start != MAP_FAILED) {
                munmap(ctx->buffers[i].start, ctx->buffers[i].length);
            }
        }
        close(ctx->fd);
        LOGI("Closed fd=%d", ctx->fd);
    }
    free(ctx);
}
