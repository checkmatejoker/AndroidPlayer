package com.ihunuo.tzyplayer.surfaceviews;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.AttributeSet;

/**
 * 作者:tzy on 2020-06-12.
 * 邮箱:215475174@qq.com
 * 功能介绍:xxx
 */
public class JPGSurfaceView extends EGLSurfaceView {
    public ImgVideoRender imgVideoRender;
    private int fbotextureid;

    public JPGSurfaceView(Context context) {
        this(context, null);
    }

    public JPGSurfaceView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public JPGSurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        imgVideoRender = new ImgVideoRender(context);
        setRender(imgVideoRender);
        setRenderMode(EGLSurfaceView.RENDERMODE_WHEN_DIRTY);
        imgVideoRender.setOnRenderCreateListener(new ImgVideoRender.OnRenderCreateListener() {
            @Override
            public void onCreate(int textid) {
                fbotextureid = textid;
            }
        });
    }

    public void setCurrentImg(int imgsr) {
        if (imgVideoRender != null) {
            imgVideoRender.setCurrentImgSrc(imgsr);
            requestRender();
        }
    }

    public void setCurrentBitmap(Bitmap bitmap) {
        if (imgVideoRender != null) {
            imgVideoRender.setCurrentBitmap(bitmap);
            requestRender();
            setRenderMode(EGLSurfaceView.RENDERMODE_CONTINUOUSLY);
        }
    }

    public void setRGB(byte[] rgb, int width, int height) {
        if (imgVideoRender != null) {
            imgVideoRender.setCurrentRGB(rgb, width, height);
            requestRender();
            setRenderMode(EGLSurfaceView.RENDERMODE_CONTINUOUSLY);
        }
    }

    public int getFbotextureid() {
        return fbotextureid;
    }

    public void setRotate(int rotate, int rotateX, int rotateY, int rotateZ) {
        imgVideoRender.setRotate(rotate, rotateX, rotateY, rotateZ);
    }

    public void setIsVR(boolean var) {
        imgVideoRender.setIsVR(var);
    }

    public boolean getIsVR() {
        return imgVideoRender.isVR;
    }


    public ImgVideoRender getHsnRender() {
        return imgVideoRender;
    }
}
