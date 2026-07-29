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
#include <android/bitmap.h>
#include <android/log.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <sys/select.h>
#include <termios.h>
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
    // M6-Latenz-Statistik: wie viele bereits fertige Frames beim Dequeue verworfen wurden
    // (Drain-to-Latest). drained/calls ≈ mittlere Queue-Altlast in Frames (~33 ms/Frame).
    unsigned long calls;
    unsigned long drained;
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

    // M6 (PERF-Doku 2026-07-03): Drain-to-Latest. V4L2 liefert FIFO — den ÄLTESTEN Puffer.
    // Braucht der Java-Decode ~eine Frameperiode, bleibt die Queue dauerhaft gefüllt und
    // jeder gelieferte Frame ist bis zu n_buffers-1 Frames (~100 ms) alt. Daher: alle schon
    // fertigen Puffer abholen, nur den NEUESTEN behalten, ältere sofort requeuen.
    // Verworfene Frames sind fürs Livebild gewollt (Aktualität schlägt Vollständigkeit).
    for (;;) {
        struct v4l2_buffer next = {0};
        next.type   = V4L2_BUF_TYPE_VIDEO_CAPTURE;
        next.memory = V4L2_MEMORY_MMAP;
        if (xioctl(ctx->fd, VIDIOC_DQBUF, &next) < 0) break; // EAGAIN = Queue leer
        xioctl(ctx->fd, VIDIOC_QBUF, &buf);
        buf = next;
        ctx->drained++;
    }
    ctx->calls++;
    if (ctx->calls % 300 == 0) {
        LOGI("drain-to-latest: %lu verworfen / %lu Frames (Mittel %.2f Frames Altlast)",
             ctx->drained, ctx->calls, (double) ctx->drained / (double) ctx->calls);
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

// ────────────────── RGB→I420 (M3a, H264Encoder) ──────────────────
// Native Farbraum-Konvertierung Bitmap → MediaCodec-YUV-Planes. Ersetzt den Kotlin-Pfad
// getPixels(int[]) + RGB→YUV-Schleife (~25–40 ms bei 720p) durch direkten Zugriff auf die
// Bitmap-Pixel (AndroidBitmap_lockPixels, keine Kopie) + C-Schleife (~3–6 ms, -O3).
// Gleiches Farbmodell wie der Kotlin-Fallback: BT.601 studio swing.
// Kotlin-Klasse: com.uip.oneapp.network.video.H264Encoder

static inline uint8_t clamp_u8(int v) {
    return (uint8_t)(v < 0 ? 0 : (v > 255 ? 255 : v));
}

static inline void rgb_to_yuv_store(
        int r, int g, int b, int x, int y,
        uint8_t* yp, int yRs, int yPs,
        uint8_t* up, int uRs, int uPs,
        uint8_t* vp, int vRs, int vPs) {
    int yy = (((66 * r + 129 * g + 25 * b) + 128) >> 8) + 16;
    yp[y * yRs + x * yPs] = clamp_u8(yy);
    if ((y & 1) == 0 && (x & 1) == 0) {
        int cx = x >> 1, cy = y >> 1;
        int u = (((-38 * r - 74 * g + 112 * b) + 128) >> 8) + 128;
        int v = (((112 * r - 94 * g - 18 * b) + 128) >> 8) + 128;
        up[cy * uRs + cx * uPs] = clamp_u8(u);
        vp[cy * vRs + cx * vPs] = clamp_u8(v);
    }
}

JNIEXPORT jboolean JNICALL
Java_com_uip_oneapp_network_video_H264Encoder_nativeConvertToI420(
        JNIEnv* env, jobject thiz, jobject bitmap,
        jobject yBuf, jint yRs, jint yPs,
        jobject uBuf, jint uRs, jint uPs,
        jobject vBuf, jint vRs, jint vPs,
        jint width, jint height) {
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) return JNI_FALSE;
    if ((int) info.width < width || (int) info.height < height) return JNI_FALSE;
    if (info.format != ANDROID_BITMAP_FORMAT_RGB_565 &&
        info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return JNI_FALSE;

    uint8_t* yp = (uint8_t*) (*env)->GetDirectBufferAddress(env, yBuf);
    uint8_t* up = (uint8_t*) (*env)->GetDirectBufferAddress(env, uBuf);
    uint8_t* vp = (uint8_t*) (*env)->GetDirectBufferAddress(env, vBuf);
    if (!yp || !up || !vp) return JNI_FALSE;

    void* pixels = NULL;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) return JNI_FALSE;

    // Bildraten-Rettung 2026-07-29 (RESULT_CAMERA2_UMBAU Abschnitt 10, Anlauf 2): Die
    // MediaCodec-Input-Planes sind DMA-/gralloc-Speicher — verstreute Einzelbyte-Stores
    // dorthin (wie rgb_to_yuv_store sie erzeugt) kosteten GEMESSEN ~62 ms/Frame statt der
    // dokumentierten 3–6 ms. Deshalb: erst in gecachte Zwischenpuffer (malloc) konvertieren,
    // dann ausschließlich sequentielle memcpy-Bursts in die Planes schreiben.
    const int cw = width >> 1, ch = height >> 1;
    uint8_t* stage = (uint8_t*) malloc((size_t)(width * height) + 2u * (size_t)(cw * ch));
    if (!stage) { AndroidBitmap_unlockPixels(env, bitmap); return JNI_FALSE; }
    uint8_t* yS = stage;
    uint8_t* uS = stage + width * height;
    uint8_t* vS = uS + cw * ch;

    if (info.format == ANDROID_BITMAP_FORMAT_RGB_565) {
        for (int y = 0; y < height; y++) {
            const uint16_t* row = (const uint16_t*) ((const uint8_t*) pixels + y * info.stride);
            uint8_t* yRow = yS + y * width;
            const int cy = y >> 1;
            const int subsample = (y & 1) == 0;
            for (int x = 0; x < width; x++) {
                uint16_t px = row[x];
                // 565 auf 8 Bit expandieren (obere Bits replizieren).
                int r = (px >> 11) & 0x1F; r = (r << 3) | (r >> 2);
                int g = (px >> 5) & 0x3F;  g = (g << 2) | (g >> 4);
                int b = px & 0x1F;         b = (b << 3) | (b >> 2);
                yRow[x] = clamp_u8((((66 * r + 129 * g + 25 * b) + 128) >> 8) + 16);
                if (subsample && (x & 1) == 0) {
                    int cx = x >> 1;
                    uS[cy * cw + cx] = clamp_u8((((-38 * r - 74 * g + 112 * b) + 128) >> 8) + 128);
                    vS[cy * cw + cx] = clamp_u8((((112 * r - 94 * g - 18 * b) + 128) >> 8) + 128);
                }
            }
        }
    } else { // RGBA_8888: Speicherlayout R,G,B,A
        for (int y = 0; y < height; y++) {
            const uint8_t* row = (const uint8_t*) pixels + y * info.stride;
            uint8_t* yRow = yS + y * width;
            const int cy = y >> 1;
            const int subsample = (y & 1) == 0;
            for (int x = 0; x < width; x++) {
                const uint8_t* p = row + x * 4;
                int r = p[0], g = p[1], b = p[2];
                yRow[x] = clamp_u8((((66 * r + 129 * g + 25 * b) + 128) >> 8) + 16);
                if (subsample && (x & 1) == 0) {
                    int cx = x >> 1;
                    uS[cy * cw + cx] = clamp_u8((((-38 * r - 74 * g + 112 * b) + 128) >> 8) + 128);
                    vS[cy * cw + cx] = clamp_u8((((112 * r - 94 * g - 18 * b) + 128) >> 8) + 128);
                }
            }
        }
    }

    // Store-Phase: nur sequentielle Bursts Richtung Codec-Planes.
    if (yPs == 1) {
        for (int y = 0; y < height; y++) memcpy(yp + y * yRs, yS + y * width, (size_t) width);
    } else {
        for (int y = 0; y < height; y++) {
            const uint8_t* s = yS + y * width;
            uint8_t* d = yp + y * yRs;
            for (int x = 0; x < width; x++) d[x * yPs] = s[x];
        }
    }
    if (uPs == 1 && vPs == 1) {           // planar I420
        for (int y = 0; y < ch; y++) {
            memcpy(up + y * uRs, uS + y * cw, (size_t) cw);
            memcpy(vp + y * vRs, vS + y * cw, (size_t) cw);
        }
    } else if (uPs == 2 && vPs == 2 && (up - vp == 1 || vp - up == 1)) {
        // semi-planar NV12/NV21: U/V aliasen denselben Speicher, um 1 Byte versetzt.
        uint8_t* rowBuf = (uint8_t*) malloc((size_t) width);
        if (rowBuf) {
            for (int y = 0; y < ch; y++) {
                const uint8_t* su = uS + y * cw;
                const uint8_t* sv = vS + y * cw;
                // Verschränkte Zeile lokal (gecacht) bauen, dann als EIN Burst über den
                // niedrigeren der beiden Alias-Zeiger schreiben (uBuf/vBuf zeigen in
                // denselben Speicher, um 1 Byte versetzt).
                uint8_t* base = up < vp ? up : vp;
                int uOff = (int) (up - base), vOff = (int) (vp - base);
                for (int x = 0; x < cw; x++) {
                    rowBuf[x * 2 + uOff] = su[x];
                    rowBuf[x * 2 + vOff] = sv[x];
                }
                memcpy(base + y * uRs, rowBuf, (size_t)(cw * 2));
            }
            free(rowBuf);
        } else {
            for (int y = 0; y < ch; y++)
                for (int x = 0; x < cw; x++) {
                    up[y * uRs + x * uPs] = uS[y * cw + x];
                    vp[y * vRs + x * vPs] = vS[y * cw + x];
                }
        }
    } else {                               // exotisches Layout: direkter (langsamer) Pfad
        for (int y = 0; y < ch; y++)
            for (int x = 0; x < cw; x++) {
                up[y * uRs + x * uPs] = uS[y * cw + x];
                vp[y * vRs + x * vPs] = vS[y * cw + x];
            }
    }

    free(stage);
    AndroidBitmap_unlockPixels(env, bitmap);
    return JNI_TRUE;
}

// ────────────── YUV_420_888 → RGB-Bitmap (Camera2FrameSource) ──────────────
// Bildraten-Rettung 2026-07-29 (RESULT_CAMERA2_UMBAU Abschnitt 10): ersetzt die frühere
// Java-Dreifachstufe NV21-Bytekopie → YuvImage.compressToJpeg → BitmapFactory.decode
// (~74 ms/Frame ≙ 13,5 fps) durch eine einzige native BT.601-Konvertierung direkt ins
// gelockte Bitmap. Deckt über row-/pixelStride sowohl semi-planare (NV12, pixelStride 2)
// als auch planare (I420, pixelStride 1) HAL-Layouts ab.

static inline int clamp255(int v) { return v < 0 ? 0 : (v > 255 ? 255 : v); }

JNIEXPORT jboolean JNICALL
Java_com_uip_oneapp_network_internal_Camera2FrameSource_nativeYuvToBitmap(
        JNIEnv* env, jclass clazz,
        jobject yBuf, jint yRs, jint yPs,
        jobject uBuf, jint uRs, jint uPs,
        jobject vBuf, jint vRs, jint vPs,
        jint width, jint height, jobject bitmap) {
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) return JNI_FALSE;
    if ((int) info.width < width || (int) info.height < height) return JNI_FALSE;
    if (info.format != ANDROID_BITMAP_FORMAT_RGB_565) return JNI_FALSE;

    const uint8_t* yp = (const uint8_t*) (*env)->GetDirectBufferAddress(env, yBuf);
    const uint8_t* up = (const uint8_t*) (*env)->GetDirectBufferAddress(env, uBuf);
    const uint8_t* vp = (const uint8_t*) (*env)->GetDirectBufferAddress(env, vBuf);
    if (!yp || !up || !vp) return JNI_FALSE;

    void* pixels = NULL;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) return JNI_FALSE;

    for (int y = 0; y < height; y++) {
        uint16_t* out = (uint16_t*) ((uint8_t*) pixels + y * info.stride);
        const uint8_t* yRow = yp + y * yRs;
        const uint8_t* uRow = up + (y >> 1) * uRs;
        const uint8_t* vRow = vp + (y >> 1) * vRs;
        for (int x = 0; x < width; x++) {
            int Y = yRow[x * yPs];
            int cx = x >> 1;
            int U = uRow[cx * uPs] - 128;
            int V = vRow[cx * vPs] - 128;
            // BT.601 full-range, Festkomma (Faktor 256) — identische Koeffizienten wie
            // die Gegenrichtung in rgb_to_yuv_store.
            int r = clamp255(Y + ((359 * V) >> 8));
            int g = clamp255(Y - ((88 * U + 183 * V) >> 8));
            int b = clamp255(Y + ((454 * U) >> 8));
            out[x] = (uint16_t) (((r & 0xF8) << 8) | ((g & 0xFC) << 3) | (b >> 3));
        }
    }

    AndroidBitmap_unlockPixels(env, bitmap);
    return JNI_TRUE;
}

// ────────────────────── Serieller Port (UART) ──────────────────────
// Steuer-/Telemetriepfad der ONE-Schiebekamera (/dev/ttyS5). Öffnen +
// termios-Konfiguration (9600 8N1, Raw) + blockierendes Lesen/Schreiben
// im App-Prozess — ohne su, ohne FileInputStream.available() (das liefert
// bei TTYs 0 und verhindert das Lesen). Entspricht dem Vorgehen der
// Original-App (com.naz.serial.port.SerialPort via termios).
// Kotlin-Package: com.uip.oneapp.network.internal.OneInternalHardwareService

JNIEXPORT jint JNICALL
Java_com_uip_oneapp_network_internal_OneInternalHardwareService_nativeOpenSerial(
        JNIEnv* env, jobject thiz, jstring jpath, jint baud) {
    const char* path = (*env)->GetStringUTFChars(env, jpath, NULL);
    int fd = open(path, O_RDWR | O_NOCTTY);
    if (fd < 0) {
        LOGE("serial open %s failed: %s", path, strerror(errno));
        (*env)->ReleaseStringUTFChars(env, jpath, path);
        return -1;
    }
    (*env)->ReleaseStringUTFChars(env, jpath, path);

    struct termios tio;
    memset(&tio, 0, sizeof(tio));
    if (tcgetattr(fd, &tio) != 0) {
        LOGE("tcgetattr failed: %s", strerror(errno));
        close(fd);
        return -1;
    }

    speed_t spd = (baud == 115200) ? B115200 : B9600;
    cfsetispeed(&tio, spd);
    cfsetospeed(&tio, spd);
    cfmakeraw(&tio);                 // raw: kein echo/canonical/signal/opost
    tio.c_cflag |= (CLOCAL | CREAD); // lokale Leitung, Empfänger an
    tio.c_cflag &= ~CSTOPB;          // 1 Stopbit
    tio.c_cflag &= ~PARENB;          // keine Parität
    tio.c_cflag &= ~CSIZE;
    tio.c_cflag |= CS8;              // 8 Datenbits
#ifdef CRTSCTS
    tio.c_cflag &= ~CRTSCTS;         // keine HW-Flusssteuerung
#endif
    tio.c_cc[VMIN]  = 0;
    tio.c_cc[VTIME] = 1;             // bis 100 ms auf Daten warten, dann 0 zurück

    tcflush(fd, TCIFLUSH);
    if (tcsetattr(fd, TCSANOW, &tio) != 0) {
        LOGE("tcsetattr failed: %s", strerror(errno));
        close(fd);
        return -1;
    }
    LOGI("serial configured fd=%d baud=%d 8N1 raw", fd, (int)baud);
    return fd;
}

JNIEXPORT jint JNICALL
Java_com_uip_oneapp_network_internal_OneInternalHardwareService_nativeReadSerial(
        JNIEnv* env, jobject thiz, jint fd, jbyteArray jbuf) {
    if (fd < 0 || jbuf == NULL) return -1;
    jsize cap = (*env)->GetArrayLength(env, jbuf);
    if (cap <= 0) return 0;
    jbyte* tmp = (jbyte*) malloc((size_t) cap);
    if (!tmp) return -1;
    ssize_t n = read(fd, tmp, (size_t) cap);
    if (n > 0) {
        (*env)->SetByteArrayRegion(env, jbuf, 0, (jsize) n, tmp);
    }
    free(tmp);
    if (n < 0) {
        if (errno == EAGAIN || errno == EINTR) return 0;
        return -1;
    }
    return (jint) n;
}

JNIEXPORT jint JNICALL
Java_com_uip_oneapp_network_internal_OneInternalHardwareService_nativeWriteSerial(
        JNIEnv* env, jobject thiz, jint fd, jbyteArray jdata, jint len) {
    if (fd < 0 || jdata == NULL || len <= 0) return -1;
    jbyte* data = (*env)->GetByteArrayElements(env, jdata, NULL);
    if (!data) return -1;
    ssize_t w = write(fd, data, (size_t) len);
    if (w >= 0) { /* optional: tcdrain(fd); */ }
    (*env)->ReleaseByteArrayElements(env, jdata, data, JNI_ABORT);
    return (jint) w;
}

JNIEXPORT void JNICALL
Java_com_uip_oneapp_network_internal_OneInternalHardwareService_nativeCloseSerial(
        JNIEnv* env, jobject thiz, jint fd) {
    if (fd >= 0) {
        close(fd);
        LOGI("serial closed fd=%d", fd);
    }
}
