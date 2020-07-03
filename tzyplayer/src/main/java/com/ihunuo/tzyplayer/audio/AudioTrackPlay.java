package com.ihunuo.tzyplayer.audio;

/**
 * 作者:tzy on 2020-06-12.
 * 邮箱:215475174@qq.com
 * 功能介绍:xxx
 */

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.util.concurrent.ConcurrentLinkedQueue;

public class AudioTrackPlay {
    public AudioTrack Audioplayer;
    public int audioBufSize;
    String filePath = Environment.getExternalStorageDirectory() + "/TQT_Car/abctest123.pcm";
    String mp3Path = Environment.getExternalStorageDirectory() + "/TQT_Car/testmp3.mp3";

    public ConcurrentLinkedQueue audioQueue = new ConcurrentLinkedQueue();

    public boolean isVoice = true;//是否打开声音

//     String filePath = Environment.getExternalStorageDirectory() + "/TQT_Car/8k_16bit_mono.pcm";
//     String filePath = Environment.getExternalStorageDirectory() + "/TQT_Car/tageat.wav";

    public void initAudioTrackPlay() {
        audioBufSize = AudioTrack.getMinBufferSize(8000,
                AudioFormat.CHANNEL_CONFIGURATION_MONO,//  单通道
                AudioFormat.ENCODING_PCM_16BIT);
        Log.e("bbb", "initAudioTrackPlay: audioBufSize:"+audioBufSize);

        Audioplayer = new AudioTrack(AudioManager.STREAM_MUSIC, 8000,
                AudioFormat.CHANNEL_CONFIGURATION_MONO,//  单通道
                AudioFormat.ENCODING_PCM_16BIT,
                audioBufSize,
                AudioTrack.MODE_STREAM);
        Audioplayer.play();
//        new Player().start();
        thread.start();
    }

    public int audioTrackPlay_write(byte[] data, int offset, int length) {
        int ret = 0;
        if (Audioplayer != null && isVoice) {
            ret = Audioplayer.write(data, offset, length);
        }
        return ret;
    }

    public void Audiorelease() {
        if (Audioplayer != null) {
            Audioplayer.stop();//停止播放
            Audioplayer.release();//释放底层资源。
            Audioplayer = null;
        }
        isOpen = false;
    }

    private boolean isOpen;
    Thread thread = new Thread(new Runnable() {
        @Override
        public void run() {
            isOpen = true;
            while (isOpen) {
                if (audioQueue.size() != 0) {
                    byte[] bytes = (byte[]) audioQueue.poll();
                    audioTrackPlay_write(bytes, 0, bytes.length);
                }else {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    });


    class Player extends Thread {
        byte[] data1 = new byte[audioBufSize * 2];
        File file = new File(filePath);
        int off1 = 0;
        FileInputStream fileInputStream;

        @Override
        public void run() {
            super.run();
            while (true) {
                try {
                    fileInputStream = new FileInputStream(file);
                    fileInputStream.skip((long) off1);
                    int num = fileInputStream.read(data1, 0, audioBufSize * 2);
                    Log.d("bbb", "run: read:num" + num);
                    off1 += audioBufSize * 2;
                } catch (Exception e) {

                }
                int ret = Audioplayer.write(data1, 0, audioBufSize * 2);
                Log.d("bbb", "run: player.write:" + ret);
            }
        }
    }

    public boolean isGetAudio = false;

    public void getAudio() {
        isGetAudio = true;
        new Thread(new Runnable() {
            @Override
            public void run() {

//                UIUtils.deleteFile(mp3Path);

//                inSamplerate ： 输入采样频率 Hz
//                inChannel ： 输入声道数
//                outSamplerate ： 输出采样频率 Hz
//                outBitrate ： Encoded bit rate. KHz
//                quality ： MP3音频质量。0~9。 其中0是最好，非常慢，9是最差。
//                LameUtil.init(8000, 1, 8000, 128, 6);

                //根据自己设置的音频格式配置相应的数组大小，用于存储数据，同时可以提高效率，节约空间
                int bufferSize = 2 * AudioRecord.getMinBufferSize(8000, AudioFormat.CHANNEL_CONFIGURATION_MONO, AudioFormat.ENCODING_PCM_16BIT);
                AudioRecord mic = new AudioRecord(MediaRecorder.AudioSource.MIC, 8000, AudioFormat.CHANNEL_CONFIGURATION_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
                mic.startRecording();

                byte pcmBuffer[] = new byte[512];
                while (isGetAudio) {
                    int encodedSize = mic.read(pcmBuffer, 0, pcmBuffer.length);
                    if (encodedSize <= 0) {
                        break;
                    }
                }
            }
        }).start();
    }



}
