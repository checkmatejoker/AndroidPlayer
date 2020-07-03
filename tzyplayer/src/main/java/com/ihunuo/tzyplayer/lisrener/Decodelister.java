package com.ihunuo.tzyplayer.lisrener;

import android.graphics.Bitmap;

/**
 * 作者:tzy on 2020-06-12.
 * 邮箱:215475174@qq.com
 * 功能介绍:xxx
 */
public interface Decodelister {
    public void firstDecode();
    public void decodeYUV(byte yuv[],int width,int hieght);
    public void takePhoto(Bitmap bmp);
    public void redata(byte data [],int len);
}
