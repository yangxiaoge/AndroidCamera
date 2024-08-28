package com.android.xz.camerademo.camera_activity;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.ImageView;

import com.android.xz.camera.CameraManager;
import com.android.xz.camera.callback.PreviewBufferCallback;
import com.android.xz.camerademo.R;
import com.android.xz.util.ImageUtils;
import com.android.xz.view.CameraGLSurfaceHolderView;

public class GLSurfaceHolderCameraActivity extends AppCompatActivity {

    private CameraGLSurfaceHolderView mCameraGLSurfaceHolderView;
    private CameraManager mCameraManager;
    private ImageView mPictureIv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_glsurface_holder_camera);

        mCameraGLSurfaceHolderView = findViewById(R.id.cameraView);
        mCameraManager = (CameraManager) mCameraGLSurfaceHolderView.getCameraManager();
        findViewById(R.id.captureBtn).setOnClickListener(v -> capture());
        findViewById(R.id.switchCameraBtn).setOnClickListener(v -> mCameraManager.switchCamera());
        mPictureIv = findViewById(R.id.pictureIv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mCameraGLSurfaceHolderView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mCameraGLSurfaceHolderView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mCameraGLSurfaceHolderView.onDestroy();
    }

    private void capture() {
        mCameraManager.takePicture(mPictureCallback);
    }

    private final Camera.PictureCallback mPictureCallback = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            mCameraManager.startPreview(mCameraGLSurfaceHolderView.getSurfaceTexture()); // 拍摄结束继续预览
            new ImageSaveTask().execute(data); // 保存图片
        }
    };

    private class ImageSaveTask extends AsyncTask<byte[], Void, Bitmap> {

        @Override
        protected Bitmap doInBackground(byte[]... bytes) {
            ImageUtils.saveImage(bytes[0]);
            return ImageUtils.getLatestThumbBitmap();
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            mPictureIv.setImageBitmap(bitmap);
        }
    }
}