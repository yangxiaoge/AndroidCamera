package com.android.xz.view.base;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.EGL14;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.xz.camera.ICameraManager;
import com.android.xz.camera.callback.CameraCallback;
import com.android.xz.encoder.MediaRecordListener;
import com.android.xz.encoder.TextureMovieEncoder;
import com.android.xz.gles.Drawable2d;
import com.android.xz.gles.EglCore;
import com.android.xz.gles.GlUtil;
import com.android.xz.gles.ScaledDrawable2d;
import com.android.xz.gles.Sprite2d;
import com.android.xz.gles.Texture2dProgram;
import com.android.xz.gles.WindowSurface;
import com.android.xz.util.ImageUtils;
import com.android.xz.util.Logs;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Date;

public abstract class BaseGLTextureView extends TextureView implements TextureView.SurfaceTextureListener, SurfaceTexture.OnFrameAvailableListener, CameraCallback, BaseCameraView {
    private static final String TAG = BaseGLTextureView.class.getSimpleName();

    private static final int DEFAULT_ZOOM_PERCENT = 0;      // 0-100
    private static final int DEFAULT_SIZE_PERCENT = 100;     // 0-100
    private static final int DEFAULT_ROTATE_PERCENT = 0;    // 0-100

    private Context mContext;
    private SurfaceTexture mSurfaceTexture;
    private SurfaceTexture mPreviewSurfaceTexture;
    private MainHandler mMainHandler;
    private boolean isMirror;
    private boolean hasSurface; // 是否存在摄像头显示层
    private ICameraManager mCameraManager;
    private int mRatioWidth = 0;
    private int mRatioHeight = 0;
    private int mPreviewWidth;
    private int mPreviewHeight;
    private RenderThread mRenderThread;
    private TextureMovieEncoder mMovieEncoder;
    private boolean mRecordingEnabled;      // controls button state

    public BaseGLTextureView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public BaseGLTextureView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public BaseGLTextureView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    public BaseGLTextureView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context);
    }

    private void init(Context context) {
        mContext = context;
        mCameraManager = createCameraManager(context);
        mCameraManager.setCameraCallback(this);
        setSurfaceTextureListener(this);
        mMainHandler = new MainHandler(this);
        mMovieEncoder = new TextureMovieEncoder(context);
        mRenderThread = new RenderThread(mMainHandler, mMovieEncoder);
        mRenderThread.start();
        mRenderThread.waitUntilReady();
    }

    public void startRecord() {
        mRecordingEnabled = true;
        if (mRenderThread != null) {
            RenderHandler handler = mRenderThread.getHandler();
            if (handler != null) {
                handler.sendRecordState(true);
            }
        }
    }

    public void stopRecord() {
        mRecordingEnabled = false;
        if (mRenderThread != null) {
            RenderHandler handler = mRenderThread.getHandler();
            if (handler != null) {
                handler.sendRecordState(false);
            }
        }
    }

    public abstract ICameraManager createCameraManager(Context context);

    /**
     * 获取摄像头工具类
     *
     * @return
     */
    public ICameraManager getCameraManager() {
        return mCameraManager;
    }

    /**
     * 是否镜像
     *
     * @return
     */
    public boolean isMirror() {
        return isMirror;
    }

    /**
     * 设置是否镜像
     *
     * @param mirror
     */
    public void setMirror(boolean mirror) {
        isMirror = mirror;
        requestLayout();
    }

    private void setAspectRatio(int width, int height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Size cannot be negative.");
        }
        mRatioWidth = width;
        mRatioHeight = height;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (0 == mRatioWidth || 0 == mRatioHeight) {
            setMeasuredDimension(width, width * 4 / 3);
        } else {
            if (width < height * mRatioWidth / mRatioHeight) {
                setMeasuredDimension(width, width * mRatioHeight / mRatioWidth);
            } else {
                setMeasuredDimension(height * mRatioWidth / mRatioHeight, height);
            }
        }

        if (isMirror) {
            android.graphics.Matrix transform = new android.graphics.Matrix();
            transform.setScale(-1, 1, getMeasuredWidth() / 2, 0);
            setTransform(transform);
        } else {
            setTransform(null);
        }
    }

    /**
     * 获取SurfaceTexture
     *
     * @return
     */
    @Override
    public SurfaceTexture getSurfaceTexture() {
        return mPreviewSurfaceTexture;
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
        Logs.i(TAG, "onSurfaceTextureAvailable " + width + "x" + height);
        mSurfaceTexture = surfaceTexture;
        if (mRenderThread != null) {
            RenderHandler handler = mRenderThread.getHandler();
            if (handler != null) {
                handler.sendSurfaceAvailable(mSurfaceTexture, true);
                handler.sendSurfaceChanged(0, width, height);
            }
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
        Logs.i(TAG, "onSurfaceTextureSizeChanged " + width + "x" + height);
        if (mRenderThread != null) {
            RenderHandler handler = mRenderThread.getHandler();
            if (handler != null) {
                handler.sendSurfaceChanged(0, width, height);
            }
        }
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        Logs.v(TAG, "onSurfaceTextureDestroyed.");
        closeCamera();
        mSurfaceTexture = null;
        if (mRenderThread != null) {
            RenderHandler handler = mRenderThread.getHandler();
            if (handler != null) {
                handler.sendSurfaceDestroyed();
            }
        }
        hasSurface = false;
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        if (mRenderThread != null) {
            RenderHandler handler = mRenderThread.getHandler();
            if (handler != null) {
                handler.sendFrameAvailable();
            }
        }
    }

    /**
     * Connects the SurfaceTexture to the Camera preview output, and starts the preview.
     */
    private void handleSetSurfaceTexture(SurfaceTexture st) {
        Logs.i(TAG, "handleSetSurfaceTexture " + st);
        mPreviewSurfaceTexture = st;
        hasSurface = true;
        mPreviewSurfaceTexture.setOnFrameAvailableListener(this);
        openCamera();
    }

    /**
     * 打开摄像头并预览
     */
    @Override
    public void onResume() {
        if (hasSurface) {
            // 当activity暂停，但是并未停止的时候，surface仍然存在，所以 surfaceCreated()
            // 并不会调用，需要在此处初始化摄像头
            openCamera();
        }
    }

    /**
     * 停止预览并关闭摄像头
     */
    @Override
    public void onPause() {
        closeCamera();
    }

    @Override
    public void onDestroy() {
        if (mRenderThread != null) {
            RenderHandler handler = mRenderThread.getHandler();
            if (handler != null) {
                handler.sendShutdown();
            }
        }
        mMainHandler.invalidateHandler();
    }

    /**
     * 初始化摄像头，较为关键的内容
     */
    private void openCamera() {
        if (mPreviewSurfaceTexture == null) {
            Logs.e(TAG, "mSurfaceTexture is null.");
            return;
        }
        if (mCameraManager.isOpen()) {
            Logs.w(TAG, "Camera is opened！");
            return;
        }
        mCameraManager.openCamera();
    }

    private void closeCamera() {
        mCameraManager.releaseCamera();
    }

    @Override
    public void onOpen() {
        mCameraManager.startPreview(mPreviewSurfaceTexture);
        if (mRenderThread != null) {
            RenderHandler handler = mRenderThread.getHandler();
            if (handler != null) {
                handler.sendRotate(mCameraManager.getOrientation(), mCameraManager.getCameraId());
            }
        }
    }

    @Override
    public void onOpenError(int error, String msg) {

    }

    @Override
    public void onPreview(int previewWidth, int previewHeight) {
        Logs.i(TAG, "onPreview " + previewWidth + " " + previewHeight);
        if (mRenderThread != null) {
            RenderHandler handler = mRenderThread.getHandler();
            if (handler != null) {
                handler.sendPreviewSize(previewWidth, previewHeight);
            }
        }

        mPreviewWidth = previewWidth;
        mPreviewHeight = previewHeight;
        setAspectRatio(mPreviewHeight, mPreviewWidth);
    }

    @Override
    public void onPreviewError(int error, String msg) {

    }

    @Override
    public void onClose() {

    }

    /**
     * Handles camera operation requests from other threads.  Necessary because the Camera
     * must only be accessed from one thread.
     * <p>
     * The object is created on the UI thread, and all handlers run there.  Messages are
     * sent from other threads, using sendMessage().
     */
    static class MainHandler extends Handler {
        public static final int MSG_SET_SURFACE_TEXTURE = 0;

        public static final int MSG_SURFACE_CHANGED = 1;

        private WeakReference<BaseGLTextureView> mWeakGLSurfaceView;

        public MainHandler(BaseGLTextureView view) {
            mWeakGLSurfaceView = new WeakReference<>(view);
        }

        /**
         * Drop the reference to the activity.  Useful as a paranoid measure to ensure that
         * attempts to access a stale Activity through a handler are caught.
         */
        public void invalidateHandler() {
            mWeakGLSurfaceView.clear();
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            int what = msg.what;

            BaseGLTextureView view = mWeakGLSurfaceView.get();
            if (view == null) {
                return;
            }

            switch (what) {
                case MSG_SET_SURFACE_TEXTURE:
                    view.handleSetSurfaceTexture((SurfaceTexture) msg.obj);
                    break;
                default:
                    throw new RuntimeException("unknown msg " + what);
            }
        }
    }

    static class RenderHandler extends Handler {
        private static final int MSG_SURFACE_AVAILABLE = 0;
        private static final int MSG_SURFACE_CHANGED = 1;
        private static final int MSG_SURFACE_DESTROYED = 2;
        private static final int MSG_SHUTDOWN = 3;
        private static final int MSG_FRAME_AVAILABLE = 4;
        private static final int MSG_ZOOM_VALUE = 5;
        private static final int MSG_SIZE_VALUE = 6;
        private static final int MSG_ROTATE_VALUE = 7;
        private static final int MSG_POSITION = 8;
        private static final int MSG_REDRAW = 9;
        private static final int MSG_RECORD_STATE = 10;

        // This shouldn't need to be a weak ref, since we'll go away when the Looper quits,
        // but no real harm in it.
        private WeakReference<RenderThread> mWeakRenderThread;

        /**
         * Call from render thread.
         */
        public RenderHandler(RenderThread rt) {
            mWeakRenderThread = new WeakReference<>(rt);
        }

        /**
         * Sends the "surface available" message.  If the surface was newly created (i.e.
         * this is called from surfaceCreated()), set newSurface to true.  If this is
         * being called during Activity startup for a previously-existing surface, set
         * newSurface to false.
         * <p>
         * The flag tells the caller whether or not it can expect a surfaceChanged() to
         * arrive very soon.
         * <p>
         * Call from UI thread.
         */
        public void sendSurfaceAvailable(SurfaceTexture surfaceTexture, boolean newSurface) {
            sendMessage(obtainMessage(MSG_SURFACE_AVAILABLE, newSurface ? 1 : 0, 0, surfaceTexture));
        }

        /**
         * Sends the "surface changed" message, forwarding what we got from the SurfaceHolder.
         * <p>
         * Call from UI thread.
         */
        public void sendSurfaceChanged(int format, int width,
                                       int height) {
            // ignore format
            sendMessage(obtainMessage(MSG_SURFACE_CHANGED, width, height));
        }

        /**
         * Sends the "shutdown" message, which tells the render thread to halt.
         * <p>
         * Call from UI thread.
         */
        public void sendSurfaceDestroyed() {
            sendMessage(obtainMessage(MSG_SURFACE_DESTROYED));
        }

        /**
         * Sends the "shutdown" message, which tells the render thread to halt.
         * <p>
         * Call from UI thread.
         */
        public void sendShutdown() {
            sendMessage(obtainMessage(MSG_SHUTDOWN));
        }

        /**
         * Sends the "frame available" message.
         * <p>
         * Call from UI thread.
         */
        public void sendFrameAvailable() {
            sendMessage(obtainMessage(MSG_FRAME_AVAILABLE));
        }

        /**
         * Sends the "rotation" message.
         * <p>
         * Call from UI thread.
         */
        public void sendRotate(int rotation, int cameraId) {
            sendMessage(obtainMessage(MSG_ROTATE_VALUE, rotation, cameraId));
        }

        /**
         * Sends the "preview size" message.
         * <p>
         * Call from UI thread.
         */
        public void sendPreviewSize(int width, int height) {
            sendMessage(obtainMessage(MSG_SIZE_VALUE, width, height));
        }

        public void sendRecordState(boolean state) {
            sendMessage(obtainMessage(MSG_RECORD_STATE, state));
        }

        @Override  // runs on RenderThread
        public void handleMessage(Message msg) {
            int what = msg.what;
            //Log.d(TAG, "RenderHandler [" + this + "]: what=" + what);

            RenderThread renderThread = mWeakRenderThread.get();
            if (renderThread == null) {
                Log.w(TAG, "RenderHandler.handleMessage: weak ref is null");
                return;
            }

            switch (what) {
                case MSG_SURFACE_AVAILABLE:
                    renderThread.surfaceAvailable((SurfaceTexture) msg.obj, msg.arg1 != 0);
                    break;
                case MSG_SURFACE_CHANGED:
                    renderThread.surfaceChanged(msg.arg1, msg.arg2);
                    break;
                case MSG_SURFACE_DESTROYED:
                    renderThread.surfaceDestroyed();
                    break;
                case MSG_SHUTDOWN:
                    renderThread.shutdown();
                    break;
                case MSG_FRAME_AVAILABLE:
                    renderThread.frameAvailable();
                    break;
                case MSG_SIZE_VALUE:
                    renderThread.setCameraPreviewSize(msg.arg1, msg.arg2);
                    break;
                case MSG_ROTATE_VALUE:
                    renderThread.setRotate(msg.arg1, msg.arg2);
                    break;
                case MSG_RECORD_STATE:
                    renderThread.changeRecordingState((boolean) msg.obj);
                    break;
                default:
                    throw new RuntimeException("unknown message " + what);
            }
        }
    }

    /**
     * Renderer object for our GLSurfaceView.
     * <p>
     * Do not call any methods here directly from another thread -- use the
     * GLSurfaceView#queueEvent() call.
     */
    static class RenderThread extends Thread {

        private static final int RECORDING_OFF = 0;
        private static final int RECORDING_ON = 1;
        private static final int RECORDING_RESUMED = 2;

        // Used to wait for the thread to start.
        private Object mStartLock = new Object();
        private boolean mReady = false;
        private MainHandler mMainHandler;
        private RenderHandler mHandler;

        // width/height of the incoming camera preview frames
        private SurfaceTexture mPreviewTexture;
        private int mTextureId;

        private float[] mDisplayProjectionMatrix = new float[16];
        private EglCore mEglCore;
        private WindowSurface mWindowSurface;
        private Texture2dProgram mTexProgram;
        private final ScaledDrawable2d mRectDrawable =
                new ScaledDrawable2d(Drawable2d.Prefab.RECTANGLE);
        private final Sprite2d mRect = new Sprite2d(mRectDrawable);
        private int mWindowSurfaceWidth;
        private int mWindowSurfaceHeight;
        private int mCameraPreviewWidth, mCameraPreviewHeight;
        private boolean mSizeUpdated;

        private int mZoomPercent = DEFAULT_ZOOM_PERCENT;
        private int mSizePercent = DEFAULT_SIZE_PERCENT;
        private int mRotatePercent = DEFAULT_ROTATE_PERCENT;
        private float mPosX, mPosY;
        private int mRotate;
        private boolean mRotateUpdated;
        private boolean mMirror;

        private File mOutputFile;
        private TextureMovieEncoder mVideoEncoder;
        private boolean mRecordingEnabled;
        private int mRecordingStatus;
        private long mVideoMillis;

        public RenderThread(MainHandler mainHandler, TextureMovieEncoder textureMovieEncoder) {
            super("Renderer Thread");
            mMainHandler = mainHandler;
            mVideoEncoder = textureMovieEncoder;
            mSizeUpdated = false;
        }

        @Override
        public void run() {
            super.run();
            Logs.i(TAG, "Render Thread start.");
            Looper.prepare();

            // We need to create the Handler before reporting ready.
            mHandler = new RenderHandler(this);
            synchronized (mStartLock) {
                mReady = true;
                mStartLock.notify();    // signal waitUntilReady()
            }

            // Prepare EGL and open the camera before we start handling messages.
            mEglCore = new EglCore(null, 0);

            Looper.loop();
            mHandler = null;
            releaseGl();
            mEglCore.release();

            Logs.v(TAG, "Render Thread exit.");
        }

        /**
         * Notifies the renderer that we want to stop or start recording.
         */
        public void changeRecordingState(boolean isRecording) {
            Log.d(TAG, "changeRecordingState: was " + mRecordingEnabled + " now " + isRecording);
            mRecordingEnabled = isRecording;
        }

        public void notifyStopRecord() {
            if (mVideoEncoder != null && mVideoEncoder.isRecording()) {
                mVideoEncoder.stopRecording();
                mRecordingStatus = RECORDING_OFF;
            }
        }

        /**
         * Waits until the render thread is ready to receive messages.
         * <p>
         * Call from the UI thread.
         */
        public void waitUntilReady() {
            synchronized (mStartLock) {
                while (!mReady) {
                    try {
                        mStartLock.wait();
                    } catch (InterruptedException ie) { /* not expected */ }
                }
            }
        }

        /**
         * Shuts everything down.
         */
        private void shutdown() {
            Log.d(TAG, "shutdown");
            Looper.myLooper().quit();
        }

        public RenderHandler getHandler() {
            return mHandler;
        }

        public void surfaceAvailable(SurfaceTexture surfaceTexture, boolean newSurface) {
            mRecordingEnabled = mVideoEncoder.isRecording();
            if (mRecordingEnabled) {
                mRecordingStatus = RECORDING_RESUMED;
            } else {
                mRecordingStatus = RECORDING_OFF;
            }

            mWindowSurface = new WindowSurface(mEglCore, surfaceTexture);
            mWindowSurface.makeCurrent();

            // Create and configure the SurfaceTexture, which will receive frames from the
            // camera.  We set the textured rect's program to render from it.
            mTexProgram = new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT);
            mTextureId = mTexProgram.createTextureObject();
            mPreviewTexture = new SurfaceTexture(mTextureId);
            mRect.setTexture(mTextureId);

            if (!newSurface) {
                mWindowSurfaceWidth = mWindowSurface.getWidth();
                mWindowSurfaceHeight = mWindowSurface.getHeight();

                finishSurfaceSetup();
            }

            mMainHandler.sendMessage(mMainHandler.obtainMessage(MainHandler.MSG_SET_SURFACE_TEXTURE, mPreviewTexture));
        }

        public void surfaceChanged(int width, int height) {
            mWindowSurfaceWidth = width;
            mWindowSurfaceHeight = height;
            finishSurfaceSetup();
        }

        public void surfaceDestroyed() {
            // In practice this never appears to be called -- the activity is always paused
            // before the surface is destroyed.  In theory it could be called though.
            Log.d(TAG, "RenderThread surfaceDestroyed");
            releaseGl();
        }

        private void finishSurfaceSetup() {
            int width = mWindowSurfaceWidth;
            int height = mWindowSurfaceHeight;
            if (width == 0) {
                width = 1;
            }
            if (height == 0) {
                height = 1;
            }
            Log.d(TAG, "finishSurfaceSetup size=" + width + "x" + height +
                    " camera=" + mCameraPreviewWidth + "x" + mCameraPreviewHeight);

            // Use full window.
            GLES20.glViewport(0, 0, width, height);

            // Simple orthographic projection, with (0,0) in lower-left corner.
            Matrix.orthoM(mDisplayProjectionMatrix, 0, 0, width, 0, height, -1, 1);

            mPosX = width / 2.0f;
            mPosY = height / 2.0f;

            updateGeometry();
        }

        /**
         * Updates the geometry of mRect, based on the size of the window and the current
         * values set by the UI.
         */
        private void updateGeometry() {
            int width = mWindowSurfaceWidth;
            int height = mWindowSurfaceHeight;

            int smallDim = Math.min(width, height);
            // Max scale is a bit larger than the screen, so we can show over-size.
            float scaled = smallDim * (mSizePercent / 100.0f) * 1f;
            float cameraAspect = (float) mCameraPreviewWidth / mCameraPreviewHeight;
            int newWidth = Math.round(scaled * cameraAspect);
            int newHeight = Math.round(scaled);

            float zoomFactor = 1.0f - (mZoomPercent / 100.0f);

            mRect.setMirror(mMirror);
            mRect.setScale(newWidth, newHeight);
            mRect.setPosition(mPosX, mPosY);
            mRect.setRotation(-mRotate);
            mRectDrawable.setScale(zoomFactor);

            Logs.i(TAG, "new size:" + newWidth + "*" + newHeight);
        }

        public void frameAvailable() {
            draw();
        }

        public void draw() {
            if (mPreviewTexture == null) return;
            if (mWindowSurface == null) return;

            mPreviewTexture.updateTexImage();
            GlUtil.checkGlError("draw start");

            // If the recording state is changing, take care of it here.  Ideally we wouldn't
            // be doing all this in onDrawFrame(), but the EGLContext sharing with GLSurfaceView
            // makes it hard to do elsewhere.
            if (mRecordingEnabled) {
                switch (mRecordingStatus) {
                    case RECORDING_OFF:
                        Log.d(TAG, "START recording");
                        // 开始录制前删除之前的视频文件
                        String name = "VID_" + ImageUtils.DATE_FORMAT.format(new Date(System.currentTimeMillis())) + ".mp4";
                        mOutputFile = new File(ImageUtils.getVideoPath(), name);
                        // start recording
                        mVideoEncoder.startRecording(new TextureMovieEncoder.EncoderConfig(
                                mOutputFile, mCameraPreviewHeight, mCameraPreviewWidth, mCameraPreviewWidth * mCameraPreviewHeight * 10, EGL14.eglGetCurrentContext()));
                        mRecordingStatus = RECORDING_ON;
                        break;
                    case RECORDING_RESUMED:
                        Log.d(TAG, "RESUME recording");
                        mVideoEncoder.updateSharedContext(EGL14.eglGetCurrentContext());
                        mRecordingStatus = RECORDING_ON;
                        break;
                    case RECORDING_ON:
                        // yay
                        break;
                    default:
                        throw new RuntimeException("unknown status " + mRecordingStatus);
                }
            } else {
                switch (mRecordingStatus) {
                    case RECORDING_ON:
                    case RECORDING_RESUMED:
                        // stop recording
                        Log.d(TAG, "STOP recording");
                        mVideoEncoder.stopRecording();
                        mRecordingStatus = RECORDING_OFF;
                        break;
                    case RECORDING_OFF:
                        // yay
                        break;
                    default:
                        throw new RuntimeException("unknown status " + mRecordingStatus);
                }
            }

            //        if (mVideoEncoder.isRecording() && System.currentTimeMillis() - mVideoMillis > 50) {
            if (mVideoEncoder.isRecording()) {
                // Set the video encoder's texture name.  We only need to do this once, but in the
                // current implementation it has to happen after the video encoder is started, so
                // we just do it here.
                //
                // TODO: be less lame.
                mVideoEncoder.setTextureId(mTextureId);

                // Tell the video encoder thread that a new frame is available.
                // This will be ignored if we're not actually recording.
                mVideoEncoder.frameAvailable(mPreviewTexture);

                mVideoMillis = System.currentTimeMillis();
            }

            if (mSizeUpdated) {
                mSizeUpdated = false;
                finishSurfaceSetup();
            }
            if (mRotateUpdated) {
                mRotateUpdated = false;
                finishSurfaceSetup();
            }

            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            mRect.draw(mTexProgram, mDisplayProjectionMatrix);
            mWindowSurface.swapBuffers();

            GlUtil.checkGlError("draw done");
        }

        public void setCameraPreviewSize(int width, int height) {
            mCameraPreviewWidth = width;
            mCameraPreviewHeight = height;
            mSizeUpdated = true;
        }

        public void setRotate(int rotation, int cameraId) {
            this.mRotate = rotation;
            this.mMirror = (cameraId == Camera.CameraInfo.CAMERA_FACING_FRONT);
            mRotateUpdated = true;
        }

        public void setMirror(boolean mirror) {

        }

        /**
         * Releases most of the GL resources we currently hold (anything allocated by
         * surfaceAvailable()).
         * <p>
         * Does not release EglCore.
         */
        private void releaseGl() {
            GlUtil.checkGlError("releaseGl start");

            if (mWindowSurface != null) {
                mWindowSurface.release();
                mWindowSurface = null;
            }
            if (mPreviewTexture != null) {
                mPreviewTexture.release();
                mPreviewTexture = null;
            }
            if (mTexProgram != null) {
                mTexProgram.release();
                mTexProgram = null;
            }
            GlUtil.checkGlError("releaseGl done");

            mEglCore.makeNothingCurrent();
        }
    }

    public void setRecordListener(MediaRecordListener recordListener) {
        if (mMovieEncoder != null) {
            mMovieEncoder.setRecordListener(recordListener);
        }
    }
}
