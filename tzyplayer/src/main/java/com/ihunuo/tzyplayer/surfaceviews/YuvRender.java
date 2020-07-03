package com.ihunuo.tzyplayer.surfaceviews;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLException;
import android.opengl.Matrix;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;

import com.ihunuo.tzyplayer.R;
import com.ihunuo.tzyplayer.lisrener.Decodelister;
import com.ihunuo.tzyplayer.units.ShaderUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import javax.microedition.khronos.opengles.GL10;

/**
 * 作者:tzy on 2020-06-12.
 * 邮箱:215475174@qq.com
 * 功能介绍:xxx
 */
public class YuvRender implements YUVSurfaceView.Renderer{

    String TAG = "MyRender";

    public static final int RENDER_YUV = 1;
    public static final int RENDER_MEDIACODEC = 2;
    public int SplitScreen = 1;                     //分屏参数
    public float zoom = 1;                          //缩放视频
    public boolean zoomFlag = false;                //缩放视频
    private boolean isTranslateM;                   //是否平移
    public float[] xyz = new float[3];              //平移 x y z 参数
    public float[] xyzRotate = new float[4];        //旋转 x y z 参数
    public int filter = 1;                          //滤镜参数

    private Context context;
    public boolean isTalkPhoto = false;

    private final float[] vertexData = {
            -1f, -1f,
            1f, -1f,
            -1f, 1f,
            1f, 1f
    };

    private final float[] textureData = {
            0f, 1f,
            1f, 1f,
            0f, 0f,
            1f, 0f
    };

    private float[] matrix = new float[]{
            1, 0, 0, 0,
            0, 1, 0, 0,
            0, 0, 1, 0,
            0, 0, 0, 1};

    private FloatBuffer vertexBuffer;
    private FloatBuffer textureBuffer;
    public int renderType = RENDER_YUV;

    //yuv
    private int program_yuv, avPosition_yuv, afPosition_yuv;

    private int sampler_y, sampler_u, sampler_v, sampler_split_screen, sampler_zoom, umatrix;
    private int sampler_move_x, sampler_move_y, sampler_filter;

    private int[] textureId_yuv;

    private int width_yuv, height_yuv;
    private ByteBuffer y, u, v;

    //mediacodec
    private int program_mediacodec;
    private int avPosition_mediacodec;
    private int afPosition_mediacodec;
    private int samplerOES_mediacodec;
    private int textureId_mediacodec;
    private SurfaceTexture surfaceTexture;
    private Surface surface;

    private OnSurfaceCreateListener onSurfaceCreateListener;
    private OnRenderListener onRenderListener;
    private Decodelister decodelister;

    private int sampler_texelWidth, sampler_texelHeight, uniformConvolutionMatrix;

    private boolean isRotate = false;
    private boolean isAI = false;
    private OnRenderCreateListener onRenderCreateListener;

    public YuvRender(Context context) {
        this.context = context;
        vertexBuffer = ByteBuffer.allocateDirect(vertexData.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(vertexData);
        vertexBuffer.position(0);
        textureBuffer = ByteBuffer.allocateDirect(textureData.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(textureData);
        textureBuffer.position(0);
    }

    public void setRenderType(int renderType) {
        this.renderType = renderType;
    }

    public void setOnSurfaceCreateListener(OnSurfaceCreateListener onSurfaceCreateListener) {
        this.onSurfaceCreateListener = onSurfaceCreateListener;
    }

    public void setOnRenderListener(OnRenderListener onRenderListener) {
        this.onRenderListener = onRenderListener;
    }


    @Override
    public void onSurfaceCreated() {
        if (renderType == RENDER_YUV) {
            initRenderYUV();
        } else if (renderType == RENDER_MEDIACODEC) {
            initRenderMediacodec();
        }
//        GLES20.glClearColor(1.0f, 1.0f, 0.0f, 1.0f);//设置背景色，黄色

    }

    @Override
    public void onSurfaceChanged( int width, int height) {
        widthScreen = width;
        heightScreen = height;
        GLES20.glViewport(0, 0, width, height);
    }


    @Override
    public void onDrawFrame() {
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);

//        if (zoomFlag) {//缩放
//            matrix = new float[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};//每次都是 初始矩阵
//            Matrix.scaleM(matrix, 0, zoom, zoom, zoom);
//            zoomFlag = false;
//        }
        if (isRotate) {//是否旋转
            Matrix.rotateM(matrix, 0, xyzRotate[0], xyzRotate[1], xyzRotate[2], xyzRotate[3]);
            isRotate = false;
        }
        GLES20.glUniformMatrix4fv(umatrix, 1, false, matrix, 0);

        if (renderType == RENDER_YUV) {
            renderYUV();
        } else if (renderType == RENDER_MEDIACODEC) {
//            renderMediacodec(gl);
        }

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        if (isTalkPhoto) {
            Log.d(TAG, "onDrawFrame: cutBitmap");
            Bitmap bitmap = cutBitmap(0, 0, widthScreen, heightScreen);

//            Bitmap bitmap =   readBufferPixelToBitmap(widthScreen,heightScreen);

//            Bitmap bitmap = SavePixels (0, 0, widthScreen, heightScreen,gl);
            if (bitmap != null && decodelister != null) {
                isTalkPhoto = false;
                decodelister.takePhoto(bitmap);
                isAI = false;
            }
        }
    }

//    @Override
//    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
//        if (onRenderListener != null) {
//            onRenderListener.onRender();
//        }
//    }

    private void initRenderYUV() {
        String vertexSource = ShaderUtil.readRawTxt(context, R.raw.vertex_shader_normol);
        String fragmentSource = ShaderUtil.readRawTxt(context, R.raw.fragment_yuv);
        program_yuv = ShaderUtil.createProgram(vertexSource, fragmentSource);

        avPosition_yuv = GLES20.glGetAttribLocation(program_yuv, "av_Position");
        afPosition_yuv = GLES20.glGetAttribLocation(program_yuv, "af_Position");

        sampler_y = GLES20.glGetUniformLocation(program_yuv, "sampler_y");
        sampler_u = GLES20.glGetUniformLocation(program_yuv, "sampler_u");
        sampler_v = GLES20.glGetUniformLocation(program_yuv, "sampler_v");
        sampler_split_screen = GLES20.glGetUniformLocation(program_yuv, "sampler_split_screen");
        sampler_zoom = GLES20.glGetUniformLocation(program_yuv, "sampler_zoom");
        sampler_move_x = GLES20.glGetUniformLocation(program_yuv, "sampler_move_x");
        sampler_move_y = GLES20.glGetUniformLocation(program_yuv, "sampler_move_y");
        umatrix = GLES20.glGetUniformLocation(program_yuv, "u_Matrix");

        sampler_filter = GLES20.glGetUniformLocation(program_yuv, "sampler_filter");
        sampler_texelWidth = GLES20.glGetUniformLocation(program_yuv, "texelWidth");
        sampler_texelHeight = GLES20.glGetUniformLocation(program_yuv, "texelHeight");
        uniformConvolutionMatrix = GLES20.glGetUniformLocation(program_yuv, "convolutionMatrix");

        textureId_yuv = new int[3];
        GLES20.glGenTextures(3, textureId_yuv, 0);

        for (int i = 0; i < 3; i++) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId_yuv[i]);

            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        }
        if (onRenderCreateListener != null) {
            onRenderCreateListener.onCreate(textureId_yuv);
        }
    }

    public void setYUVRenderData(int width, int height, byte[] y, byte[] u, byte[] v) {
        this.width_yuv = width;
        this.height_yuv = height;
        this.y = ByteBuffer.wrap(y);
        this.u = ByteBuffer.wrap(u);
        this.v = ByteBuffer.wrap(v);
    }

    private void renderYUV() {
        if (width_yuv > 0 && height_yuv > 0 && y != null && u != null && v != null) {
            GLES20.glUseProgram(program_yuv);

            GLES20.glEnableVertexAttribArray(avPosition_yuv);
            GLES20.glVertexAttribPointer(avPosition_yuv, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer);

            GLES20.glEnableVertexAttribArray(afPosition_yuv);
            GLES20.glVertexAttribPointer(afPosition_yuv, 2, GLES20.GL_FLOAT, false, 8, textureBuffer);

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId_yuv[0]);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, width_yuv, height_yuv, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, y);

            GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId_yuv[1]);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, width_yuv / 2, height_yuv / 2, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, u);

            GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId_yuv[2]);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, width_yuv / 2, height_yuv / 2, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, v);

            GLES20.glUniform1i(sampler_y, 0);
            GLES20.glUniform1i(sampler_u, 1);
            GLES20.glUniform1i(sampler_v, 2);
            GLES20.glUniform1i(sampler_split_screen, SplitScreen);//分屏参数
            GLES20.glUniform1f(sampler_zoom, zoom);//分屏参数
            GLES20.glUniform1f(sampler_move_x, xyz[0]);//X周平移
            GLES20.glUniform1f(sampler_move_y, xyz[1]);//Y周平移

            GLES20.glUniform1i(sampler_filter, filter);//滤镜参数
            GLES20.glUniform1f(sampler_texelHeight, 1f / heightScreen);
            GLES20.glUniform1f(sampler_texelWidth, 1f / widthScreen);
            float intensity = 1.0f;
            float[] Convolution = new float[]{
                    intensity * (-2.0f), -intensity, 0.0f,
                    -intensity, 1.0f, intensity,
                    0.0f, intensity, intensity * 2.0f,};
            GLES20.glUniformMatrix3fv(uniformConvolutionMatrix, 1, false, Convolution, 0);


            y.clear();
            u.clear();
            v.clear();
            y = null;
            u = null;
            v = null;
//            Log.d(TAG, "renderYUV: ");
        }
    }

    private void initRenderMediacodec() {
        String vertexSource = ShaderUtil.readRawTxt(context, R.raw.vertex_shader_normol);
        String fragmentSource = ShaderUtil.readRawTxt(context, R.raw.fragment_mediacodec);
        program_mediacodec = ShaderUtil.createProgram(vertexSource, fragmentSource);

        avPosition_mediacodec = GLES20.glGetAttribLocation(program_mediacodec, "av_Position");
        afPosition_mediacodec = GLES20.glGetAttribLocation(program_mediacodec, "af_Position");
//        samplerOES_mediacodec = GLES20.glGetUniformLocation(program_mediacodec, "sTexture");

        sampler_split_screen = GLES20.glGetUniformLocation(program_mediacodec, "sampler_split_screen");
        sampler_zoom = GLES20.glGetUniformLocation(program_mediacodec, "sampler_zoom");
        sampler_move_x = GLES20.glGetUniformLocation(program_mediacodec, "sampler_move_x");
        sampler_move_y = GLES20.glGetUniformLocation(program_mediacodec, "sampler_move_y");
        sampler_filter = GLES20.glGetUniformLocation(program_mediacodec, "sampler_filter");
        umatrix = GLES20.glGetUniformLocation(program_mediacodec, "u_Matrix");

        sampler_texelWidth = GLES20.glGetUniformLocation(program_mediacodec, "texelWidth");
        sampler_texelHeight = GLES20.glGetUniformLocation(program_mediacodec, "texelHeight");
        uniformConvolutionMatrix = GLES20.glGetUniformLocation(program_mediacodec, "convolutionMatrix");


        int[] textureids = new int[1];
        GLES20.glGenTextures(1, textureids, 0);
        textureId_mediacodec = textureids[0];

        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        surfaceTexture = new SurfaceTexture(textureId_mediacodec);
        surface = new Surface(surfaceTexture);
//        surfaceTexture.setOnFrameAvailableListener(this);


        if (onSurfaceCreateListener != null) {
            onSurfaceCreateListener.onSurfaceCreate(surface);
        }

    }

    private void renderMediacodec(GL10 gl) {
        surfaceTexture.updateTexImage();
        GLES20.glUseProgram(program_mediacodec);

        GLES20.glEnableVertexAttribArray(avPosition_mediacodec);
        GLES20.glVertexAttribPointer(avPosition_mediacodec, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer);

        GLES20.glEnableVertexAttribArray(afPosition_mediacodec);
        GLES20.glVertexAttribPointer(afPosition_mediacodec, 2, GLES20.GL_FLOAT, false, 8, textureBuffer);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId_mediacodec);
//        GLES20.glUniform1i(samplerOES_mediacodec, 0);


        GLES20.glUniform1i(sampler_split_screen, SplitScreen);//分屏参数
        GLES20.glUniform1f(sampler_zoom, zoom);//分屏参数
        GLES20.glUniform1f(sampler_move_x, xyz[0]);//X周平移
        GLES20.glUniform1f(sampler_move_y, xyz[1]);//Y周平移
        GLES20.glUniform1i(sampler_filter, filter);//滤镜参数

        GLES20.glUniform1f(sampler_texelHeight, 1f / heightScreen);
        GLES20.glUniform1f(sampler_texelWidth, 1f / widthScreen);

        float intensity = 1.0f;
        float[] Convolution = new float[]{
                intensity * (-2.0f), -intensity, 0.0f,
                -intensity, 1.0f, intensity,
                0.0f, intensity, intensity * 2.0f,};
        GLES20.glUniformMatrix3fv(uniformConvolutionMatrix, 1, false, Convolution, 0);

        Log.d(TAG, "renderMediacodec: textureid=" + textureId_mediacodec);

    }

    public int gettextureId_mediacodec() {
        return textureId_mediacodec;
    }


    public interface OnSurfaceCreateListener {
        void onSurfaceCreate(Surface surface);
    }

    public interface OnRenderListener {
        void onRender();
    }

    public void setHNSingleFrameListener(Decodelister mydecodelister) {
        this.decodelister = mydecodelister;
    }

    public int talkphotoW = 1920, talkphotoH = 1080;
    private int widthScreen = 0, heightScreen = 0;

    public void talkPhoto(int w, int h) {
        isTalkPhoto = true;
        talkphotoW = w;
        talkphotoH = h;
    }

    public void talkPhoto(int w, int h, boolean isAI) {
        isTalkPhoto = true;
        this.isAI = isAI;
        talkphotoW = w;
        talkphotoH = h;
    }


    private Bitmap cutBitmap(int x, int y, int w, int h) {
        int bitmapBuffer[] = new int[w * h];
        int bitmapSource[] = new int[w * h];
        IntBuffer intBuffer = IntBuffer.wrap(bitmapBuffer);
        intBuffer.position(0);
        try {
            GLES20.glReadPixels(x, y, w, h, GL10.GL_RGBA, GL10.GL_UNSIGNED_BYTE, intBuffer);
            int offset1, offset2;
            for (int i = 0; i < h; i++) {
                offset1 = i * w;
                offset2 = (h - i - 1) * w;
                for (int j = 0; j < w; j++) {
                    int texturePixel = bitmapBuffer[offset1 + j];
                    int blue = (texturePixel >> 16) & 0xff;
                    int red = (texturePixel << 16) & 0x00ff0000;
                    int pixel = (texturePixel & 0xff00ff00) | red | blue;
                    bitmapSource[offset2 + j] = pixel;
                }
            }
        } catch (GLException e) {
            return null;
        }
        Bitmap bitmap = Bitmap.createBitmap(bitmapSource, w, h, Bitmap.Config.ARGB_8888);
        Bitmap newbitmap = Bitmap.createScaledBitmap(bitmap, talkphotoW, talkphotoH, true);
        intBuffer.clear();
        return newbitmap;
    }

    @NonNull
    private Bitmap readBufferPixelToBitmap(int width, int height) {
        ByteBuffer buf = ByteBuffer.allocateDirect(width * height * 4);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf);
        buf.rewind();

        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bmp.copyPixelsFromBuffer(buf);
        return bmp;
    }




    public void setZoom(float zoom) {
        this.zoom = zoom;
        zoomFlag = true;
    }

    public void setXYZ(float x, float y, float z) {
        this.xyz[0] = x;
        this.xyz[1] = y;
        this.xyz[2] = z;
        isTranslateM = false;
    }

    /**
     * @param rotate  旋转角度 正数：逆时针旋转 负数：顺时针旋转
     * @param rotateX X轴
     * @param rotateY Y轴
     * @param rotateZ Z轴
     */
    public void setRotate(int rotate, int rotateX, int rotateY, int rotateZ) {
        this.xyzRotate[0] = rotate;
        this.xyzRotate[1] = rotateX;
        this.xyzRotate[2] = rotateY;
        this.xyzRotate[3] = rotateZ;
        isRotate = true;
    }


    public void setOnRenderCreateListener(OnRenderCreateListener onRenderCreateListener) {
        this.onRenderCreateListener = onRenderCreateListener;
    }

    public interface OnRenderCreateListener {
        void onCreate(int textid[]);
    }




}

