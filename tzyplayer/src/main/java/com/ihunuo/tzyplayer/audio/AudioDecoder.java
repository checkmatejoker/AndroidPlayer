package com.ihunuo.tzyplayer.audio;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * 作者:tzy on 2020-06-12.
 * 邮箱:215475174@qq.com
 * 功能介绍:xxx
 */
public class AudioDecoder {

    private static final String TAG = "AudioDecoder";
    public static final int KEY_CHANNEL_COUNT = 2;
    public Worker mWorker;
    private String path;//aac文件的路径。
    public boolean isVoicePlay = false, isCallBackPcmData = false;

    public AudioDecoder(String filename) {
        this.path = filename;
    }

    public void start() {
        if (mWorker == null) {
            mWorker = new Worker();
            mWorker.setRunning(true);
            mWorker.start();
        }
    }

    public void stop() {
        if (mWorker != null) {
            mWorker.setRunning(false);
            mWorker = null;
        }

    }

    public class Worker extends Thread {
        private static final int KEY_SAMPLE_RATE = 0;
        private boolean isRunning = false;
        private AudioTrack mPlayer;
        private MediaCodec mDecoder;
        private MediaExtractor extractor;

        public void setRunning(boolean run) {
            isRunning = run;
        }

        @Override
        public void run() {
            super.run();
            if (!prepare()) {
                isRunning = false;
                Log.d(TAG, "音频解码器初始化失败");
            }
            while (isRunning) {
                decode();
            }
            release();
            if (audioListener != null) {
                audioListener.endPlay();
            }
        }

        /**
         * 等待客户端连接，初始化解码器
         *
         * @return 初始化失败返回false，成功返回true
         */
        public boolean prepare() {
            // 等待客户端
            mPlayer = new AudioTrack(AudioManager.STREAM_MUSIC, 16000, AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT, 2048, AudioTrack.MODE_STREAM);//
            mPlayer.play();
            try {
                mDecoder = MediaCodec.createDecoderByType("audio/mp4a-latm");

                final String encodeFile = path;
                extractor = new MediaExtractor();
                extractor.setDataSource(encodeFile);

                MediaFormat mediaFormat = null;
                for (int i = 0; i < extractor.getTrackCount(); i++) {
                    MediaFormat format = extractor.getTrackFormat(i);
                    String mime = format.getString(MediaFormat.KEY_MIME);
                    if (mime.startsWith("audio/")) {
                        extractor.selectTrack(i);
                        mediaFormat = format;
                        Log.e("ccc", "prepare: ccccccccc");
                        break;
                    }
                }
                mediaFormat.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm");
                mediaFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 2);
                mediaFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, KEY_SAMPLE_RATE);
                mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 16000);
                mediaFormat.setInteger(MediaFormat.KEY_IS_ADTS, 1);
                mediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);

                int samplingFreq[] = {
                        96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050,
                        16000, 12000, 11025, 8000
                };

                // Search the Sampling Frequencies9
                int sampleIndex = -1;
                for (int i = 0; i < samplingFreq.length; ++i) {
                    if (samplingFreq[i] == 16000) {
                        Log.d("TAG", "kSamplingFreq " + samplingFreq[i] + " i : " + i);
                        sampleIndex = i;
                    }
                }

                if (sampleIndex == -1) {
                    return false;
                }

                mDecoder.configure(mediaFormat, null, null, 0);
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
            if (mDecoder == null) {
                Log.e(TAG, "create mediaDecode failed");
                return false;
            }
            mDecoder.start();
            return true;
        }

        /**
         * aac解码+播放
         */
        public void decode() {

            final long kTimeOutUs = 5000;
            boolean sawInputEOS = false;
            boolean sawOutputEOS = false;
            int totalRawSize = 0;

            try {
                ByteBuffer[] codecInputBuffers = mDecoder.getInputBuffers();
                ByteBuffer[] codecOutputBuffers = mDecoder.getOutputBuffers();
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

                while (!sawOutputEOS) {

//                    ByteBuffer inputBuffer = ByteBuffer.allocate(10 * 1024);
//                    int sampleSize = extractor.readSampleData(inputBuffer, 0);
//                    byte[] buffer = new byte[sampleSize];
//                    inputBuffer.get(buffer);
//
//                    decode(buffer, 0, buffer.length);

                    if (!sawInputEOS) {
                        int inputBufIndex = mDecoder.dequeueInputBuffer(-1);
                        if (inputBufIndex >= 0) {
                            ByteBuffer inputBuffer = ByteBuffer.allocate(10 * 1024);
                            ByteBuffer dstBuf = codecInputBuffers[inputBufIndex];

                            int sampleSize = extractor.readSampleData(inputBuffer, 0);
                            byte[] buffer = new byte[sampleSize];
                            inputBuffer.get(buffer);

//                            byte[] bytes = new byte[20];
//                            System.arraycopy(buffer, 0, bytes, 0, 20);
//                            Log.d(TAG, "decode: byte2hex=" + UIUtils.byte2hex(bytes));

                            if (buffer[0] != 0xff & (buffer[0] & 0xf0) != 0xf0) {//是否有ADTS头
                                Log.d(TAG, "decode: 111111");
                                byte[] newbuff = new byte[sampleSize + 7];
                                addADTStoPacket(newbuff, sampleSize + 7);
                                System.arraycopy(buffer, 0, newbuff, 7, sampleSize);
                                dstBuf.put(newbuff);
                                sampleSize += 7;
                            }else {
                                Log.d(TAG, "decode: 22222");
                                dstBuf.put(buffer);
                            }

                            if (sampleSize < 0) {
                                Log.i("TAG", "saw input EOS.");
                                sawInputEOS = true;
                                mDecoder.queueInputBuffer(inputBufIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            } else {
                                Log.i("TAG", "saw input getSampleTime.");
                                long presentationTimeUs = extractor.getSampleTime();
                                mDecoder.queueInputBuffer(inputBufIndex, 0, sampleSize, presentationTimeUs, 0);
                                extractor.advance();
                            }
                        }
                    }
                    int res = mDecoder.dequeueOutputBuffer(info, kTimeOutUs);
                    if (res >= 0) {

                        int outputBufIndex = res;
                        // Simply ignore codec config buffers.
                        if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            Log.i("TAG", "audio encoder: codec config buffer");
                            mDecoder.releaseOutputBuffer(outputBufIndex, false);
                            continue;
                        }

                        if (info.size != 0) {

                            ByteBuffer outBuf = codecOutputBuffers[outputBufIndex];

                            outBuf.position(info.offset);
                            outBuf.limit(info.offset + info.size);
                            byte[] data = new byte[info.size];
                            outBuf.get(data);
                            totalRawSize += data.length;
                            // fosDecoder.write(data);
                            Log.i("TAG", "audio encoder: data.length=" + data.length);
                            if (isVoicePlay)
                                mPlayer.write(data, 0, info.size);// 播放音乐
                            if (isCallBackPcmData && audioListener != null)
                                audioListener.onPcmData(data, info.size);

                            mDecoder.releaseOutputBuffer(outputBufIndex, false);
                        }

                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            Log.i("TAG", "saw output EOS.");
                            sawOutputEOS = true;
                        }

                    } else if (res == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                        codecOutputBuffers = mDecoder.getOutputBuffers();
                        Log.e("TAG", "output buffers have changed.");
                    } else if (res == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        MediaFormat oformat = mDecoder.getOutputFormat();
                        Log.e("TAG", "output format has changed to " + oformat);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                // fosDecoder.close();
                Log.e("TAG", "decode: finally close");
//                if (extractor != null) extractor.release();
                if (audioListener != null) {
                    audioListener.endPlay();
                }
            }
        }

        /**
         * 释放资源
         */
        public void release() {
            if (mDecoder != null) {
                mDecoder.stop();
                mDecoder.release();
                mDecoder = null;
            }
            if (mPlayer != null) {
                mPlayer.stop();
                mPlayer.release();
                mPlayer = null;
            }
            if (extractor != null) {
                extractor.release();
                extractor = null;
            }
        }

        /**
         * aac解码+播放
         */
        public void decode(byte[] buf, int offset, int length) {
//        byte[] bytes = new byte[10];
//        System.arraycopy(buf,0,bytes,0,10);
//        Log.d(TAG, "decode data: "+ UIUtils.byte2hex(buf));
            Log.d(TAG, "decode data length: " + length);

            //输入ByteBuffer
            ByteBuffer[] codecInputBuffers = mDecoder.getInputBuffers();
            //输出ByteBuffer
            ByteBuffer[] codecOutputBuffers = mDecoder.getOutputBuffers();
            //等待时间，0->不等待，-1->一直等待

            long kTimeOutUs = 100;
            try {
                //返回一个包含有效数据的input buffer的index,-1->不存在
                int inputBufIndex = mDecoder.dequeueInputBuffer(kTimeOutUs);
                if (inputBufIndex >= 0) {
                    //获取当前的ByteBuffer
                    ByteBuffer dstBuf = codecInputBuffers[inputBufIndex];
                    //清空ByteBuffer
                    dstBuf.clear();
                    //填充数据
                    dstBuf.put(buf, offset, length);
                    //将指定index的input buffer提交给解码器
                    mDecoder.queueInputBuffer(inputBufIndex, 0, length, 0, 0);
//                Log.d(TAG, "decode:inputBufIndex::::" + inputBufIndex);
                }
//            Log.d(TAG, "decode:inputBufIndex:" + inputBufIndex);
                //编解码器缓冲区
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                //返回一个output buffer的index，-1->不存在
                int outputBufferIndex = mDecoder.dequeueOutputBuffer(info, kTimeOutUs);

                Log.d(TAG, "decode:outputBufferIndex:" + outputBufferIndex);
                ByteBuffer outputBuffer;
                while (outputBufferIndex >= 0) {
                    //获取解码后的ByteBuffer
                    outputBuffer = codecOutputBuffers[outputBufferIndex];
                    //用来保存解码后的数据
                    byte[] outData = new byte[info.size];
                    outputBuffer.get(outData);
                    //清空缓存
                    outputBuffer.clear();
                    //播放解码后的数据
//                int a = AudioTrackPlay.audioTrackPlay_write(outData, 0, info.size);
                    if (isVoicePlay)
                        mPlayer.write(outData, 0, info.size);// 播放音乐
                    if (isCallBackPcmData && audioListener != null)
                        audioListener.onPcmData(outData, info.size);

                    //释放已经解码的buffer
                    mDecoder.releaseOutputBuffer(outputBufferIndex, false);
                    //解码未解完的数据
                    outputBufferIndex = mDecoder.dequeueOutputBuffer(info, kTimeOutUs);
                }
            } catch (Exception e) {
                Log.e(TAG, e.toString());
                e.printStackTrace();
            }
        }

    }


    public AudioListener audioListener;

    public interface AudioListener {
        void endPlay();

        void onPcmData(byte[] data, int size);
    }


    /**
     * 给编码出的aac裸流添加adts头字段
     *
     * @param packetLen
     */
    private byte[] addADTStoPacket(int packetLen) {
        byte[] packet = new byte[7];
        int profile = 2;  //AAC LC
        int freqIdx = 8;  //44.1KHz
        int chanCfg = 2;  //CPE
        packet[0] = (byte) 0xFF;
        packet[1] = (byte) 0xF9;
        packet[2] = (byte) (((profile - 1) << 6) + (freqIdx << 2) + (chanCfg >> 2));
        packet[3] = (byte) (((chanCfg & 3) << 6) + (packetLen >> 11));
        packet[4] = (byte) ((packetLen & 0x7FF) >> 3);
        packet[5] = (byte) (((packetLen & 7) << 5) + 0x1F);
        packet[6] = (byte) 0xFC;

        return packet;
    }

    /**
     * 不包含CRC，所以packetLen需要一帧的长度+7
     *
     * @param packet    一帧数据（包含adts头长度）
     * @param packetLen 一帧数据（包含adts头）的长度，
     */
    private void addADTStoPacket(byte[] packet, int packetLen) {
        int profile = 2; // AAC LC
        int freqIdx = 8; //8 标识16000，取特定
        int channelCfg = 2; // 音频声道数为两个

        // fill in ADTS data
        packet[0] = (byte) 0xFF;//1111 1111
        packet[1] = (byte) 0xF9;//1111 1001  1111 还是syncword
        // 1001 第一个1 代表MPEG-2,接着00为常量，最后一个1，标识没有CRC

        packet[2] = (byte) (((profile - 1) << 6) + (freqIdx << 2) + (channelCfg >> 2));
        packet[3] = (byte) (((channelCfg & 3) << 6) + (packetLen >> 11));
        packet[4] = (byte) ((packetLen & 0x7FF) >> 3);
        packet[5] = (byte) (((packetLen & 7) << 5) + 0x1F);
        packet[6] = (byte) 0xFC;
    }

}