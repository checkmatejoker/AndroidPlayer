package com.ihunuo.tzyplayer.surfaceviews;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLException;
import android.opengl.Matrix;
import android.os.Environment;
import android.util.Log;

import com.ihunuo.tzyplayer.R;
import com.ihunuo.tzyplayer.filter.AFilter;
import com.ihunuo.tzyplayer.filter.Beauty;
import com.ihunuo.tzyplayer.filter.GroupFilter;
import com.ihunuo.tzyplayer.filter.LookupFilter;
import com.ihunuo.tzyplayer.filter.NoFilter;
import com.ihunuo.tzyplayer.units.BitmapUtils;
import com.ihunuo.tzyplayer.units.MatrixUtils;
import com.ihunuo.tzyplayer.units.ShaderUtil;
import com.ihunuo.tzyplayer.units.UIUtils;

import java.io.File;
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
public class ImgVideoRender implements EGLSurfaceView.GLRender {

    private final GroupFilter mGroupFilter;
    private AFilter mShowFilter;

    private Context context;
    private float[] vertexData = {
            -1f, -1f,
            1f, -1f,
            -1f, 1f,
            1f, 1f,

            -1f, -1f,
            -0f, -1f,
            -1f, 1f,
            0f, 1f,

            0f, -1f,
            1f, -1f,
            0f, 1f,
            1f, 1f,
    };
    private FloatBuffer vertexBuffer;
    private float[] fragmentData = {
            0f, 0f,
            1f, 0f,
            0f, 1f,
            1f, 1f
    };
    private FloatBuffer fragmentBuffer;

    private int program;
    private int vPosition;
    private int fPosition;
    private int textureid;
    private int sampler;

    private int vboId;
    private int fboId;
    private int umatrix;
    private float[] matrix = new float[16];

    public static int SplitScreen = 1;              //分屏参数
    public float zoom = 1;                          //缩放视频
    public boolean zoomFlag = false;                //缩放视频
    private boolean isTranslateM;                   //是否平移
    public float[] xyz_move = new float[3];              //平移 x y z 参数
    public int filter = 1;                          //滤镜参数

    private int imgTextureId;

    private OnRenderCreateListener onRenderCreateListener;

    private ImgFboRender ImgFboRender;

    private int renderMode = 0;//0-资源 1-bitmap 2-rgb
    private int srcImg = 0;
    private Bitmap bitmap;
    private byte[] rgb;
    private int widthRGB = 0, heightRGB = 0;
    private int widthScreen = 0, heightScreen = 0;

    boolean isRotate = false, isVR = false;
    int xyz[] = new int[4];

    public boolean isTalkPhoto = false, isTalkPhotoSave = false, isTaikPhotoPush = true, isTaikPhotoDaTouTie = false;//拍照
    public static int isTalkPhotoSize = 0; // 0-不插值 1-vga 2-720 3-1080 4-2k 5-4K
    private int talkphotoW, talkphotoH;
    public Bitmap bitmapDaTouTie;
    public String talkPhotoPath = "";
    //    private HNMjpegListener hnMjpegListener;
    private int sampler_move_x, sampler_move_y, sampler_zoom, sampler_singleStepOffset, sampler_params, sampler_brightness;
    private boolean isOnSurfaceChanged = false;


    public ImgVideoRender(Context context) {
        this.context = context;

        ImgFboRender = new ImgFboRender(context);
        vertexBuffer = ByteBuffer.allocateDirect(vertexData.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(vertexData);
        vertexBuffer.position(0);

        fragmentBuffer = ByteBuffer.allocateDirect(fragmentData.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(fragmentData);
        fragmentBuffer.position(0);

        mShowFilter = new NoFilter(context.getResources());
        mGroupFilter = new GroupFilter(context.getResources());

        LookupFilter mLookupFilter = new LookupFilter(context.getResources());
        mLookupFilter.setMaskImage("lookup/purity.png");
        mLookupFilter.setIntensity(100 / 100f);
        mGroupFilter.addFilter(mLookupFilter);
        Beauty mBeautyFilter = new Beauty(context.getResources());
        mBeautyFilter.setFlag(100 / 20 + 1);
        mGroupFilter.addFilter(mBeautyFilter);
    }

    public void setOnRenderCreateListener(OnRenderCreateListener onRenderCreateListener) {
        this.onRenderCreateListener = onRenderCreateListener;
    }

    @Override
    public void onSurfaceCreated() {
        ImgFboRender.onCreate();
        String vertexSource = ShaderUtil.getRawResource(context, R.raw.vertex_shader_opengl2);
        String fragmentSource = ShaderUtil.getRawResource(context, R.raw.fragment_shader_screen);

        program = ShaderUtil.createProgram(vertexSource, fragmentSource);

        vPosition = GLES20.glGetAttribLocation(program, "v_Position");
        fPosition = GLES20.glGetAttribLocation(program, "f_Position");
        sampler = GLES20.glGetUniformLocation(program, "sTexture");
        umatrix = GLES20.glGetUniformLocation(program, "u_Matrix");

        sampler_zoom = GLES20.glGetUniformLocation(program, "sampler_zoom");
        sampler_move_x = GLES20.glGetUniformLocation(program, "sampler_move_x");
        sampler_move_y = GLES20.glGetUniformLocation(program, "sampler_move_y");
        sampler_singleStepOffset = GLES20.glGetUniformLocation(program, "singleStepOffset");
        sampler_params = GLES20.glGetUniformLocation(program, "params");
        sampler_brightness = GLES20.glGetUniformLocation(program, "brightness");

        GLES20.glUniform1i(sampler, 0);

        mGroupFilter.create();
        mShowFilter.create();

    }

    private int mShowType = MatrixUtils.TYPE_CENTERCROP;          //输出到屏幕上的方式
    private float[] SM = new float[16];                           //用于绘制到屏幕上的变换矩阵

    @Override
    public void onSurfaceChanged(int width, int height) {
        widthScreen = width;
        heightScreen = height;

        mShowFilter.setSize(width, height);
        mGroupFilter.setSize(width, height);

//        MatrixUtils.getMatrix(SM, mShowType, width, height, width, height);
//        mShowFilter.setSize(width, height);
//        mShowFilter.setMatrix(SM);
//        mGroupFilter.setSize(width, height);
//        mShowFilter.setSize(width, height);

//        if (filter != 2) {
        int[] vbos = new int[1];
        GLES20.glGenBuffers(1, vbos, 0);
        vboId = vbos[0];

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertexData.length * 4 + fragmentData.length * 4, null, GLES20.GL_STATIC_DRAW);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, vertexData.length * 4, vertexBuffer);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, vertexData.length * 4, fragmentData.length * 4, fragmentBuffer);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        int[] fbos = new int[1];
        GLES20.glGenBuffers(1, fbos, 0);
        fboId = fbos[0];
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId);


        int[] textureIds = new int[1];
        GLES20.glGenTextures(1, textureIds, 0);
        textureid = textureIds[0];

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureid);

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, textureid, 0);
        if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            Log.e("ywl5320", "fbo wrong");
        } else {
            Log.e("ywl5320", "fbo success" + width + " " + height);
        }

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        if (onRenderCreateListener != null) {
            onRenderCreateListener.onCreate(textureid);
        }

        GLES20.glViewport(0, 0, width, height);
        Matrix.orthoM(matrix, 0, -1f, 1f, -1f, 1f, -1f, 1f);

        ImgFboRender.onChange(width, height);
//        }

    }

    @Override
    public void onDrawFrame() {

        imgTextureId = loadTexrute();
//        Log.d("ywl5320", "id is : " + imgTextureId);

        if (filter == 100) {
            mGroupFilter.setTextureId(imgTextureId);
            mGroupFilter.draw();

            mShowFilter.setTextureId(mGroupFilter.getOutputTexture());
            mShowFilter.draw();

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
            int[] ids = new int[]{imgTextureId};
            GLES20.glDeleteTextures(1, ids, 0);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        } else {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId);

            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            GLES20.glClearColor(1f, 0f, 0f, 1f);

            GLES20.glUseProgram(program);
            if (isRotate) {//是否旋转
                Matrix.rotateM(matrix, 0, xyz[0], xyz[1], xyz[2], xyz[3]);
                isRotate = false;
            }
            GLES20.glUniformMatrix4fv(umatrix, 1, false, matrix, 0);
            GLES20.glUniform1f(sampler_zoom, zoom);//分屏参数
            GLES20.glUniform1f(sampler_move_x, xyz_move[0]);//X周平移
            GLES20.glUniform1f(sampler_move_y, xyz_move[1]);//Y周平移


            float beauty = 0.6f, saturate = 0.6f, bright = 0.6f;
            float[] params = getParams(beauty, saturate);
            GLES20.glUniform4f(sampler_params, params[0], params[1], params[2], params[3]);
            GLES20.glUniform1f(sampler_brightness, getBright(bright));
            float[] singleStepOffset = getSingleStepOffset((float) widthScreen, (float) heightScreen);
            GLES20.glUniform2f(sampler_singleStepOffset, singleStepOffset[0], singleStepOffset[1]);


            if (isVR) {
                //第一次绘制
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, imgTextureId);
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);
                GLES20.glEnableVertexAttribArray(vPosition);
                GLES20.glVertexAttribPointer(vPosition, 2, GLES20.GL_FLOAT, false, 8, 32);
                GLES20.glEnableVertexAttribArray(fPosition);
                GLES20.glVertexAttribPointer(fPosition, 2, GLES20.GL_FLOAT, false, 8, vertexData.length * 4);
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
                //第二次绘制
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, imgTextureId);
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);
                GLES20.glEnableVertexAttribArray(vPosition);
                GLES20.glVertexAttribPointer(vPosition, 2, GLES20.GL_FLOAT, false, 8, 64);
                GLES20.glEnableVertexAttribArray(fPosition);
                GLES20.glVertexAttribPointer(fPosition, 2, GLES20.GL_FLOAT, false, 8, vertexData.length * 4);
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

            } else {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, imgTextureId);

                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);

                GLES20.glEnableVertexAttribArray(vPosition);
                GLES20.glVertexAttribPointer(vPosition, 2, GLES20.GL_FLOAT, false, 8, 0);

                GLES20.glEnableVertexAttribArray(fPosition);
                GLES20.glVertexAttribPointer(fPosition, 2, GLES20.GL_FLOAT, false, 8, vertexData.length * 4);

                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            }

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

            int[] ids = new int[]{imgTextureId};
            GLES20.glDeleteTextures(1, ids, 0);

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            ImgFboRender.onDraw(textureid);
        }

        if (isTalkPhoto) { //拍照
            talk(talkPhotoPath);
        }
    }

    private int loadTexrute() {
        if (renderMode == 0) {
            return ShaderUtil.loadTexrute(srcImg, context);
        } else if (renderMode == 1) {
            return ShaderUtil.loadTexruteBitmap(bitmap, context);
        } else if (renderMode == 2) {
            return ShaderUtil.loadRGBTexture(rgb, widthRGB, heightRGB);
        }
        return 0;
    }


    public interface OnRenderCreateListener {
        void onCreate(int textid);
    }

    public void setCurrentImgSrc(int src) {
        renderMode = 0;
        srcImg = src;
    }

    public void setCurrentBitmap(Bitmap bitmap) {
        renderMode = 1;
        this.bitmap = bitmap;
    }

    public void setCurrentRGB(byte[] rgb, int width, int height) {
        renderMode = 2;
        this.rgb = rgb;
        this.widthRGB = width;
        this.heightRGB = height;
    }

    /**
     * @param rotate  旋转角度 正数：逆时针旋转 负数：顺时针旋转
     * @param rotateX X轴
     * @param rotateY Y轴
     * @param rotateZ Z轴
     */
    public void setRotate(int rotate, int rotateX, int rotateY, int rotateZ) {
        this.xyz[0] = rotate;
        this.xyz[1] = rotateX;
        this.xyz[2] = rotateY;
        this.xyz[3] = rotateZ;
        isRotate = true;
    }

    public void setIsVR(boolean var) {
        this.isVR = var;
    }

    public void talkPhoto(boolean var, String path,  int w, int h) {
        Log.d("ccc", "talkPhoto: " + w + " " + h);
        isTalkPhoto = true;
        isTalkPhotoSave = var;
        talkphotoW = w;
        talkphotoH = h;
        talkPhotoPath = path;
//        this.hnMjpegListener = hnMjpegListener;
    }

    private void talk(String path) {
        Bitmap bitmap = cutBitmap(0, 0, widthScreen, heightScreen);

        String name = System.currentTimeMillis() + "";
        Bitmap newbitmap = null;
        if (isTalkPhotoSize == 6 && bitmap.getWidth() == 1920) {
            int width = 0, height = 0;
            if (bitmap.getWidth() == 1280) {
                width = bitmap.getWidth();
                height = bitmap.getHeight();
            } else if (bitmap.getWidth() == 1920) {
                width = 4096;
                height = 2160;
            }
            newbitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);
        } else {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            if (isTalkPhotoSize == 1) {
                width = 640;
                height = 480;
                newbitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);
            } else if (isTalkPhotoSize == 2) {
                width = 1280;
                height = 720;
                newbitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);
            } else if (isTalkPhotoSize == 3) {
                width = 1920;
                height = 1080;
                newbitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);
            } else if (isTalkPhotoSize == 4) {
                width = 4096;
                height = 2160;
                newbitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);
            } else {
                newbitmap = bitmap;
            }


        }
        if (isTaikPhotoDaTouTie && bitmapDaTouTie != null && !bitmapDaTouTie.isRecycled()) {
            newbitmap = BitmapUtils.mergeBitmap(newbitmap, bitmapDaTouTie);
        }
        if (isTalkPhotoSave) {
            UIUtils.bitmaptofile(newbitmap, name, path);
        } else {
//            hnMjpegListener.takePhotoSuccssed(newbitmap);
        }
        if (isTaikPhotoPush) {
            String newPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera/";
            UIUtils.bitmaptofile(newbitmap, name, newPath);
            File f = new File(newPath + name + ".jpeg");
            UIUtils.scanIntoMediaStore(context, f);//通知相册更新
        }
        isTalkPhoto = false;
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

    public void setZoom(float zoom) {
        this.zoom = zoom;
        zoomFlag = true;
    }

    public void setXYZMove(float x, float y, float z) {
        this.xyz_move[0] = x;
        this.xyz_move[1] = y;
        this.xyz_move[2] = z;
        isTranslateM = false;
    }


    private float[] getParams(float beauty, float saturate) {
        float[] value = new float[4];
        value[0] = 1.6f - 1.2f * beauty;
        value[1] = 1.3f - 0.6f * beauty;
        value[2] = -0.2f + 0.6f * saturate;
        value[3] = -0.2f + 0.6f * saturate;
        return value;
    }

    float getBright(float bright) {
        return 0.6f * (-0.5f + bright);
    }

    float[] getSingleStepOffset(float width, float height) {
        float[] value = new float[2];
        value[0] = 2.0f / width;
        value[1] = 2.0f / height;
        return value;
    }
}
