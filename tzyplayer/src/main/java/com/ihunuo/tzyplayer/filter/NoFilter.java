package com.ihunuo.tzyplayer.filter;

import android.content.res.Resources;
import android.opengl.GLES20;

import com.ihunuo.tzyplayer.filter.AFilter;

/**
 * 作者:tzy on 2020-06-12.
 * 邮箱:215475174@qq.com
 * 功能介绍:xxx
 */
public class NoFilter extends AFilter {

    public NoFilter(Resources res) {
        super(res);
    }

    @Override
    protected void onCreate() {
        createProgramByAssetsFile("shader/base_vertex.sh",
                "shader/base_fragment.sh");
    }

    @Override
    protected void onSizeChanged(int width, int height) {

    }

    //取消绑定Texture
    public void unBindFrame() {
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    }
}