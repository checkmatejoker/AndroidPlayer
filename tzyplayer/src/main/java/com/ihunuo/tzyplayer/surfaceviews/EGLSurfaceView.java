package com.ihunuo.tzyplayer.surfaceviews;

import android.content.Context;
import android.graphics.Bitmap;
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
public class EGLSurfaceView extends SurfaceView implements SurfaceHolder.Callback {
    private Surface surface;
    private EGLContext eglContext;

    private EGLThread eGLThread;
    private GLRender gLRender;

    public final static int RENDERMODE_WHEN_DIRTY = 0;
    public final static int RENDERMODE_CONTINUOUSLY = 1;

    public int mRenderMode = RENDERMODE_CONTINUOUSLY;


    public EGLSurfaceView(Context context) {
        this(context, null);
    }

    public EGLSurfaceView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public EGLSurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        getHolder().addCallback(this);
    }

    public void setRender(GLRender wlGLRender) {
        this.gLRender = wlGLRender;
    }

    public void setRenderMode(int mRenderMode) {

        if (gLRender == null) {
            throw new RuntimeException("must set render before");
        }
        this.mRenderMode = mRenderMode;
    }

    public void setSurfaceAndEglContext(Surface surface, EGLContext eglContext) {
        this.surface = surface;
        this.eglContext = eglContext;
    }

    public EGLContext getEglContext() {
        if (eGLThread != null) {
            return eGLThread.getEglContext();
        }
        return null;
    }

    public void requestRender() {
        if (eGLThread != null) {
            eGLThread.requestRender();
        }
    }


    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (surface == null) {
            surface = holder.getSurface();
        }
        eGLThread = new EGLThread(new WeakReference<EGLSurfaceView>(this));
        eGLThread.isCreate = true;
        eGLThread.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        eGLThread.width = width;
        eGLThread.height = height;
        eGLThread.isChange = true;

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        eGLThread.onDestory();
        eGLThread = null;
        surface = null;
        eglContext = null;
    }

    public interface GLRender {
        void onSurfaceCreated();

        void onSurfaceChanged(int width, int height);

        void onDrawFrame();
    }


    static class EGLThread extends Thread {

        private WeakReference<EGLSurfaceView> eGLSurfaceViewWeakReference;
        private EglHelper eglHelper = null;
        private Object object = null;

        private boolean isExit = false;
        private boolean isCreate = false;
        private boolean isChange = false;
        private boolean isStart = false;

        private int width;
        private int height;

        public EGLThread(WeakReference<EGLSurfaceView> hsnEGLSurfaceViewWeakReference) {
            this.eGLSurfaceViewWeakReference = hsnEGLSurfaceViewWeakReference;
        }

        @Override
        public void run() {
            super.run();
            isExit = false;
            isStart = false;
            object = new Object();
            eglHelper = new EglHelper();
            eglHelper.initEgl(eGLSurfaceViewWeakReference.get().surface, eGLSurfaceViewWeakReference.get().eglContext);

            while (true) {
                if (isExit) {
                    //释放资源
                    release();
                    break;
                }

                if (isStart) {
                    if (eGLSurfaceViewWeakReference.get().mRenderMode == RENDERMODE_WHEN_DIRTY) {
                        synchronized (object) {
                            try {
                                object.wait();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    } else if (eGLSurfaceViewWeakReference.get().mRenderMode == RENDERMODE_CONTINUOUSLY) {
                        try {
                            Thread.sleep(1000 / 25);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    } else {
                        throw new RuntimeException("mRenderMode is wrong value");
                    }
                }


                onCreate();
                onChange(width, height);
                onDraw();

                isStart = true;


            }


        }

        private void onCreate() {
            if (isCreate && eGLSurfaceViewWeakReference.get().gLRender != null) {
                isCreate = false;
                eGLSurfaceViewWeakReference.get().gLRender.onSurfaceCreated();
            }
        }

        private void onChange(int width, int height) {
            if (isChange && eGLSurfaceViewWeakReference.get().gLRender != null) {
                isChange = false;
                eGLSurfaceViewWeakReference.get().gLRender.onSurfaceChanged(width, height);
            }
        }

        private void onDraw() {
            if (eGLSurfaceViewWeakReference.get().gLRender != null && eglHelper != null) {
                eGLSurfaceViewWeakReference.get().gLRender.onDrawFrame();
                if (!isStart) {
                    eGLSurfaceViewWeakReference.get().gLRender.onDrawFrame();
                }
                eglHelper.swapBuffers();

            }
        }

        private void requestRender() {
            if (object != null) {
                synchronized (object) {
                    object.notifyAll();
                }
            }
        }

        public void onDestory() {
            isExit = true;
            requestRender();
        }


        public void release() {
            if (eglHelper != null) {
                eglHelper.destoryEgl();
                eglHelper = null;
                object = null;
                eGLSurfaceViewWeakReference = null;
            }
        }

        public EGLContext getEglContext() {
            if (eglHelper != null) {
                return eglHelper.getmEglContext();
            }
            return null;
        }

    }

}
