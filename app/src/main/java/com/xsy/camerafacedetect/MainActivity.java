package com.xsy.camerafacedetect;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.pm.ActivityInfo;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Camera;
import android.hardware.Camera.Face;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.CameraInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;

import com.android.internal.util.AngleHelper;

import java.io.IOException;

public class MainActivity extends AppCompatActivity implements Camera.PreviewCallback, SurfaceHolder.Callback{
    private static final String TAG = MainActivity.class.getSimpleName();

    private Camera mCamera = null;
    private Parameters mPparameters =null;
    private SurfaceView mSurfaceView;
    private int mNumberOfCamera =0;
    private RectF mRect = new RectF();
    private Matrix mMatrix = new Matrix();
    private int mWidth = 0;
    private int mHeight = 0;
    private MainHandler mHandler;
    private int cur_point = 100; //舵机旋转的当前角度
    private int orientation = 1; //1:代表向下转动，-1：代表向上转动
    private boolean isFindFace = false;
    private final int EVENT_NOTDETECT_FACE = 0;
    private final int EVENT_DETECT_FACE = 1;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG,"onCreate");
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);//横屏
        requestWindowFeature(Window.FEATURE_NO_TITLE);//去标题

        setContentView(R.layout.activity_main);
        initView();

        if(Build.VERSION.SDK_INT >= 23) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, 1);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        //initCamera();
        Log.d(TAG,"onStart");
        initAngle();
        initCamera();

        Rect outSize = new Rect();
        getWindowManager().getDefaultDisplay().getRectSize(outSize);
        int left = outSize.left;
        int top = outSize.top;
        int right = outSize.right;
        int bottom = outSize.bottom;
        mWidth = right - left;
        mHeight = bottom - top;
        Log.d(TAG, "left = " + left + ",top = " + top + ",right = " + right + ",bottom = " + bottom);
        mHandler = new MainHandler();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG,"onDestroy");
        if(mCamera != null){
            mCamera.stopFaceDetection();
            mCamera.stopPreview();
            mCamera.setPreviewCallback(null);
            mCamera.setPreviewCallbackWithBuffer(null);
            mCamera.release();
            mCamera = null;
        }
    }

    private void initView()
    {
        Log.d(TAG,"initView");
        mSurfaceView = (SurfaceView)findViewById(R.id.camera);
        mSurfaceView.getHolder().addCallback(this);
    }


    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        Log.d(TAG,"onPreviewFrame");
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.d(TAG,"surfaceCreated");
        initCamera();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.d(TAG,"surfaceChanged");
        if(mSurfaceView.getHolder().getSurface() == null){
            return;
        }
        //停止人脸识别
        mCamera.stopFaceDetection();
        mCamera.stopPreview();

        mCamera.startPreview();
        mCamera.startFaceDetection();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d(TAG,"surfaceDestroyed");
    }

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {
        Log.d(TAG,"onPointerCaptureChanged");
    }

    private void initCamera()
    {
        int cameraId = -1;
        mNumberOfCamera = Camera.getNumberOfCameras();
        CameraInfo cameraInfo = new CameraInfo();
        Log.d(TAG,"mNumberOfCamera:"+mNumberOfCamera);
        for(int i =0; i < mNumberOfCamera; i++){
            Camera.getCameraInfo(i,cameraInfo);
            Log.d(TAG,"facing:"+cameraInfo.facing);
            if(cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT){
                if(i == 1){
                    cameraId = i;
                }
            }
        }

        if(cameraId == -1){
            cameraId = 0;
        }
        Log.d(TAG,"cameraId:"+cameraId);

        mCamera = Camera.open(0);

        //给照相机设置参数
        mPparameters = mCamera.getParameters();
        //设置照片的格式
        mPparameters.setPreviewFormat(PixelFormat.YCbCr_420_SP);
        //设置照片的质量
        mPparameters.setJpegQuality(80);

        mPparameters.setPictureFormat(PixelFormat.JPEG);

        mPparameters.setPreviewSize(1280, 720);
        mCamera.setParameters(mPparameters);
        try {

            mCamera.setPreviewDisplay(mSurfaceView.getHolder());
            mCamera.setPreviewCallbackWithBuffer(this);
            //设置人脸识别监听接口
            mCamera.setFaceDetectionListener(new CFaceDetectionListener());
            mCamera.startPreview();
            //启动人脸识别
            mCamera.startFaceDetection();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    class CFaceDetectionListener implements Camera.FaceDetectionListener{

        @Override
        public void onFaceDetection(Face[] faces, Camera camera) {
            Log.d(TAG,"onFaceDetection");
            if(faces == null || faces.length == 0){
                Log.d(TAG,"onFaceDetection not detect face!!!");

                //mHandler.removeMessages(EVENT_NOTDETECT_FACE);
                Message m = mHandler.obtainMessage();
                m.what = EVENT_NOTDETECT_FACE;
                m.obj = faces;

                if(isFindFace == true){
                    mHandler.sendMessageDelayed(m,15000);
                }else{
                    mHandler.removeMessages(EVENT_NOTDETECT_FACE);
                    mHandler.sendMessage(m);
                }
            }else{
                Log.d(TAG, "onFaceDetection detect face:"+faces.length);

                mHandler.removeMessages(EVENT_NOTDETECT_FACE);
                Message m = mHandler.obtainMessage();
                m.what = EVENT_DETECT_FACE;
                m.obj = faces;
                m.sendToTarget();
            }
        }
    }

    private void seekFaceAngle()
    {
        if(cur_point >= 135 && orientation == 1){
            orientation = -1;
        }else if(cur_point <= 45 && orientation == -1){
            orientation = 1;
        }
        cur_point += (orientation) * 2;
        AngleHelper.angle(cur_point);
        try {
            Thread.currentThread().sleep(200);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    class MainHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            Face[] faces = (Face[]) msg.obj;
            Log.d(TAG, "handleMessage msg.what:"+msg.what);
            switch(msg.what){
                case EVENT_NOTDETECT_FACE:
                    processAngle(0,0);
                    isFindFace = false;

                    break;
                case EVENT_DETECT_FACE:
                    isFindFace = true;
                    doDetectFaceProcess(faces);
                    break;

            }
            // TODO Auto-generated method stub

            super.handleMessage(msg);
        }
    }

    private void initAngle()
    {
        AngleHelper.init();
        AngleHelper.angle(cur_point);
    }

    private void processAngle(int mintop,int maxbtm)
    {
        int first_level = mHeight / 4;
        int second_level = (mHeight * 3) / 4;
        int mid_level = mHeight / 2;

        int mid_point = (mintop + maxbtm) / 2;

        if(mid_point > first_level && mid_point < second_level){
            Log.d(TAG,"mid_point:"+mid_point);
            if(isFindFace == true){
                orientation *= -1;
            }
            if(mid_point < mid_level){
                int len = mid_level - mid_point;
                Log.d(TAG,"len:"+len+" before cur_point:"+cur_point);
                int angle =len * 10 / mHeight;
                cur_point -= angle;
                Log.d(TAG,"len:"+len+" after cur_point:"+cur_point);
                AngleHelper.angle(cur_point);
                try {
                    mHandler.removeMessages(EVENT_DETECT_FACE);
                    Thread.currentThread().sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }else{
                int len = mid_point - mid_level;
                Log.d(TAG,"len:"+len+" before cur_point:"+cur_point);
                int angle =len *10 / mHeight;

                cur_point += angle;
                Log.d(TAG,"len:"+len+" after cur_point:"+cur_point);
                AngleHelper.angle(cur_point);
                try {
                    mHandler.removeMessages(EVENT_DETECT_FACE);
                    Thread.currentThread().sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }else{
            if(cur_point >= 135 && orientation == 1){
                orientation = -1;
            }else if(cur_point <= 45 && orientation == -1){
                orientation = 1;
            }
            cur_point += (orientation) * 1;
            AngleHelper.angle(cur_point);
            try {
                Thread.currentThread().sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void doDetectFaceProcess(Face[] faces)
    {
        boolean isMirror = false;

        prepareMatrix(mMatrix, isMirror, 0, mWidth, mHeight);
        mMatrix.postRotate(0); //Matrix.postRotate默认是顺时针
        int mMintop = mHeight /2;
        int mMaxbottom = mHeight /2;

        for (int i = 0; i < faces.length; i++) {
            int left = faces[i].rect.left;
            int top = faces[i].rect.top;
            int right = faces[i].rect.right;
            int bottom = faces[i].rect.bottom;
            Log.d(TAG, "face[" + i + "]" + " = [" + left + "," + top + "," + right + "," + bottom + "]");

            mRect.set(faces[i].rect);
            mMatrix.mapRect(mRect);

            int mleft = Math.round(mRect.left);
            int mtop = Math.round(mRect.top);
            int mright = Math.round(mRect.right);
            int mbottom = Math.round(mRect.bottom);
            Log.d(TAG, "After Map face[" + i + "]" + " = [" + mleft + "," + mtop + "," + mright + "," + mbottom + "]");
            if(mMintop > mtop){
                mMintop = mtop;
            }
            if(mMaxbottom < mbottom){
                mMaxbottom = mbottom;
            }
        }
        processAngle(mMintop,mMaxbottom);
    }

    public static void prepareMatrix(Matrix matrix, boolean mirror, int displayOrientation,
                                     int viewWidth, int viewHeight) {
        matrix.setScale(mirror ? -1 : 1, 1);
        matrix.postRotate(displayOrientation);
        matrix.postScale(viewWidth / 2000f, viewHeight / 2000f);
        matrix.postTranslate(viewWidth / 2f, viewHeight / 2f);
    }
}
