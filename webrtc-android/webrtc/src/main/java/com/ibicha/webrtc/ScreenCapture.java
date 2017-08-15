package com.ibicha.webrtc;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import org.webrtc.ScreenCapturerAndroid;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

/**
 * Created by bhadriche on 7/31/2017.
 */

class ScreenCapture implements ActivityResultHelper.ActivityResultListener {
    private static final String TAG = ScreenCapture.class.getSimpleName();


    private static ScreenCapture _instance;

    public static ScreenCapture getInstance() {
        if (_instance == null) {
            _instance = new ScreenCapture();
        }
        return _instance;
    }

    private static final int CAPTURE_PERMISSION_REQUEST_CODE = 145;

    private VideoCallback callback;
    private int videoWidth;
    private int videoHeight;
    private int videoFps;
    private Activity mainActivity;

    public ScreenCapture() {
        ActivityResultHelper.addListener(this);
    }

    void StartScreenCapture(Activity mainActivity, VideoCallback callback) {
        StartScreenCapture(mainActivity, callback, 0, 0, 0);
    }

    void StartScreenCapture(Activity mainActivity, VideoCallback callback, int videoWidth, int videoHeight, int videoFps) {
        if (mainActivity == null) {
            callback.onVideoCapturerError("Could not get main activity.");
            return;
        }
        this.mainActivity = mainActivity;
        this.callback = callback;

        if (videoWidth == 0 || videoHeight == 0) {
            DisplayMetrics displayMetrics = new DisplayMetrics();
            WindowManager windowManager =
                    (WindowManager) mainActivity.getApplication().getSystemService(Context.WINDOW_SERVICE);
            windowManager.getDefaultDisplay().getRealMetrics(displayMetrics);
            videoWidth = displayMetrics.widthPixels;
            videoHeight = displayMetrics.heightPixels;
        }
        if (videoFps == 0) {
            videoFps = 30;
        }
        this.videoWidth = videoWidth;
        this.videoHeight = videoHeight;
        this.videoFps = videoFps;

        Log.d(TAG, "Got size: " + videoWidth + "x" + videoHeight);

        MediaProjectionManager mediaProjectionManager =
                (MediaProjectionManager) mainActivity.getApplication().getSystemService(
                        Context.MEDIA_PROJECTION_SERVICE);
        mainActivity.startActivityForResult(
                mediaProjectionManager.createScreenCaptureIntent(), CAPTURE_PERMISSION_REQUEST_CODE);

    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        Log.d(TAG, "onActivityResult");
        if (requestCode != CAPTURE_PERMISSION_REQUEST_CODE)
            return;
        if (resultCode != Activity.RESULT_OK) {
            callback.onVideoCapturerError("User didn't give permission to capture the screen.");
            return;
        }
        Log.d(TAG, "onActivityResult RESULT_OK");
        final ScreenCapturerAndroid videoCapturer = new ScreenCapturerAndroid(
                resultData, new MediaProjection.Callback() {
            @Override
            public void onStop() {
                callback.onVideoCapturerStopped();
            }
        });

        VideoSource videoSource = UnityEGLUtils.getFactory(mainActivity).createVideoSource(videoCapturer);
        videoCapturer.startCapture(videoWidth, videoHeight, videoFps);
        VideoTrack videoTrack = UnityEGLUtils.getFactory(mainActivity).createVideoTrack("ARDAMSv0", videoSource);
        videoTrack.setEnabled(true);
        videoTrack.addRenderer(new VideoRenderer(new VideoRenderer.Callbacks() {
            @Override
            public void renderFrame(VideoRenderer.I420Frame i420Frame) {
                if (i420Frame.yuvFrame) {
                    throw new UnsupportedOperationException("Only texture frames.");
                }
                Log.d(TAG, "renderFrame: texture:" + i420Frame.textureId + " size:" + i420Frame.width + "x" + i420Frame.height +
                        " rotation:" + i420Frame.rotationDegree);
                callback.renderFrame(i420Frame.rotatedWidth(), i420Frame.rotatedHeight(), i420Frame.textureId, i420Frame);

            }
        }));
        Log.d(TAG, "onVideoCapturerStarted");
        callback.onVideoCapturerStarted(videoCapturer, videoTrack);

    }
}
