#include <jni.h>
#include <termios.h>
#include <fcntl.h>
#include <unistd.h>
#include <errno.h>
#include <string.h>
#include <android/log.h>

#define TAG "SerialPortNative"

/*
 * Opens a serial TTY with O_RDWR | O_NOCTTY, then calls cfmakeraw() + tcsetattr()
 * on the same fd. This avoids the stty-then-FileOutputStream race where the tty
 * driver resets termios to defaults between the stty close and the Java open.
 *
 * VMIN=0 VTIME=0: read() returns immediately with whatever bytes are buffered
 * (or 0 if none) — compatible with the available()+read() polling pattern in rxLoop.
 *
 * Returns the open fd on success, -1 on failure.
 */
JNIEXPORT jint JNICALL
Java_com_uip_oneapp_network_internal_SerialPortNative_openSerial(
    JNIEnv *env, jclass clazz, jstring pathStr, jint baudRate)
{
    const char *path = (*env)->GetStringUTFChars(env, pathStr, NULL);
    if (!path) return -1;

    int fd = open(path, O_RDWR | O_NOCTTY | O_NONBLOCK);
    if (fd < 0) {
        __android_log_print(ANDROID_LOG_ERROR, TAG,
            "open(%s) failed: %s", path, strerror(errno));
        (*env)->ReleaseStringUTFChars(env, pathStr, path);
        return -1;
    }

    /* Switch back to blocking mode after O_NONBLOCK was used only to avoid
     * blocking on open() itself for some tty types. */
    int flags = fcntl(fd, F_GETFL, 0);
    fcntl(fd, F_SETFL, flags & ~O_NONBLOCK);

    struct termios cfg;
    if (tcgetattr(fd, &cfg) < 0) {
        __android_log_print(ANDROID_LOG_ERROR, TAG,
            "tcgetattr(%s) failed: %s", path, strerror(errno));
        close(fd);
        (*env)->ReleaseStringUTFChars(env, pathStr, path);
        return -1;
    }

    cfmakeraw(&cfg);
    cfg.c_cc[VMIN]  = 0;   /* non-blocking read at termios level */
    cfg.c_cc[VTIME] = 0;

    speed_t speed;
    switch (baudRate) {
        case 1200:   speed = B1200;   break;
        case 2400:   speed = B2400;   break;
        case 4800:   speed = B4800;   break;
        case 9600:   speed = B9600;   break;
        case 19200:  speed = B19200;  break;
        case 38400:  speed = B38400;  break;
        case 57600:  speed = B57600;  break;
        case 115200: speed = B115200; break;
        default:
            __android_log_print(ANDROID_LOG_WARN, TAG,
                "unknown baud %d, defaulting to 9600", baudRate);
            speed = B9600;
    }
    cfsetispeed(&cfg, speed);
    cfsetospeed(&cfg, speed);

    if (tcsetattr(fd, TCSANOW, &cfg) < 0) {
        __android_log_print(ANDROID_LOG_ERROR, TAG,
            "tcsetattr(%s) failed: %s", path, strerror(errno));
        close(fd);
        (*env)->ReleaseStringUTFChars(env, pathStr, path);
        return -1;
    }

    __android_log_print(ANDROID_LOG_INFO, TAG,
        "openSerial(%s, %d) -> fd=%d OK", path, baudRate, fd);
    (*env)->ReleaseStringUTFChars(env, pathStr, path);
    return fd;
}
