package com.ihunuo.tzyplayer.decode;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import com.ihunuo.tzyplayer.lisrener.Decodelister;
import com.ihunuo.tzyplayer.units.ImageUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 作者:tzy on 2020-06-12.
 * 邮箱:215475174@qq.com
 * 功能介绍:YUV编解码器Android使用的是yuv420p也是使用mediacode解码成yuv类型的数据可以不用设置
 * SurfaceView
 */
public class MediaCodePlayYuv {
    private static int mCount = 0;
    private final static int HEAD_OFFSET = 512;
    // Video Constants
    private final static String MIME_TYPE = "video/avc"; // H.264 Advanced Video
    private final static int VIDEO_WIDTH = 1280;
    private final static int VIDEO_HEIGHT = 720;
    private final static int TIME_INTERNAL = 50;

    public MediaCodec mCodec;
    public Bitmap bt_codec;
    public boolean photo_codec_flag = false, isDaTouDie = false;
    int bitrate = 1920 * 1080*4;
    int framerate = 20;
    private Queue mediaDecodeQueue = new ConcurrentLinkedQueue();//缓存队列
    public int yuvType = 0, width = 0, height = 0;

    public Decodelister decodelister;
    private boolean isFirstPlayVideo = true;
    //插值操作只在录像时候进行
    private boolean isinvoid  =false;
    public  boolean issppred =false;
    public MediaCodePlayYuv(Decodelister mydecodelister) {
        this.decodelister = mydecodelister;
    }


    //    这是初始化解码器操作，具体要设置解码类型，高度，宽度，还有一个用于显示视频的surface。
    public void initDecoder() {
        try {
            mCodec = MediaCodec.createDecoderByType(MIME_TYPE);
        } catch (IOException e) {
            e.printStackTrace();
        }
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE, VIDEO_WIDTH, VIDEO_HEIGHT);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar);
        mCodec.configure(mediaFormat, null, null, 0);//mSurfaceView.getHolder().getSurface()
        mCodec.start();
    }

    public boolean onFrame(byte[] buf, int offset, int length) {
//        Log.d("ggg", "onFrame start:" + photo_codec_flag);
//        Log.d("Media", "onFrame Thread:" + Thread.currentThread().getId());
        // Get input buffer index
        if (mCodec!=null) {
            ByteBuffer[] inputBuffers = mCodec.getInputBuffers();
            int inputBufferIndex = mCodec.dequeueInputBuffer(-1);

//        Log.d("Media", "onFrame index:" + inputBufferIndex);
            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                inputBuffer.clear();
                inputBuffer.put(buf, offset, length);
                mCodec.queueInputBuffer(inputBufferIndex, 0, length, TIME_INTERNAL, 0);
            } else {
                return false;
            }
//        if (photo_codec_flag) {
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            int outputBufferIndex = mCodec.dequeueOutputBuffer(bufferInfo, 0);
//        Log.d("ggg", "onFrame: while1111111111111111: " + outputBufferIndex);

            yuvtobt(outputBufferIndex, bufferInfo.size);
            while (outputBufferIndex >= 0) {
                if (isFirstPlayVideo) {
                    decodelister.firstDecode();
                    isFirstPlayVideo = false;
                }

                mCodec.releaseOutputBuffer(outputBufferIndex, true);
                outputBufferIndex = mCodec.dequeueOutputBuffer(bufferInfo, 0);
//            Log.d("ggg", "onFrame: while0000000000000: " + outputBufferIndex);
                yuvtobt(outputBufferIndex, bufferInfo.size);
            }
        }

//        Log.d("Media", "onFrame end");
//        }
        return true;
    }


    public void yuvtobt(final int outputBufferIndex, int size) {
        if (outputBufferIndex >= 0) {
//            Log.d("ggg", "photo onFrame: bufferInfo.size" + size);
//            Log.d("aaa", "yuvtobt: h:"+image.getHeight() +" w:"+image.getWidth());
//            Log.d("aaa", "yuvtobt: " + mCodec.getOutputFormat().getInteger("color-format"));

            MediaFormat mediaFormat = mCodec.getOutputFormat();
            yuvType = mediaFormat.getInteger("color-format");
            width = mediaFormat.getInteger("width");
            height = mediaFormat.getInteger("height");
            if (true) {
                ByteBuffer byteBuffer = mCodec.getOutputBuffer(outputBufferIndex);
                byte[] yuv = new byte[size];
                byteBuffer.get(yuv, 0, size);

                if (mCodec.getOutputFormat().getInteger("color-format") >= 21) {
                    yuv = NV12ToYuv420P(yuv, width, height);
                }
                decodelister.decodeYUV(yuv, width, height);
                if (photo_codec_flag) {


                    int pictruewidth = width;
                    int pictrueheight = height;
                    int[] argb = I420toARGB(yuv, width, height);




                    Bitmap bitmap = Bitmap.createBitmap(argb, width, height, Bitmap.Config.ARGB_8888);
                    Bitmap  newbitmap = Bitmap.createScaledBitmap(bitmap, pictruewidth, pictrueheight, true);

                    if (newbitmap != null) {
                        if (photo_codec_flag) {

                            decodelister.takePhoto(newbitmap);
                            photo_codec_flag = false;
                        }
                    }
                }
            } else {
                Image image = mCodec.getOutputImage(outputBufferIndex);
                height = image.getHeight();
                width = image.getWidth();
                if (height == 1088) height = 1080;
                byte[] yuvBuf = ImageUtils.getDataFromImage(image, ImageUtils.COLOR_FormatI420);
                decodelister.decodeYUV(yuvBuf, width, height);
                if (photo_codec_flag) {
                    Log.d("aaa", "yuvtobt: 11111");
                    YuvImage yimage = new YuvImage(ImageUtils.getDataFromImage(image, ImageUtils.COLOR_FormatNV21),
                            ImageFormat.NV21, width, height, null);
                    if (yimage != null) {
                        Log.d("aaa", "yuvtobt: 222");
                        ByteArrayOutputStream stream = new ByteArrayOutputStream();
                        yimage.compressToJpeg(new Rect(0, 0, width, height), 100, stream);
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inSampleSize = 2;
                        Log.d("aaa", "yuvtobt: 333");
                        final Bitmap bitmap = BitmapFactory.decodeByteArray(stream.toByteArray(), 0, stream.size(), options);
                        //TODO：此处可以对位图进行处理，如显示，保存等
                        if (bitmap != null) {
                            if (decodelister != null)
                                decodelister.takePhoto(bitmap);
                            photo_codec_flag = false;
                        }

                        try {
                            stream.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                    } else {
                        Log.d("ccc", "yuvtobt: yimage != null && !photo_codec_flag");
                    }

                }
                if (image != null) image.close();
            }

        }
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


    //     YUV420P，Y，U，V三个分量都是平面格式，分为I420和YV12。I420格式和YV12格式的不同处在U平面和V平面的位置不同。
//     在I420格式中，U平面紧跟在Y平面之后，然后才是V平面（即：YUV）；但YV12则是相反（即：YVU）。
//     YUV420SP,Y分量平面格式，UV打包格式,即NV12。 NV12与NV21类似，U 和 V 交错排列,不同在于UV顺序。
//     I420:YYYYYYYY UU VV=>YUV420P
//     YV12:YYYYYYYY VV UU=>YUV420P
//     NV12:YYYYYYYY UVUV=>YUV420SP
//     NV21:YYYYYYYY VUVU=>YUV420SP
    byte[] NV12ToYuv420P(byte[] nv12, int width, int height) {
        byte[] yuv420p = new byte[nv12.length];
        int ySize = width * height;
        int i, j;

        System.arraycopy(nv12, 0, yuv420p, 0, ySize);

        i = 0;
        for (j = 0; j < ySize / 2; j += 2) {
            yuv420p[ySize + i] = nv12[ySize + j];//u
            yuv420p[ySize * 5 / 4 + i] = nv12[ySize + j + 1];//v
            i++;
        }

        return yuv420p;
    }

    public int[] I420toARGB(byte[] yuv, int width, int height) {

        boolean invertHeight = false;
        if (height < 0) {
            height = -height;
            invertHeight = true;
        }

        boolean invertWidth = false;
        if (width < 0) {
            width = -width;
            invertWidth = true;
        }

        int iterations = width * height;
        int[] rgb = new int[iterations];

        for (int i = 0; i < iterations; i++) {
            int nearest = (i / width) / 2 * (width / 2) + (i % width) / 2;

            int y = yuv[i] & 0x000000ff;
            int u = yuv[iterations + nearest] & 0x000000ff;
            int v = yuv[iterations + iterations / 4 + nearest] & 0x000000ff;
            int b = (int) (y + 1.8556 * (u - 128));
            int g = (int) (y - (0.4681 * (v - 128) + 0.1872 * (u - 128)));
            int r = (int) (y + 1.5748 * (v - 128));

            if (b > 255) {
                b = 255;
            } else if (b < 0) {
                b = 0;
            }
            if (g > 255) {
                g = 255;
            } else if (g < 0) {
                g = 0;
            }
            if (r > 255) {
                r = 255;
            } else if (r < 0) {
                r = 0;
            }
            int targetPosition = i;

            if (invertHeight) {
                targetPosition = ((height - 1) - targetPosition / width) * width + (targetPosition % width);
            }
            if (invertWidth) {
                targetPosition = (targetPosition / width) * width + (width - 1) - (targetPosition % width);
            }
            rgb[targetPosition] = (0xff000000) | (0x00ff0000 & r << 16) | (0x0000ff00 & g << 8) | (0x000000ff & b);
        }
        return rgb;

    }
}
