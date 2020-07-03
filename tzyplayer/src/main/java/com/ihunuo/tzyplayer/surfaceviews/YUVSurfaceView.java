package com.ihunuo.tzyplayer.surfaceviews;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.lang.ref.WeakReference;

import javax.microedition.khronos.egl.EGLContext;

/**
 * 作者:tzy on 2020-06-12.
 * 邮箱:215475174@qq.com
 * 功能介绍:xxx
 */
public class YUVSurfaceView extends SurfaceView implements SurfaceHolder.Callback {

    private YuvRender mRenderer;
    private EGLThread mEGLThread;
    private Surface mSurface;
    private EGLContext mEglContext;


    public final static int RENDERMODE_WHEN_DIRTY = 0;
    public final static int RENDERMODE_CONTINUOUSLY = 1;
    private  int mRenderMode = RENDERMODE_CONTINUOUSLY;

    public int[] getFbotextureid() {
        return fbotextureid;
    }

    public void setFbotextureid(int[] fbotextureid) {
        this.fbotextureid = fbotextureid;
    }

    private int fbotextureid[];

    public YUVSurfaceView(Context context) {
        this(context, null);
    }

    public YUVSurfaceView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public YUVSurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mRenderer = new YuvRender(context);
        setRenderMode(EGLSurfaceView.RENDERMODE_WHEN_DIRTY);
        mRenderer.setOnRenderCreateListener(new YuvRender.OnRenderCreateListener() {
            @Override
            public void onCreate(int[] textid) {
                fbotextureid = textid;
            }
        });
        init();
    }

    private void init() {

        SurfaceHolder holder = getHolder();
        holder.addCallback(this);
    }


    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (mSurface == null) {
            mSurface = holder.getSurface();
        }
        mEGLThread = new EGLThread(new WeakReference<>(this));
        mEGLThread.isCreate = true;
        mEGLThread.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        mEGLThread.width = width;
        mEGLThread.height = height;
        mEGLThread.isChange = true;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mEGLThread.onDestroy();
        mEGLThread = null;
        mSurface = null;
        mEglContext = null;
    }
    public void setYUVData(byte[] yuvBuf, int width, int height) {
        int size_y = width * height;
        int size_u_v = width * height * 3 / 2 - size_y;
        byte[] Y = new byte[size_y];
        byte[] U = new byte[size_y / 4];
        byte[] V = new byte[size_y / 4];

        System.arraycopy(yuvBuf, 0, Y, 0, size_y);
        System.arraycopy(yuvBuf, size_y, U, 0, size_u_v / 2);
        System.arraycopy(yuvBuf, size_y + size_y / 4, V, 0, size_u_v / 2);


        if (mRenderer != null) {
            mRenderer.setYUVRenderData(width, height, Y, U, V);
            requestRender();
        }

    }


    public void setRenderer(YuvRender mRenderer) {
        this.mRenderer = mRenderer;
    }

    public void setRenderMode(int renderMode) {
        if (mRenderer == null) {
            throw new RuntimeException("must set render before");
        }
        this.mRenderMode =renderMode;
    }

    public void requestRender() {
        if (mEGLThread != null) {
            mEGLThread.requestRender();
        }
    }

    public void setSurfaceAndEglContext(Surface surface, EGLContext eglContext) {
        this.mSurface = surface;
        this.mEglContext = eglContext;
    }

    public EGLContext getEglContext() {
        if (mEGLThread != null) {
            return mEGLThread.getEglContext();
        }
        return null;
    }


    private static class EGLThread extends Thread {


        EGLThread(WeakReference<YUVSurfaceView> eGLSurfaceViewWeakRef) {
            this.mEGLSurfaceViewWeakRef = eGLSurfaceViewWeakRef;
        }

        @Override
        public void run() {
            super.run();
            try {
                guardedRun();
            } catch (Exception e) {
                // fall thru and exit normally
            }
        }

        private void guardedRun() throws InterruptedException {
            isExit = false;
            isStart = false;
            object = new Object();
            mEglHelper = new EglHelper();
            mEglHelper.initEgl(mEGLSurfaceViewWeakRef.get().mSurface, mEGLSurfaceViewWeakRef.get().mEglContext);

            while (true) {
                if (isExit) {
                    //释放资源
                    release();
                    break;
                }

                if (isStart) {
                    if (mEGLSurfaceViewWeakRef.get().mRenderMode == RENDERMODE_WHEN_DIRTY) {
                        synchronized (object) {
                            object.wait();
                        }
                    } else if (mEGLSurfaceViewWeakRef.get().mRenderMode == RENDERMODE_CONTINUOUSLY) {
                        Thread.sleep(1000 / 60);
                    } else {
                        throw new IllegalArgumentException("renderMode");
                    }
                }

                onCreate();
                onChange(width, height);
                onDraw();
                isStart = true;
            }

        }

        private void onCreate() {
            if (!isCreate || mEGLSurfaceViewWeakRef.get().mRenderer == null)
                return;

            isCreate = false;
            mEGLSurfaceViewWeakRef.get().mRenderer.onSurfaceCreated();
        }

        private void onChange(int width, int height) {
            if (!isChange || mEGLSurfaceViewWeakRef.get().mRenderer == null)
                return;

            isChange = false;
            mEGLSurfaceViewWeakRef.get().mRenderer.onSurfaceChanged(width, height);
        }

        private void onDraw() {
            if (mEGLSurfaceViewWeakRef.get().mRenderer == null)
                return;

            mEGLSurfaceViewWeakRef.get().mRenderer.onDrawFrame();
            //第一次的时候手动调用一次 不然不会显示ui
            if (!isStart) {
                mEGLSurfaceViewWeakRef.get().mRenderer.onDrawFrame();
            }

            mEglHelper.swapBuffers();
        }

        void requestRender() {
            if (object != null) {
                synchronized (object) {
                    object.notifyAll();
                }
            }
        }

        void onDestroy() {
            isExit = true;
            //释放锁
            requestRender();
        }

        void release() {
            if (mEglHelper != null) {
                mEglHelper.destoryEgl();
                mEglHelper = null;
                object = null;
                mEGLSurfaceViewWeakRef = null;
            }
        }

        EGLContext getEglContext() {
            if (mEglHelper != null) {
                return mEglHelper.getmEglContext();
            }
            return null;
        }

        private WeakReference<YUVSurfaceView> mEGLSurfaceViewWeakRef;
        private EglHelper mEglHelper;

        private int width;
        private int height;

        private boolean isCreate;
        private boolean isChange;
        private boolean isStart;
        private boolean isExit;

        private Object object;
    }


    interface Renderer {
        void onSurfaceCreated();

        void onSurfaceChanged(int width, int height);

        void onDrawFrame();

    }
}

