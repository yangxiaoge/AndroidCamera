package com.android.xz.camera.view;

import android.content.Context;
import android.util.AttributeSet;

import com.android.xz.camera.CameraManager;
import com.android.xz.camera.ICameraManager;
import com.android.xz.camera.view.base.BaseGLESSurfaceView;

/**
 * 适用Camera的SurfaceView，自定义opengl
 *
 * @author xiaozhi
 * @since 2024/8/22
 */
public class CameraGLSurfaceHolderView extends BaseGLESSurfaceView {

    public CameraGLSurfaceHolderView(Context context) {
        super(context);
    }

    public CameraGLSurfaceHolderView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CameraGLSurfaceHolderView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public ICameraManager createCameraManager(Context context) {
        return new CameraManager(context);
    }
}
