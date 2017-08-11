package com.ibicha.webrtc;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLES31;
import android.opengl.GLES31Ext;
import android.util.Log;

import org.webrtc.EglBase;
import org.webrtc.EglBase14Ex;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.VideoRenderer;

import java.util.HashMap;

/**
 * Created by bhadriche on 8/1/2017.
 */

public class UnityEGLUtils {
    private static final String TAG = UnityEGLUtils.class.getSimpleName();


    private static PeerConnectionFactory factory;
    private static EglBase rootEglBase;

    public static EGLContext unityContext = EGL14.EGL_NO_CONTEXT;
    public static EGLDisplay unityDisplay = EGL14.EGL_NO_DISPLAY;
    public static EGLSurface unityDrawSurface = EGL14.EGL_NO_SURFACE;
    public static EGLSurface unityReadSurface = EGL14.EGL_NO_SURFACE;

    public static PeerConnectionFactory getFactory(Activity mainActivity) {
        if (factory != null) {
            return factory;
        }
        PeerConnectionFactory.initializeAndroidGlobals(mainActivity.getApplicationContext(), WebRTC.hwAccelerate);
        factory = new PeerConnectionFactory(new PeerConnectionFactory.Options());

        if (WebRTC.hwAccelerate) {
            factory.setVideoHwAccelerationOptions(getRootEglBase().getEglBaseContext(), getRootEglBase().getEglBaseContext());
        }

        return factory;
    }


    static EglBase getRootEglBase() {
        //If no acceleration, no need for any EGL work.
        if (!WebRTC.hwAccelerate)
            return null;

        if (rootEglBase != null) {
            return rootEglBase;
        }
        //First approach: creating a context, getting config attributes, and let EglBase handle it.

        EGLContext eglContext = unityContext; // getEglContext();
        EGLDisplay eglDisplay = unityDisplay; // getEglDisplay();
        int[] configAttributes = getEglConfigAttr(eglDisplay, eglContext);
//        int[] configAttributes = EglBase.CONFIG_PIXEL_RGBA_BUFFER;
        int[] clientVersion = {2};
        if (!EGL14.eglQueryContext(eglDisplay, eglContext, EGL14.EGL_CONTEXT_CLIENT_VERSION, clientVersion, 0)) {
            throw new RuntimeException("eglQueryContext failed: 0x" + Integer.toHexString(EGL14.eglGetError()));
        }

        Log.d(TAG, "getRootEglBase: clientVersion: " + clientVersion[0]);

        rootEglBase = EglBase14Ex.create(new org.webrtc.EglBase14Ex.Context(eglContext), unityDisplay, configAttributes, clientVersion[0]);// EglBase.createEgl14(eglContext, configAttributes);
//        rootEglBase.createPbufferSurface(1280, 720);
        rootEglBase.createDummyPbufferSurface();
        return rootEglBase;
        //Second approach: pass the unityContext that we recovered while in the rendering thread (in the Update method)
        //Wrap it and pass it to the factory.
//        EGLContext eglContext = unityContext;
//        EglBase14Wrapper contextWrapper = new EglBase14Wrapper(eglContext);
//        factory.setVideoHwAccelerationOptions(contextWrapper.getEglBaseContext(), contextWrapper.getEglBaseContext());
    }

    private static EGLContext getEglContext() {
        EGLContext context = EGL14.eglGetCurrentContext();
        if (context == EGL14.EGL_NO_CONTEXT) {
            context = UnityEGLUtils.unityContext;
            if (context == EGL14.EGL_NO_CONTEXT) {
                Log.d(TAG, "getEglContext: EGL_NO_CONTEXT");
            }
        }
        return context;
    }

    public static void KillFrame(VideoRenderer.I420Frame i420Frame) {
        VideoRenderer.renderFrameDone(i420Frame);
    }

    //This is called from the Unity main thread, so we can keep a reference to it
    public static void eglContextSet() {
        unityContext = EGL14.eglGetCurrentContext();
        unityDisplay = EGL14.eglGetCurrentDisplay();
        unityDrawSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW);
        unityReadSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_READ);
        Log.d(TAG, "eglContextSet: unityThread.getName() " + Thread.currentThread().getName());

        if (unityContext == EGL14.EGL_NO_CONTEXT) {
            Log.d(TAG, "eglContextSet: unityContext == EGL_NO_CONTEXT");
        }
        if (unityDisplay == EGL14.EGL_NO_DISPLAY) {
            Log.d(TAG, "eglContextSet: unityDisplay == EGL_NO_DISPLAY");
        }
        if (unityDrawSurface == EGL14.EGL_NO_SURFACE) {
            Log.d(TAG, "eglContextSet: unityDrawSurface == EGL_NO_SURFACE");
        }
        if (unityReadSurface == EGL14.EGL_NO_SURFACE) {
            Log.d(TAG, "eglContextSet: unityReadSurface == EGL_NO_SURFACE");
        }
        Log.d(TAG, "eglContextSet: DONE");

    }

     public static void switchToUnityContext() {
        if (unityContext == EGL14.EGL_NO_CONTEXT) {
            Log.d(TAG, "switchToUnityContext: unityContext == EGL14.EGL_NO_CONTEXT");
            return;
        }

        if (!EGL14.eglMakeCurrent(unityDisplay, unityDrawSurface, unityReadSurface, unityContext)) {
            throw new RuntimeException("switchToUnityContext eglMakeCurrent failed: 0x" + Integer.toHexString(EGL14.eglGetError()));
        }
    }

    private static int[] getEglConfigAttr(EGLDisplay eglDisplay, EGLContext eglContext) {
        int[] keys = {EGL14.EGL_CONFIG_ID};
        int[] configAttributes = new int[keys.length * 2 + 1];

        for (int i = 0; i < keys.length; i++) {
            configAttributes[i * 2] = keys[i];
            if (!EGL14.eglQueryContext(eglDisplay, eglContext, keys[i], configAttributes, i * 2 + 1)) {
                throw new RuntimeException("eglQueryContext failed: 0x" + Integer.toHexString(EGL14.eglGetError()));
            }
        }

        configAttributes[configAttributes.length - 1] = EGL14.EGL_NONE;
        return configAttributes;
    }

    private static EGLConfig getEglConfig(EGLDisplay eglDisplay) {
        EGLConfig[] configs = new EGLConfig[1];
        int[] num_config = new int[1];
        if (!EGL14.eglGetConfigs(eglDisplay, configs, 0, configs.length, num_config, 0)) {
            throw new RuntimeException("eglGetConfigs failed: 0x" + Integer.toHexString(EGL14.eglGetError()));
        }
        if (num_config[0] == 0) {
            throw new RuntimeException("eglGetConfigs failed: No config found");
        }
        return configs[0];
    }

    private static EGLDisplay getEglDisplay() {
        EGLDisplay eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            throw new RuntimeException("Unable to get EGL14 display: 0x" + Integer.toHexString(EGL14.eglGetError()));
        } else {
            if (!EGL14.eglInitialize(eglDisplay, null, 0, null, 0)) {
                throw new RuntimeException("Unable to initialize EGL14: 0x" + Integer.toHexString(EGL14.eglGetError()));
            } else {
                return eglDisplay;
            }
        }
    }

}
