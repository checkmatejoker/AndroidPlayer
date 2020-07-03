package com.ihunuo.tzyplayer.encode;

import android.content.Context;

/**
 * 作者:tzy on 2020-06-12.
 * 邮箱:215475174@qq.com
 * 功能介绍:xxx
 */
public class UserMediaEncodec extends BaseMediaEncoder {

    private EncodecRender mEncodecRender;

    public UserMediaEncodec(Context context, int textureId) {
        super(context);
        mEncodecRender = new EncodecRender(context, textureId);
        setRender(mEncodecRender);
        setmRenderMode(BaseMediaEncoder.RENDERMODE_CONTINUOUSLY);
    }

    public UserMediaEncodec(Context context, int textureId[]) {
        super(context);
        mEncodecRender = new EncodecRender(context, textureId,true);
        setRender(mEncodecRender);
        setmRenderMode(BaseMediaEncoder.RENDERMODE_CONTINUOUSLY);
    }
}