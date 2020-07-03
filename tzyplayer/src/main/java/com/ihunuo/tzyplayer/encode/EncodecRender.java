package com.ihunuo.tzyplayer.encode;

import android.content.Context;
import android.opengl.GLES20;

import com.ihunuo.tzyplayer.R;
import com.ihunuo.tzyplayer.surfaceviews.EGLSurfaceView;
import com.ihunuo.tzyplayer.units.ShaderUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * 作者:tzy on 2020-06-12.
 * 邮箱:215475174@qq.com
 * 功能介绍:xxx
 */
public class EncodecRender implements EGLSurfaceView.GLRender {

    private Context context;

    private float[] vertexData = {
            -1f, -1f,
            1f, -1f,
            -1f, 1f,
            1f, 1f
    };
    private FloatBuffer vertexBuffer;

    private float[] fragmentData = {
            0f, 1f,
            1f, 1f,
            0f, 0f,
            1f, 0f
    };
    private FloatBuffer fragmentBuffer;
    //midcode
    private int program;
    private int vPosition;
    private int fPosition;
    private int textureid;

    //yuv

    //yuv
    private int program_yuv;
    private int avPosition_yuv;
    private int afPosition_yuv;

    private int sampler_y;
    private int sampler_u;
    private int sampler_v;
    private int[] textureId_yuv;

    private int width_yuv;
    private int height_yuv;
    private ByteBuffer y;
    private ByteBuffer u;
    private ByteBuffer v;




    private int vboId;

    boolean isyuv = false;

    public EncodecRender(Context context, int textureid) {
        this.context = context;
        this.textureid = textureid;

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

    }
    public EncodecRender(Context context, int textureid[],boolean isyuv) {
        this.context = context;
        this.textureId_yuv = textureid;
        this.isyuv =isyuv;

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

    }

    @Override
    public void onSurfaceCreated() {

        if (isyuv)
        {
            String vertexSource = ShaderUtil.readRawTxt(context, R.raw.vertex_shader_normol);
            String fragmentSource = ShaderUtil.readRawTxt(context, R.raw.fragment_yuv);
            program_yuv = ShaderUtil.createProgram(vertexSource, fragmentSource);

            avPosition_yuv = GLES20.glGetAttribLocation(program_yuv, "av_Position");
            afPosition_yuv = GLES20.glGetAttribLocation(program_yuv, "af_Position");

            sampler_y = GLES20.glGetUniformLocation(program_yuv, "sampler_y");
            sampler_u = GLES20.glGetUniformLocation(program_yuv, "sampler_u");
            sampler_v = GLES20.glGetUniformLocation(program_yuv, "sampler_v");

            for (int i = 0; i < 3; i++) {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId_yuv[i]);

                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            }

        }

        else
        {
            String vertexSource = ShaderUtil.getRawResource(context, R.raw.vertex_shader_screen);
            String fragmentSource = ShaderUtil.getRawResource(context, R.raw.fragment_shader_screen);


            program = ShaderUtil.createProgram(vertexSource, fragmentSource);

            vPosition = GLES20.glGetAttribLocation(program, "v_Position");//gl
            fPosition = GLES20.glGetAttribLocation(program, "f_Position");//ft
            int [] vbos = new int[1];
            GLES20.glGenBuffers(1, vbos, 0);
            vboId = vbos[0];
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertexData.length * 4 + fragmentData.length * 4, null, GLES20. GL_STATIC_DRAW);
            GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, vertexData.length * 4, vertexBuffer);
            GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, vertexData.length * 4, fragmentData.length * 4, fragmentBuffer);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        }



    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame() {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glClearColor(0f,0f, 0f, 1f);

        GLES20.glUseProgram(program);


        if (isyuv)
        {
            GLES20.glUseProgram(program_yuv);

            GLES20.glEnableVertexAttribArray(avPosition_yuv);
            GLES20.glVertexAttribPointer(avPosition_yuv, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer);

            GLES20.glEnableVertexAttribArray(afPosition_yuv);
            GLES20.glVertexAttribPointer(afPosition_yuv, 2, GLES20.GL_FLOAT, false, 8, fragmentBuffer);

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId_yuv[0]);

            GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId_yuv[1]);

            GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId_yuv[2]);

            GLES20.glUniform1i(sampler_y, 0);
            GLES20.glUniform1i(sampler_u, 1);
            GLES20.glUniform1i(sampler_v, 2);

        }
        else
        {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureid);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);

            GLES20.glEnableVertexAttribArray(vPosition);
            GLES20.glVertexAttribPointer(vPosition, 2, GLES20.GL_FLOAT, false, 8,
                    0);

            GLES20.glEnableVertexAttribArray(fPosition);
            GLES20.glVertexAttribPointer(fPosition, 2, GLES20.GL_FLOAT, false, 8,
                    vertexData.length * 4);


        }
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);





    }
}
