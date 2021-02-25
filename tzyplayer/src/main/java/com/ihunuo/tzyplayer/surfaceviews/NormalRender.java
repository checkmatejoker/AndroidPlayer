package com.ihunuo.tzyplayer.surfaceviews;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLException;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;

import com.ihunuo.tzyplayer.R;
import com.ihunuo.tzyplayer.surfaceviews.EGLSurfaceView;
import com.ihunuo.tzyplayer.units.ImageUtils;
import com.ihunuo.tzyplayer.units.ShaderUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import javax.microedition.khronos.opengles.GL10;

public class NormalRender implements EGLSurfaceView.GLRender, SurfaceTexture.OnFrameAvailableListener {

    private final String TAG = "NormalRender";
    private final Context context;

    public int SplitScreen = 1;                     //分屏参数
    private int sampler_split_screen;

    private boolean isTalkPhoto = false;
    private boolean isAI;
    private Bitmap stickerBitmap;

    private final float[] vertexData = {
            -1f, -1f,
            1f, -1f,
            -1f, 1f,
            1f, 1f
    };

    private final float[] textureData = {
            0f, 0f,
            1f, 0f,
            0f, 1f,
            1f, 1f
    };

    private final FloatBuffer vertexBuffer;
    private final FloatBuffer textureBuffer;

    //MediaCodec
    private int program;
    private int avPosition;
    private int afPosition;
    private int textureId;
    private SurfaceTexture surfaceTexture;
    private Surface surface;

    private OnSurfaceCreateListener onSurfaceCreateListener;
    private OnRenderListener onRenderListener;

    public int takePhotoWidth = 1920, takePhotoHeight = 1080;
    private int widthScreen = 0, heightScreen = 0;

    private int vboId;
    private int fboId;
    private int fboTextureId;

    private final NormlFobRender FboRender;
    private final int screenWidth;
    private final int screenHeight;


    private long startTime;
    private long endTime;

    private OnRenderCreateListener onRenderCreateListener;

    public NormalRender(Context context) {
        this.context = context;
        WindowManager wm = (WindowManager) context
                .getSystemService(Context.WINDOW_SERVICE);
        int width = wm.getDefaultDisplay().getWidth();
        int height = wm.getDefaultDisplay().getHeight();
        screenWidth = width;
        screenHeight = height;

        FboRender = new NormlFobRender(context);

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

    public void setOnSurfaceCreateListener(OnSurfaceCreateListener onSurfaceCreateListener) {
        this.onSurfaceCreateListener = onSurfaceCreateListener;
    }

    public void setOnRenderListener(OnRenderListener onRenderListener) {
        this.onRenderListener = onRenderListener;
    }

    public void setOnRenderCreateListener(OnRenderCreateListener onRenderCreateListener) {
        this.onRenderCreateListener = onRenderCreateListener;
    }

    @Override
    public void onSurfaceCreated() {
        Log.d(TAG, "onSurfaceCreated: ");

        FboRender.onCreate();

        String vertexSource = ShaderUtil.getRawResource(context, R.raw.vertex_shader_opengl2);
        String fragmentSource = ShaderUtil.getRawResource(context, R.raw.fragment_shader_screen);
        program = ShaderUtil.createProgram(vertexSource, fragmentSource);

        avPosition = GLES20.glGetAttribLocation(program, "av_Position");
        afPosition = GLES20.glGetAttribLocation(program, "af_Position");

        sampler_split_screen = GLES20.glGetUniformLocation(program, "sampler_split_screen");


        int[] vbos = new int[1];
        GLES20.glGenBuffers(1, vbos, 0);
        vboId = vbos[0];
        //绑定vbo
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);

        //分配内存空间
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertexData.length * 4 + textureData.length * 4, null, GLES20.GL_STATIC_DRAW);
        //赋值（把顶点坐标的数据缓存到GPU内存空间）
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, vertexData.length * 4, vertexBuffer);
        //赋值（把纹理坐标的数据缓存到GPU内存空间）
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, vertexData.length * 4, textureData.length * 4, textureBuffer);
        //解绑 vbo
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        //创建fbo
        int[] fbos = new int[1];
        GLES20.glGenBuffers(1, fbos, 0);
        //绑定fbo
        fboId = fbos[0];
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId);

        //创建纹理
        int[] textureIds = new int[1];
        GLES20.glGenTextures(1, textureIds, 0);
        //绑定纹理
        fboTextureId = textureIds[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTextureId);

        //设置环绕方式（超出纹理坐标范围）
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);
        //设置过滤方式（纹理像素映射到坐标点）
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        //设置fbo分配内存大小
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, screenWidth, screenHeight,
                0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        //将纹理绑定到fbo
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, fboTextureId, 0);
        //检查fbo绑定是否成功
        if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            Log.e("ywl5320", "fbo wrong");
        } else {
            Log.e("ywl5320", "fbo success");
        }
        //解除纹理绑定
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        //解除fbo绑定
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        int[] textureids = new int[1];
        GLES20.glGenTextures(1, textureids, 0);
        textureId = textureids[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);

        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        surfaceTexture = new SurfaceTexture(textureId);
        surface = new Surface(surfaceTexture);
        surfaceTexture.setOnFrameAvailableListener(this);

        if (onSurfaceCreateListener != null) {
            onSurfaceCreateListener.onSurfaceCreate(surface);
        }
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);

        if (onRenderCreateListener != null) {
            onRenderCreateListener.onCreate(fboTextureId);
        }
    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        Log.d(TAG, "onSurfaceChanged: " + width + "*" + height);
        widthScreen = width;
        heightScreen = height;

        GLES20.glViewport(0, 0, widthScreen, heightScreen);
        FboRender.onChange(widthScreen, heightScreen);
    }

    @Override
    public void onDrawFrame() {
//        Log.d(TAG, "onDrawFrame: ");
        //绑定fbo
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId);
        //绑定vbo
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);
        GLES20.glUseProgram(program);
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);

        surfaceTexture.updateTexImage();

//        //使顶点属性可用(设置为可用的状态)
        GLES20.glEnableVertexAttribArray(avPosition);
//        //对顶点赋值
//        //size 指定每个顶点属性的组件数量。必须为1、2、3或者4。初始值为4。（如position是由3个（x,y,z）组成，而颜色是4个（r,g,b,a））
//        //stride 指定连续顶点属性之间的偏移量。如果为0，那么顶点属性会被理解为：它们是紧密排列在一起的。初始值为0。
//        //size 2 代表(x,y)，stride 8 代表跨度 （2个点为一组，2个float有8个字节）
        GLES20.glVertexAttribPointer(avPosition, 2, GLES20.GL_FLOAT, false, 8, 0);
        GLES20.glEnableVertexAttribArray(afPosition);
        GLES20.glVertexAttribPointer(afPosition, 2, GLES20.GL_FLOAT, false, 8, vertexData.length * 4);

        GLES20.glUniform1i(sampler_split_screen, SplitScreen);//分屏参数


        //绘制到fboTexture 上面
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        //解绑vbo
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        //解绑纹理
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
        //解绑fbo
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        FboRender.onDraw(fboTextureId);

        if (isTalkPhoto) {
            isTalkPhoto = false;
            Log.d(TAG, "onDrawFrame: cutBitmap");
            Bitmap bitmap = cutBitmap(widthScreen, heightScreen);

//                String name = DateUtils.getCurDate("yyyyMMddHHmmss") + ".jpeg";
//                Bitmap destBitmap = Bitmap.createScaledBitmap(bitmap, takePhotoWidth, takePhotoHeight, true);
//
//                if (stickerBitmap != null) {
//                    //大头贴
//                    destBitmap = ImageUtils.mergeBitmap(destBitmap, stickerBitmap);
//                    stickerBitmap = null;
//                }
//
//                // 保存图片到APP指定路径
//                ImageUtils.bitmapToFile(destBitmap, name, AppData.Path_Photo_Video_Save);
//                // 保存图片到手机系统相册
//                ImageUtils.saveBitmapToSystemAlbum(destBitmap, name, context);

        }
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        if (onRenderListener != null) {
//            Log.d(TAG, "onFrameAvailable: ");
            onRenderListener.onRender();
        }
    }

    public int getTextureId() {
        return fboTextureId;
    }

    public interface OnSurfaceCreateListener {
        void onSurfaceCreate(Surface surface);
    }

    public interface OnRenderListener {
        void onRender();
    }

    public void talkPhoto(int width, int height, boolean isAI, Bitmap stickerBitmap) {
        isTalkPhoto = true;
        takePhotoWidth = width;
        takePhotoHeight = height;
        this.isAI = isAI;
        this.stickerBitmap = stickerBitmap;
    }

    private Bitmap cutBitmap(int w, int h) {
//        startTime = System.currentTimeMillis();
        int[] bitmapBuffer = new int[w * h];
        int[] bitmapSource = new int[w * h];
        IntBuffer intBuffer = IntBuffer.wrap(bitmapBuffer);
        intBuffer.position(0);
        try {
            GLES20.glReadPixels(0, 0, w, h, GL10.GL_RGBA, GL10.GL_UNSIGNED_BYTE, intBuffer);
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
//        Bitmap newBitmap = Bitmap.createScaledBitmap(bitmap, takePhotoWidth, takePhotoHeight, true);
        intBuffer.clear();
//        return newBitmap;
//        endTime = System.currentTimeMillis();
//        Log.d(TAG, "duration: " + (endTime - startTime) + "ms");
        return bitmap;
    }


    public interface OnRenderCreateListener {
        void onCreate(int fboId);
    }
}
