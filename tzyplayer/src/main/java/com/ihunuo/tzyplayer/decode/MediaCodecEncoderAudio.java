package com.ihunuo.tzyplayer.decode;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;

import com.ihunuo.tzyplayer.units.UIUtils;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 作者:tzy on 2020-06-12.
 * 邮箱:215475174@qq.com
 * 功能介绍:xxx
 */
public class MediaCodecEncoderAudio {

    private MediaCodec mediaCodec;
    private String SaveH264Path;
    public boolean isRuning;
    public ConcurrentLinkedQueue aacQueue = new ConcurrentLinkedQueue();

    public void initEncoder(String path) {
        try {
            mediaCodec = MediaCodec.createEncoderByType("audio/mp4a-latm");
        } catch (IOException e) {
            e.printStackTrace();
        }
        MediaFormat mediaFormat = new MediaFormat();
        mediaFormat.setString("mime", "audio/mp4a-latm");
        mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 10 * 1024);//作用于inputBuffer的大小
        mediaFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
        mediaFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, 8000);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 16000);// AAC-HE // 64kbps
        mediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);//AACObjectLC
        mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mediaCodec.start();

        setSavePath(path);
        UIUtils.deleteFile(SaveH264Path + "/123.aac");
    }

    public void StartEncoderThread() {

        Thread EncoderThread = new Thread(new Runnable() {
            @Override
            public void run() {
                isRuning = true;
                while (isRuning | aacQueue.size() != 0) {

                    if (aacQueue.size() != 0) {
                        ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
                        ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();
                        int inputBufferIndex = mediaCodec.dequeueInputBuffer(-1);
                        if (inputBufferIndex >= 0) {
                            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                            inputBuffer.clear();
                            byte[] aac = (byte[]) aacQueue.poll();
                            inputBuffer.put(aac);
                            mediaCodec.queueInputBuffer(inputBufferIndex, 0, aac.length, 0, 0);
                        }
                        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                        int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
                        while (outputBufferIndex >= 0) {
                            ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                            outputBuffer.position(bufferInfo.offset);
                            byte[] outData = new byte[bufferInfo.size];
                            outputBuffer.get(outData);

                            byte[] bytes = new byte[bufferInfo.size + 7];
                            System.arraycopy(outData, 0, bytes, 7, bufferInfo.size);
                            addADTStoPacket(bytes, bufferInfo.size + 7);

                            mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                            outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);

                        }
                    } else {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                }
            }
        });

        EncoderThread.start();

    }


    /**
     * 给编码出的aac裸流添加adts头字段
     * 0: 96000 Hz 1: 88200 Hz 2: 64000 Hz 3: 48000 Hz 4: 44100 Hz
     * 5: 32000 Hz 6: 24000 Hz 7: 22050 Hz 8: 16000 Hz 9: 12000 Hz 10: 11025 Hz
     * 11: 8000 Hz 12: 7350 Hz 13: Reserved 14: Reserved 15: frequency is written explictly
     *
     * @param packet    要空出前7个字节，否则会搞乱数据
     * @param packetLen
     */
    private void addADTStoPacket(byte[] packet, int packetLen) {
        int profile = 2;  //AAC LC
        int freqIdx = 11;  //44.1KHz -4
        int chanCfg = 1;  //CPE
        packet[0] = (byte) 0xFF;
        packet[1] = (byte) 0xF9;
        packet[2] = (byte) (((profile - 1) << 6) + (freqIdx << 2) + (chanCfg >> 2));
        packet[3] = (byte) (((chanCfg & 3) << 6) + (packetLen >> 11));
        packet[4] = (byte) ((packetLen & 0x7FF) >> 3);
        packet[5] = (byte) (((packetLen & 7) << 5) + 0x1F);
        packet[6] = (byte) 0xFC;

        UIUtils.write(SaveH264Path + "/123.aac", packet, 0, packet.length);
    }


    public void setSavePath(String s) {
        SaveH264Path = s;
        File file = new File(s);
        if (!file.exists()) {
            file.mkdirs();
        }
    }

    public void release() {
        if (mediaCodec != null) {
            mediaCodec.stop();
            mediaCodec.release();
            mediaCodec = null;
        }
        isRuning = false;
    }


}
