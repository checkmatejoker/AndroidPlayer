package com.ihunuo.tzyplayer.decode;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.view.Surface;

import com.ihunuo.tzyplayer.lisrener.Decodelister;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * 作者:tzy on 1/27/21.
 * 邮箱:215475174@qq.com
 * 功能介绍:xxx
 */
public class MediaCodeSurface {
    // Video Constants
    private final static String MIME_TYPE = "video/avc"; // H.264 Advanced Video
    private final static int VIDEO_WIDTH = 1280;
    private final static int VIDEO_HEIGHT = 720;
    private final static int TIME_INTERNAL = 0;

    public MediaCodec mCodec;

    public boolean isFirstPlayVideo = true;

    private Decodelister decodelister;

    //    这是初始化解码器操作，具体要设置解码类型，高度，宽度，还有一个用于显示视频的surface。
    public void initDecoder(Surface surface,Decodelister mydecodelister) {
        try {
            decodelister = mydecodelister;
            mCodec = MediaCodec.createDecoderByType(MIME_TYPE);
            MediaFormat mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE, VIDEO_WIDTH, VIDEO_HEIGHT);
//            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);//比特率
            mCodec.configure(mediaFormat, surface, null, 0);//MediaCodec.CRYPTO_MODE_UNENCRYPTED
            mCodec.start();
//            Log.e("ccc", "initDecoder: 4");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean onFrame(byte[] buf, int offset, int length) {
//        Log.d("解码器数据", CommonUtils.bytesToHexStringTopTen(buf) + "");
//        Log.e("ccc", "initDecoder: 5");
        try {
            if (mCodec != null) {
//                Log.e("ccc", "initDecoder:   6");
                ByteBuffer[] inputBuffers = mCodec.getInputBuffers();
                int inputBufferIndex = mCodec.dequeueInputBuffer(-1);

//                Log.d("CCC-onFrame", "index:" + inputBufferIndex);
                if (inputBufferIndex >= 0) {
                    ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                    inputBuffer.clear();
                    inputBuffer.put(buf, offset, length);
                    mCodec.queueInputBuffer(inputBufferIndex, 0, length, TIME_INTERNAL, 0);
                } else {
                    return false;
                }

                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                int outputBufferIndex = mCodec.dequeueOutputBuffer(bufferInfo, 0);
//                Log.d("CCC", "解码器outputBufferIndex: " + outputBufferIndex);
                while (outputBufferIndex >= 0) {

                    if (isFirstPlayVideo) {
                        isFirstPlayVideo = false;
                        if (decodelister!=null)
                        {
                            decodelister.firstDecode();
                        }

                    }

                    mCodec.releaseOutputBuffer(outputBufferIndex, true);
                    outputBufferIndex = mCodec.dequeueOutputBuffer(bufferInfo, 0);

                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }

    public void release() {
        try {
            if (mCodec != null) {
                mCodec.stop();
                mCodec.release();
                mCodec = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public int getVideoWidth() {
        return mCodec.getOutputFormat().getInteger("width");
    }

    public int getVideoHeight() {
        return mCodec.getOutputFormat().getInteger("height");
    }

    public void setOnDecodeListener(Decodelister onDecodeListener) {
        this.decodelister = onDecodeListener;
    }
}
