package com.ihunuo.tzyplayer.surfaceviews;

import android.content.Context;
import android.opengl.GLES20;

import com.ihunuo.tzyplayer.R;
import com.ihunuo.tzyplayer.units.ShaderUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * 作者:tzy on 2/25/21.
 * 邮箱:215475174@qq.com
 * 功能介绍:xxx
 */
public class NormlFobRender {
    private final String TAG = "CCC-NormlFobRender";

    private Context context;

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

    private FloatBuffer vertexBuffer;
    private FloatBuffer textureBuffer;

    private int program;
    private int av_Position;
    private int af_Position;

    private int vboId;

    public NormlFobRender(Context context) {
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

    public void onCreate() {
        String vertexSource = ShaderUtil.getRawResource(context, R.raw.vertex_shader_screen_h264);
        String fragmentSource = ShaderUtil.getRawResource(context, R.raw.fragment_shader_screen_h264);
        program = ShaderUtil.createProgram(vertexSource, fragmentSource);

        av_Position = GLES20.glGetAttribLocation(program, "av_Position");
        af_Position = GLES20.glGetAttribLocation(program, "af_Position");

        //创建vbo
        int[] vbos = new int[1];
        GLES20.glGenBuffers(1, vbos, 0);
        vboId = vbos[0];

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertexData.length * 4 + textureData.length * 4, null, GLES20.GL_STATIC_DRAW);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, vertexData.length * 4, vertexBuffer);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, vertexData.length * 4, textureData.length * 4, textureBuffer);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    public void onChange(int width, int height) {
        GLES20.glViewport(0, 0, width, height);
    }

    public void onDraw(int textureId) {
//        Log.d(TAG, "textureId: " + textureId);
//        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);
//        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);

        GLES20.glUseProgram(program);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);

        GLES20.glEnableVertexAttribArray(av_Position);
        GLES20.glVertexAttribPointer(av_Position, 2, GLES20.GL_FLOAT, false,
                8, 0);
        GLES20.glEnableVertexAttribArray(af_Position);
        GLES20.glVertexAttribPointer(af_Position, 2, GLES20.GL_FLOAT, false,
                8, vertexData.length * 4);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

//        if (BaseHnGlSurfaceView.chMediaEncodec != null) {
//            BaseHnGlSurfaceView.chMediaEncodec.requestRender();
//        }
    }
}
