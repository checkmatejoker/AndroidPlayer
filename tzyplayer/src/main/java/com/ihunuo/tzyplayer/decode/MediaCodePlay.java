package com.ihunuo.tzyplayer.decode;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;
import android.view.SurfaceView;

import com.ihunuo.tzyplayer.lisrener.Decodelister;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * 作者:tzy on 2020-06-12.
 * 邮箱:215475174@qq.com
 * 功能介绍:最简单的编解码器,直接使用原生的mediacode解码h264流需要设置一个SurfaceView
 */
public class MediaCodePlay {

    private static int mCount = 0;
    // Video Constants
    private final static String MIME_TYPE = "video/avc"; // H.264 Advanced Video
    private final static int VIDEO_WIDTH = 1280;
    private final static int VIDEO_HEIGHT = 720;
    private final static int TIME_INTERNAL = 50;

    public MediaCodec mCodec;
    private Decodelister decodelister;
    private boolean isFirstPlayVideo = true;

    public MediaCodePlay(Decodelister myDecodelister){
        this.decodelister = myDecodelister;
    }

    //    这是初始化解码器操作，具体要设置解码类型，高度，宽度，还有一个用于显示视频的surface。
    public void initDecoder(SurfaceView mSurfaceView) {
        try {
            mCodec = MediaCodec.createDecoderByType(MIME_TYPE);
        } catch (IOException e) {
            e.printStackTrace();
        }
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE, VIDEO_WIDTH, VIDEO_HEIGHT);
        //有很多参数可以根据自己的需要设置
        mCodec.configure(mediaFormat, mSurfaceView.getHolder().getSurface(), null, 0);//mSurfaceView.getHolder().getSurface()
        mCodec.start();
    }

    public boolean onFrame(byte[] buf, int offset, int length) {
//        Log.d("ccc", "onFrame start");
//        Log.d("Media", "onFrame Thread:" + Thread.currentThread().getId());
        // Get input buffer index
        ByteBuffer[] inputBuffers = mCodec.getInputBuffers();
        try {
            int inputBufferIndex = mCodec.dequeueInputBuffer(100);

//            Log.d("Media", "onFrame index:" + inputBufferIndex);
            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                inputBuffer.clear();
                inputBuffer.put(buf, offset, length);
                mCodec.queueInputBuffer(inputBufferIndex, 0, length, TIME_INTERNAL, 0);
            } else {
                return false;
            }
            onframe_out();
        } catch (Exception e) {
            Log.e("ccc", e.toString());
            e.printStackTrace();
        }

//        Log.d("Media", "onFrame end");
        return true;
    }

    public void onframe_out() {
        // Get output buffer index
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int outputBufferIndex = mCodec.dequeueOutputBuffer(bufferInfo, 100);
//        Log.d("ccc", "onFrame: while1111111111111111: " + outputBufferIndex);

        while (outputBufferIndex >= 0) {
            if (isFirstPlayVideo) {
                decodelister.firstDecode();
                isFirstPlayVideo = false;
            }
            mCodec.releaseOutputBuffer(outputBufferIndex, true);
            outputBufferIndex = mCodec.dequeueOutputBuffer(bufferInfo, 50);
//            Log.d("ccc", "onFrame: while0000000000000: " + outputBufferIndex);

        }

    }

    public void release() {
        try {
            if (mCodec != null) {
                mCodec.stop();
                mCodec.release();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        isFirstPlayVideo = true;
    }
}
