/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circle Play render bridge — the Android-facing half of the display path. The
 * bundled X server (circle-xserver, vendored) renders the game into a shared
 * framebuffer and reads input from a unix socket; this native lib maps that
 * framebuffer and blits each new frame onto the Android Surface, and forwards
 * touch/key input back to the server. JNI surface for SurfaceBridge.
 *
 * Shared-framebuffer protocol (the X server writes, we read):
 *   [CircleFbHeader][pixels RGBA_8888, stride bytes/row]
 * Input protocol (we write, the X server reads): CircleInput datagrams.
 */
#include <jni.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <android/log.h>

#include <pthread.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <cstring>
#include <cstdint>
#include <atomic>

#define TAG "CirclePlayRender"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {

constexpr uint32_t CFB_MAGIC = 0x31424643u; // "CFB1"

struct CircleFbHeader {
    uint32_t magic;
    uint32_t width;
    uint32_t height;
    uint32_t stride;     // bytes per row of the source framebuffer
    uint32_t format;     // 1 = RGBA_8888
    uint32_t pad;
    uint64_t frame_seq;  // bumped by the X server on every new frame
};

struct CircleInput {
    uint32_t type;       // 1 = motion, 2 = button, 3 = key
    int32_t a, b, c;
};

ANativeWindow* g_window = nullptr;
std::atomic<bool> g_running{false};
pthread_t g_thread;
pthread_mutex_t g_win_lock = PTHREAD_MUTEX_INITIALIZER;

int g_input_fd = -1;
char g_fb_path[256] = "/data/local/tmp/circle_fb";
char g_input_path[256] = "/data/local/tmp/circle_input";

void blit(const CircleFbHeader* h, const uint8_t* src) {
    pthread_mutex_lock(&g_win_lock);
    ANativeWindow* w = g_window;
    if (w) ANativeWindow_acquire(w);
    pthread_mutex_unlock(&g_win_lock);
    if (!w) return;

    ANativeWindow_setBuffersGeometry(w, h->width, h->height, WINDOW_FORMAT_RGBA_8888);
    ANativeWindow_Buffer buf;
    if (ANativeWindow_lock(w, &buf, nullptr) == 0) {
        uint8_t* dst = static_cast<uint8_t*>(buf.bits);
        uint32_t rows = h->height < static_cast<uint32_t>(buf.height)
                ? h->height : static_cast<uint32_t>(buf.height);
        uint32_t cols = h->width < static_cast<uint32_t>(buf.width)
                ? h->width : static_cast<uint32_t>(buf.width);
        size_t dst_stride = static_cast<size_t>(buf.stride) * 4;
        for (uint32_t y = 0; y < rows; ++y) {
            memcpy(dst + y * dst_stride, src + static_cast<size_t>(y) * h->stride,
                   static_cast<size_t>(cols) * 4);
        }
        ANativeWindow_unlockAndPost(w);
    }
    ANativeWindow_release(w);
}

void* render_loop(void*) {
    int fd = -1;
    void* map = nullptr;
    size_t map_len = 0;
    uint64_t last = 0;

    while (g_running.load()) {
        if (fd < 0) {
            fd = open(g_fb_path, O_RDONLY);
            if (fd < 0) { usleep(50000); continue; }
        }
        struct stat st;
        if (fstat(fd, &st) != 0 || st.st_size < static_cast<off_t>(sizeof(CircleFbHeader))) {
            usleep(50000); continue;
        }
        if (!map || map_len != static_cast<size_t>(st.st_size)) {
            if (map) munmap(map, map_len);
            map_len = static_cast<size_t>(st.st_size);
            map = mmap(nullptr, map_len, PROT_READ, MAP_SHARED, fd, 0);
            if (map == MAP_FAILED) { map = nullptr; usleep(50000); continue; }
        }
        auto* h = static_cast<CircleFbHeader*>(map);
        if (h->magic != CFB_MAGIC) { usleep(20000); continue; }
        if (h->frame_seq == last) { usleep(4000); continue; } // ~250 fps poll cap
        last = h->frame_seq;
        blit(h, static_cast<const uint8_t*>(map) + sizeof(CircleFbHeader));
    }

    if (map && map != MAP_FAILED) munmap(map, map_len);
    if (fd >= 0) close(fd);
    return nullptr;
}

void send_input(uint32_t type, int32_t a, int32_t b, int32_t c) {
    if (g_input_fd < 0) {
        g_input_fd = socket(AF_UNIX, SOCK_DGRAM, 0);
        if (g_input_fd < 0) return;
        sockaddr_un addr{};
        addr.sun_family = AF_UNIX;
        strncpy(addr.sun_path, g_input_path, sizeof(addr.sun_path) - 1);
        if (connect(g_input_fd, reinterpret_cast<sockaddr*>(&addr), sizeof(addr)) != 0) {
            close(g_input_fd); g_input_fd = -1; return;
        }
    }
    CircleInput pkt{type, a, b, c};
    send(g_input_fd, &pkt, sizeof(pkt), MSG_NOSIGNAL);
}

} // namespace

extern "C" {

JNIEXPORT void JNICALL
Java_za_co_circleos_circleplay_runtime_SurfaceBridge_nativeInit(
        JNIEnv* env, jclass, jstring fbPath, jstring inputPath) {
    const char* fb = env->GetStringUTFChars(fbPath, nullptr);
    const char* ip = env->GetStringUTFChars(inputPath, nullptr);
    if (fb) { strncpy(g_fb_path, fb, sizeof(g_fb_path) - 1); env->ReleaseStringUTFChars(fbPath, fb); }
    if (ip) { strncpy(g_input_path, ip, sizeof(g_input_path) - 1); env->ReleaseStringUTFChars(inputPath, ip); }
    LOGI("init fb=%s input=%s", g_fb_path, g_input_path);
}

JNIEXPORT void JNICALL
Java_za_co_circleos_circleplay_runtime_SurfaceBridge_nativeAttach(
        JNIEnv* env, jclass, jobject surface) {
    ANativeWindow* w = ANativeWindow_fromSurface(env, surface);
    pthread_mutex_lock(&g_win_lock);
    if (g_window) ANativeWindow_release(g_window);
    g_window = w;
    pthread_mutex_unlock(&g_win_lock);
    if (!g_running.exchange(true)) {
        pthread_create(&g_thread, nullptr, render_loop, nullptr);
    }
    LOGI("attached surface");
}

JNIEXPORT void JNICALL
Java_za_co_circleos_circleplay_runtime_SurfaceBridge_nativeResize(
        JNIEnv*, jclass, jint /*w*/, jint /*h*/) {
    // Geometry tracks the source framebuffer; nothing to do here for now.
}

JNIEXPORT void JNICALL
Java_za_co_circleos_circleplay_runtime_SurfaceBridge_nativeDetach(JNIEnv*, jclass) {
    if (g_running.exchange(false)) {
        pthread_join(g_thread, nullptr);
    }
    pthread_mutex_lock(&g_win_lock);
    if (g_window) { ANativeWindow_release(g_window); g_window = nullptr; }
    pthread_mutex_unlock(&g_win_lock);
    if (g_input_fd >= 0) { close(g_input_fd); g_input_fd = -1; }
    LOGI("detached");
}

JNIEXPORT void JNICALL
Java_za_co_circleos_circleplay_runtime_SurfaceBridge_nativeMotion(
        JNIEnv*, jclass, jint x, jint y, jint action) {
    send_input(1, x, y, action);
}

JNIEXPORT void JNICALL
Java_za_co_circleos_circleplay_runtime_SurfaceBridge_nativeKey(
        JNIEnv*, jclass, jint keycode, jint down) {
    send_input(3, keycode, down, 0);
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM*, void*) {
    return JNI_VERSION_1_6;
}

} // extern "C"
