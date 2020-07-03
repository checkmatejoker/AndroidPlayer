package com.ihunuo.tzyplayer.encode;

import android.content.Context;
import android.content.Intent;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;

import com.ihunuo.tzyplayer.surfaceviews.EGLSurfaceView;
import com.ihunuo.tzyplayer.surfaceviews.EglHelper;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;

import javax.microedition.khronos.egl.EGLContext;

/**
 * 作者:tzy on 2020-06-12.
 * 邮箱:215475174@qq.com
 * 功能介绍:基础编码类用于编码导出视频原理是读取共享上下文的缓存的h264流
 */
public abstract class BaseMediaEncoder {

    private final Context context;
    private Surface surface;
    private EGLContext eglContext;

    private int width;
    private int height;

    private MediaCodec videoEncodec;
    private MediaFormat videoFormat;
    private MediaCodec.BufferInfo videoBufferinfo;

    private MediaCodec audioEncodec;
    private MediaFormat audioFormat;
    private MediaCodec.BufferInfo audioBufferinfo;
    private long audioPts = 0;
    private int sampleRate;

    private MediaMuxer mediaMuxer;
    private boolean encodecStart;
    private boolean audioExit;
    private boolean videoExit;

    private EGLMediaThread mEGLMediaThread;
    private VideoEncodecThread videoEncodecThread;
    private AudioEncodecThread audioEncodecThread;


    private EGLSurfaceView.GLRender mGLRender;

    public final static int RENDERMODE_WHEN_DIRTY = 0;
    public final static int RENDERMODE_CONTINUOUSLY = 1;
    private int mRenderMode = RENDERMODE_CONTINUOUSLY;

    private OnMediaInfoListener onMediaInfoListener;
    private boolean isRecodeAAC = false;
    private static final int RECODE_FRAME_RATE = 25;
    private String savePath = Environment.getExternalStorageDirectory() + "/HYFPV/abc.h264";


    public BaseMediaEncoder(Context context) {
        this.context = context;
    }

    public void setRender(EGLSurfaceView.GLRender myGLRender) {
        this.mGLRender = myGLRender;
    }

    public void setmRenderMode(int mRenderMode) {
        if (mGLRender == null) {
            throw new RuntimeException("must set render before");
        }
        this.mRenderMode = mRenderMode;
    }

    public void setOnMediaInfoListener(OnMediaInfoListener onMediaInfoListener) {
        this.onMediaInfoListener = onMediaInfoListener;
    }

    public void initEncodec(EGLContext eglContext, String savePath, int width, int height,
                            int sampleRate, int channelCount, boolean isRecodeAAC) {
        this.width = width;
        this.height = height;
        this.eglContext = eglContext;
        this.isRecodeAAC = isRecodeAAC;
        this.savePath = savePath;
        initMediaEncodec(savePath, width, height, sampleRate, channelCount);
    }

    public void startRecord() {
        if (surface != null ) {

            audioPts = 0;
            audioExit = false;
            videoExit = false;
            encodecStart = false;

            mEGLMediaThread = new EGLMediaThread(new WeakReference<BaseMediaEncoder>(this));
            videoEncodecThread = new VideoEncodecThread(new WeakReference<BaseMediaEncoder>(this));
            mEGLMediaThread.isCreate = true;
            mEGLMediaThread.isChange = true;
            mEGLMediaThread.start();
            videoEncodecThread.start();

            if (isRecodeAAC) {
                audioEncodecThread = new AudioEncodecThread(new WeakReference<BaseMediaEncoder>(this));
                audioEncodecThread.start();
            }
        }
    }

    public void stopRecord() {
        if (mEGLMediaThread != null && videoEncodecThread != null) {
            videoEncodecThread.exit();
            mEGLMediaThread.onDestory();
            videoEncodecThread = null;
            mEGLMediaThread = null;

        }
        if (audioEncodecThread != null) {
            audioEncodecThread.exit();
            audioEncodecThread = null;
        }
    }

    private void initMediaEncodec(String savePath, int width, int height, int sampleRate, int channelCount) {
        try {
            mediaMuxer = new MediaMuxer(savePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            initVideoEncodec(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
            if (isRecodeAAC) {
                initAudioEncodec(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void initVideoEncodec(String mimeType, int width, int height) {
        try {
            videoBufferinfo = new MediaCodec.BufferInfo();
            videoFormat = MediaFormat.createVideoFormat(mimeType, width, height);
            videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, width * height * 4);
            videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, RECODE_FRAME_RATE);
            videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

            videoEncodec = MediaCodec.createEncoderByType(mimeType);
            videoEncodec.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

            surface = videoEncodec.createInputSurface();

        } catch (IOException e) {
            e.printStackTrace();
            videoEncodec = null;
            videoFormat = null;
            videoBufferinfo = null;
        }

    }

    private void initAudioEncodec(String mimeType, int sampleRate, int channelCount) {
        try {
            this.sampleRate = sampleRate;
            audioBufferinfo = new MediaCodec.BufferInfo();
            audioFormat = MediaFormat.createAudioFormat(mimeType, sampleRate, channelCount);
            audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, 16000);
            audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 4096);

            audioEncodec = MediaCodec.createEncoderByType(mimeType);
            audioEncodec.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

        } catch (IOException e) {
            e.printStackTrace();
            audioBufferinfo = null;
            audioFormat = null;
            audioEncodec = null;
        }
    }

    public void putPCMData(byte[] buffer, int size) {
        if (audioEncodecThread != null && !audioEncodecThread.isExit && buffer != null && size > 0) {
            int inputBufferindex = audioEncodec.dequeueInputBuffer(0);
            if (inputBufferindex >= 0) {
                ByteBuffer byteBuffer = audioEncodec.getInputBuffers()[inputBufferindex];
                byteBuffer.clear();
                byteBuffer.put(buffer);
                long pts = getAudioPts(size, sampleRate);
                audioEncodec.queueInputBuffer(inputBufferindex, 0, size, pts, 0);
            }
        }
    }

    static class EGLMediaThread extends Thread {
        private WeakReference<BaseMediaEncoder> encoder;
        private EglHelper eglHelper;
        private Object object;

        private boolean isExit = false;
        private boolean isCreate = false;
        private boolean isChange = false;
        private boolean isStart = false;

        public EGLMediaThread(WeakReference<BaseMediaEncoder> encoder) {
            this.encoder = encoder;
        }

        @Override
        public void run() {
            super.run();
            isExit = false;
            isStart = false;
            object = new Object();
            eglHelper = new EglHelper();
            eglHelper.initEgl(encoder.get().surface, encoder.get().eglContext);

            while (true) {
                if (isExit) {
                    release();
                    break;
                }

                if (isStart) {
                    if (encoder.get().mRenderMode == RENDERMODE_WHEN_DIRTY) {
                        synchronized (object) {
                            try {
                                object.wait();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    } else if (encoder.get().mRenderMode == RENDERMODE_CONTINUOUSLY) {
                        try {
                            Thread.sleep(1000 / RECODE_FRAME_RATE);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    } else {
                        throw new RuntimeException("mRenderMode is wrong value");
                    }
                }

                if (encoder.get() != null ) {
                    onCreate();
                    onChange(encoder.get().width, encoder.get().height);
                    onDraw();
                }
                isStart = true;
            }

        }

        private void onCreate() {
            if (isCreate && encoder.get().mGLRender != null) {
                isCreate = false;
                encoder.get().mGLRender.onSurfaceCreated();
            }
        }

        private void onChange(int width, int height) {
            if (isChange && encoder.get().mGLRender != null) {
                isChange = false;
                encoder.get().mGLRender.onSurfaceChanged(width, height);
            }
        }

        private void onDraw() {
            if (encoder.get().mGLRender != null && eglHelper != null) {
                encoder.get().mGLRender.onDrawFrame();
                if (!isStart) {
                    encoder.get().mGLRender.onDrawFrame();
                }
                eglHelper.swapBuffers();

            }
        }

        private void requestRender() {
            if (object != null) {
                synchronized (object) {
                    object.notifyAll();
                }
            }
        }

        public void onDestory() {
            isExit = true;
            requestRender();
        }

        public void release() {
            if (eglHelper != null) {
                eglHelper.destoryEgl();
                eglHelper = null;
                object = null;
                encoder = null;
            }
        }
    }

    static class VideoEncodecThread extends Thread {
        private WeakReference<BaseMediaEncoder> encoder;

        private boolean isExit;

        private MediaCodec videoEncodec;
        private MediaCodec.BufferInfo videoBufferinfo;
        private MediaMuxer mediaMuxer;

        private int videoTrackIndex = -1;
        private long pts;


        public VideoEncodecThread(WeakReference<BaseMediaEncoder> encoder) {
            this.encoder = encoder;
            videoEncodec = encoder.get().videoEncodec;
            videoBufferinfo = encoder.get().videoBufferinfo;
            mediaMuxer = encoder.get().mediaMuxer;
            videoTrackIndex = -1;
        }

        @Override
        public void run() {
            super.run();
            pts = 0;
            videoTrackIndex = -1;
            isExit = false;
            videoEncodec.start();
            while (true) {
                if (isExit) {

                    videoEncodec.stop();
                    videoEncodec.release();
                    videoEncodec = null;
                    encoder.get().videoExit = true;
                    if (encoder.get().audioExit) {
                        if (encoder.get().encodecStart) {
                            mediaMuxer.stop();
                            mediaMuxer.release();
                            mediaMuxer = null;

                            Intent intent = new Intent("com.ihunuo.record");
                            intent.putExtra("savePath", encoder.get().savePath);
                            encoder.get().context.sendBroadcast(intent);
                        }
                    } else {
                        // 视频在相册不显示问题
                        if (encoder.get().encodecStart) {
                            mediaMuxer.stop();
                            mediaMuxer.release();
                            mediaMuxer = null;

                            Intent intent = new Intent("com.ihunuo.record");
                            intent.putExtra("savePath", encoder.get().savePath);
                            encoder.get().context.sendBroadcast(intent);
                        }
                    }
                    Log.d("ywl5320", "录制完成");

                    break;
                }

                int outputBufferIndex = videoEncodec.dequeueOutputBuffer(videoBufferinfo, 0);

                if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    videoTrackIndex = mediaMuxer.addTrack(videoEncodec.getOutputFormat());
                    if (encoder.get().audioEncodecThread != null) {
                        if (encoder.get().audioEncodecThread.audioTrackIndex != -1) {
                            mediaMuxer.start();
                            encoder.get().encodecStart = true;
                        }
                    } else {
                        mediaMuxer.start();
                        encoder.get().encodecStart = true;
                    }
                } else {
                    while (outputBufferIndex >= 0) {
                        if (encoder.get() == null) break;
                        if (encoder.get().encodecStart) {
                            ByteBuffer outputBuffer = videoEncodec.getOutputBuffers()[outputBufferIndex];
                            outputBuffer.position(videoBufferinfo.offset);
                            outputBuffer.limit(videoBufferinfo.offset + videoBufferinfo.size);
                            //
                            if (pts == 0) {
                                pts = videoBufferinfo.presentationTimeUs;
                            }
                            videoBufferinfo.presentationTimeUs = videoBufferinfo.presentationTimeUs - pts;

                            mediaMuxer.writeSampleData(videoTrackIndex, outputBuffer, videoBufferinfo);
                            byte[] data = new byte[videoBufferinfo.size];
                            outputBuffer.get(data, 0, videoBufferinfo.size);
//                            UIUtils.write(encoder.get().savePath, data, 0, videoBufferinfo.size);
                            if (encoder.get().onMediaInfoListener != null) {
                                encoder.get().onMediaInfoListener.onMediaTime((int) (videoBufferinfo.presentationTimeUs / 1000000));
                            }
                        }
                        videoEncodec.releaseOutputBuffer(outputBufferIndex, false);
                        outputBufferIndex = videoEncodec.dequeueOutputBuffer(videoBufferinfo, 0);
                    }
                }
            }
        }

        public void exit() {
            isExit = true;
        }

    }

    static class AudioEncodecThread extends Thread {

        private WeakReference<BaseMediaEncoder> encoder;
        private boolean isExit;

        private MediaCodec audioEncodec;
        private MediaCodec.BufferInfo bufferInfo;
        private MediaMuxer mediaMuxer;

        private int audioTrackIndex = -1;
        long pts;


        public AudioEncodecThread(WeakReference<BaseMediaEncoder> encoder) {
            this.encoder = encoder;
            audioEncodec = encoder.get().audioEncodec;
            bufferInfo = encoder.get().audioBufferinfo;
            mediaMuxer = encoder.get().mediaMuxer;
            audioTrackIndex = -1;
        }

        @Override
        public void run() {
            super.run();
            pts = 0;
            isExit = false;
            audioEncodec.start();
            while (true) {
                if (isExit) {
                    //

                    audioEncodec.stop();
                    audioEncodec.release();
                    audioEncodec = null;
                    encoder.get().audioExit = true;
                    if (encoder.get().videoExit) {
                        mediaMuxer.stop();
                        mediaMuxer.release();
                        mediaMuxer = null;
                    }
                    break;
                }

                int outputBufferIndex = audioEncodec.dequeueOutputBuffer(bufferInfo, 0);
                if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (mediaMuxer != null) {
                        audioTrackIndex = mediaMuxer.addTrack(audioEncodec.getOutputFormat());
                        if (encoder.get().videoEncodecThread.videoTrackIndex != -1) {
                            mediaMuxer.start();
                            encoder.get().encodecStart = true;
                        }
                    }
                } else {
                    while (outputBufferIndex >= 0) {
                        if (encoder.get().encodecStart) {

                            ByteBuffer outputBuffer = audioEncodec.getOutputBuffers()[outputBufferIndex];
                            outputBuffer.position(bufferInfo.offset);
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
                            if (pts == 0) {
                                pts = bufferInfo.presentationTimeUs;
                            }
                            bufferInfo.presentationTimeUs = bufferInfo.presentationTimeUs - pts;
                            mediaMuxer.writeSampleData(audioTrackIndex, outputBuffer, bufferInfo);
                        }
                        audioEncodec.releaseOutputBuffer(outputBufferIndex, false);
                        outputBufferIndex = audioEncodec.dequeueOutputBuffer(bufferInfo, 0);
                    }
                }

            }

        }

        public void exit() {
            isExit = true;
        }
    }

    public interface OnMediaInfoListener {
        void onMediaTime(int times);
    }

    private long getAudioPts(int size, int sampleRate) {
        audioPts += (long) (1.0 * size / (sampleRate * 2 * 2) * 1000000.0);
        return audioPts;
    }
}
