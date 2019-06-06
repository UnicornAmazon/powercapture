package com.afscope.powercapture;

import android.Manifest;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.os.Environment;
import android.os.Vibrator;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback,EasyPermissions.PermissionCallbacks, DialogInterface.OnDismissListener {

    private SurfaceView surfaceView;
    private Camera mCamera;//相机
    private int mCurrCameraFacing;//当前设置头类型,0:后置/1:前置
    private File mCaptureFile;//拍照保存的文件
    private SurfaceHolder mSurfaceHolder;
    private int cameraCode=0;
    private int mOrientation;//当前屏幕旋转角度
    private int mWidthPixel, mHeightPixel;//SurfaceView的宽高

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestPermiss();
    }
    private void requestPermiss() {
        String[] perms = {Manifest.permission.CAMERA,Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.READ_EXTERNAL_STORAGE};
        if (EasyPermissions.hasPermissions(this, perms)) {
             init();

        } else {
            EasyPermissions.requestPermissions(this, "拍照需要摄像头权限",
                    cameraCode, perms);
        }
    }

    private void init() {

        Dialog dialog = new Dialog(this, R.style.dialog_style) {
            @Override
            public boolean onKeyDown(int keyCode, @NonNull KeyEvent event) {
                switch (keyCode) {

                    case KeyEvent.KEYCODE_VOLUME_DOWN:
                        //音量下键切换摄像头
                        switchCamera();
                        return true;

                    case KeyEvent.KEYCODE_VOLUME_UP:
                        //音量上键拍照
                        startCapture();
                        Vibrator vibrator = (Vibrator)MainActivity.this.getSystemService(MainActivity.this.VIBRATOR_SERVICE);
                        vibrator.vibrate(100);
                        return true;
                    case KeyEvent.KEYCODE_VOLUME_MUTE:
//                        tv.setText("MUTE");

                        return true;

                }
                return super.onKeyDown(keyCode, event);
            }
        };

        dialog.setContentView(R.layout.activity_main);
        dialog.setOnDismissListener(this);
        surfaceView = dialog.findViewById(R.id.surfaceview);
        mSurfaceHolder = surfaceView.getHolder();
        mSurfaceHolder.addCallback(this);
        dialog.show();
        Window win = dialog.getWindow();
        WindowManager.LayoutParams lp = win.getAttributes();
        lp.width = 200;
        lp.height = 200;
        lp.dimAmount = 0.2f;
        lp.gravity = Gravity.LEFT | Gravity.TOP;
        win.setAttributes(lp);
    }

    /**
     * 切换摄像头
     */
    public void switchCamera() {
        if (mCurrCameraFacing == 0) {
            mCurrCameraFacing = Camera.CameraInfo.CAMERA_FACING_FRONT;
        } else {
            mCurrCameraFacing = Camera.CameraInfo.CAMERA_FACING_BACK;
        }
        openCamera();
        if (mCamera != null) {
            setCameraParameters();
            updateCameraOrientation();
            try {
                mCamera.setPreviewDisplay(mSurfaceHolder);
                mCamera.startPreview();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 开始拍照
     */
    public void startCapture() {
        if (null != mCamera) {
            mCamera.autoFocus(new Camera.AutoFocusCallback() {
                @Override
                public void onAutoFocus(boolean success, Camera camera) {
                    camera.takePicture(null, null, pictureCallback);
                }
            });
        }
    }

    /**
     * 打开相机
     */
    private void openCamera() {
        if (null != mCamera) {
            releaseCamera();
        }
        try {
            if (!checkCameraFacing(0) && !checkCameraFacing(1)) {
                Toast.makeText(this, "未发现有可用摄像头", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!checkCameraFacing(mCurrCameraFacing)) {
                Toast.makeText(this, mCurrCameraFacing == 0 ? "后置摄像头不可用" : "前置摄像头不可用", Toast.LENGTH_SHORT).show();
                return;
            }
            mCamera = Camera.open(mCurrCameraFacing);
        } catch (Exception e) {
            e.printStackTrace();
            releaseCamera();
        }
    }

    /**
     * 检查是否有摄像头
     *
     * @param facing 前置还是后置
     * @return
     */
    private boolean checkCameraFacing(int facing) {
        int cameraCount = Camera.getNumberOfCameras();
        Camera.CameraInfo info = new Camera.CameraInfo();
        for (int i = 0; i < cameraCount; i++) {
            Camera.getCameraInfo(i, info);
            if (facing == info.facing) {
                return true;
            }
        }
        return false;
    }

    /**
     * 释放相机资源
     */
    private void releaseCamera() {
        if (null != mCamera) {
            try {
                mCamera.stopPreview();
                mCamera.release();
                mCamera = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private final Camera.PictureCallback pictureCallback = new Camera.PictureCallback() {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            if (data != null) {
                //解析生成相机返回的图片
                Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                //生成缩略图
                // Bitmap thumbnail= ThumbnailUtils.extractThumbnail(bm, 213, 213);
                try {
                    File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
                    if (!dir.exists()) {
                        dir.mkdirs();
                    }
                    mCaptureFile = new File(dir, System.currentTimeMillis() + ".jpg");
                    BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(mCaptureFile));
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bos);
                    bos.flush();
                    bos.close();
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(MainActivity.this, "保存相片失败", Toast.LENGTH_SHORT).show();
                } finally {
//                    saveRotatePicture(mCaptureFile.getPath());
                }
            } else {
                Toast.makeText(MainActivity.this, "拍照失败，请重试", Toast.LENGTH_SHORT).show();
            }
            //重新打开预览图，进行下一次的拍照准备
            camera.startPreview();
        }
    };
    /**
     * 启动屏幕朝向改变监听函数 用于在屏幕横竖屏切换时改变保存的图片的方向
     */
    private void startOrientationChangeListener() {
        OrientationEventListener orientationEventListener = new OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int rotation) {
                Log.i("lixiang", "onOrientationChanged: "+rotation);
                if ((rotation >= 0 && rotation <= 45) || rotation > 315) {
                    rotation = 0;
                } else if (rotation > 45 && rotation <= 135) {
                    rotation = 90;
                } else if (rotation > 135 && rotation <= 225) {
                    rotation = 180;
                } else if (rotation > 225 && rotation <= 315) {
                    rotation = 270;
                } else {
                    rotation = 0;
                }
                if (rotation == mOrientation) return;
                mOrientation = rotation;
                updateCameraOrientation();
            }
        };
        orientationEventListener.enable();
    }
    /**
     * 根据当前朝向修改保存图片的旋转角度
     */
    private void updateCameraOrientation() {
        try {
            if (mCamera != null && null != mCamera.getParameters()) {
                Camera.Parameters parameters = mCamera.getParameters();
                //rotation参数为 0、90、180、270。水平方向为0。
                int rotation = 90 + mOrientation == 360 ? 0 : 90 + mOrientation;
                //前置摄像头需要对垂直方向做变换，否则照片是颠倒的
                if (mCurrCameraFacing == 1) {
                    if (rotation == 90) rotation = 270;
                    else if (rotation == 270) rotation = 90;
                }
                List<String> modes = parameters.getSupportedFocusModes();
                if (modes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                    //支持自动聚焦模式
                    parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                }
                parameters.setRotation(rotation);//生成的图片转90°
                //预览图片旋转90°
                mCamera.setDisplayOrientation(90);//预览转90°
                mCamera.setParameters(parameters);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    /**
     * 设置相机参数
     */
    private void setCameraParameters() {
        if (null != mCamera) {
            Camera.Parameters params = mCamera.getParameters();
//            Camera.Size preViewSize = getOptimalSize(params.getSupportedPreviewSizes(), mWidthPixel, mHeightPixel);
//            if (null != preViewSize) {
//                params.setPreviewSize(preViewSize.width, preViewSize.height);
//            }
//
//            Camera.Size pictureSize = getOptimalSize(params.getSupportedPictureSizes(), mWidthPixel, mHeightPixel);
//            if (null != pictureSize) {
//                params.setPictureSize(pictureSize.width, pictureSize.height);
//            }
            //设置图片格式
            params.setPictureFormat(ImageFormat.JPEG);
            params.setJpegQuality(100);
            params.setJpegThumbnailQuality(100);

            List<String> modes = params.getSupportedFocusModes();
            List<String> flashModes = params.getSupportedFlashModes();
            if (modes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                //支持自动聚焦模式
                params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            }
            if (null!=flashModes&&flashModes.contains(Camera.Parameters.FLASH_MODE_AUTO)) {
                params.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
            }
            mCamera.setParameters(params);
            //开启屏幕朝向监听
            startOrientationChangeListener();
        }

    }
    /**
     * 获取最优尺寸
     * @param sizes
     * @param w
     * @param h
     * @return
     */
    private Camera.Size getOptimalSize(List<Camera.Size> sizes, int w, int h) {
        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio = (double) h / w;
        if (w > h)
            targetRatio = (double) w / h;
        if (sizes == null)
            return null;
        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;
        int targetHeight = h;
        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (size.height >= size.width)
                ratio = (float) size.height / size.width;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE)
                continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
                Log.d("lixiang", "getOptimalSize: width:" + size.width + " height:" + size.height + " minDiff:" + minDiff);
            }
        }

        return optimalSize;
    }
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.i("lixiang", "surfaceCreated: ");
        try {
            if (mCamera == null) {
                openCamera();
            }
            if (null != mCamera) {
                mCamera.setPreviewDisplay(mSurfaceHolder);
                mCamera.startPreview();
                setCameraParameters();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "打开相机失败", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        mWidthPixel = width;
        mHeightPixel = height;
        updateCameraOrientation();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        releaseCamera();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    @Override
    public void onPermissionsGranted(int requestCode, List<String> perms) {
        init();
        Toast.makeText(this, "用户授权成功", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onPermissionsDenied(int requestCode, List<String> perms) {
        Toast.makeText(this, "用户授权失败", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        finish();
    }
}
