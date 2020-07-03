package com.ihunuo.tzyplayer.communication;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import com.coremedia.iso.boxes.Container;
import com.googlecode.mp4parser.FileDataSourceImpl;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder;
import com.googlecode.mp4parser.authoring.tracks.AACTrackImpl;
import com.googlecode.mp4parser.authoring.tracks.h264.H264TrackImpl;
import com.ihunuo.tzyplayer.R;
import com.ihunuo.tzyplayer.audio.AudioDecoder;
import com.ihunuo.tzyplayer.audio.AudioTrackPlay;
import com.ihunuo.tzyplayer.decode.MediaCodePlayYuv;
import com.ihunuo.tzyplayer.decode.MediaCodecEncoderAudio;
import com.ihunuo.tzyplayer.encode.UserMediaEncodec;
import com.ihunuo.tzyplayer.lisrener.Decodelister;
import com.ihunuo.tzyplayer.surfaceviews.YUVSurfaceView;
import com.ihunuo.tzyplayer.units.UIUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.channels.FileChannel;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;

import jp.co.cyberagent.android.gpuimage.GPUImage;

import static android.content.Context.WIFI_SERVICE;

/**
 * 作者:tzy on 2020-06-12.
 * 邮箱:215475174@qq.com
 * 功能介绍:xxx
 */
public class PlayH264Udp {
    private static PlayH264Udp playH264Udp;
    private WifiManager.MulticastLock lock;
    private WifiManager manager;
    private DatagramSocket socket = null;
    private boolean isplay = true;//是否开启解码
    private boolean isRev = true;//是否接受
    private int delay_time = 200;//启动延时
    private Queue videoQueue = null;//缓存队列
    private boolean is_video_flag = false;//是否开始录像

    public int width, height;//拍照
    public Context context;
    private String savePath;
    private Thread thread;


    public Decodelister decodelister;
    private MediaCodecEncoderAudio mediaCodecEncoderAudio;
    private boolean isFirst = true;
    public Bitmap resIdBitmap, bitmap;
    private AudioTrackPlay audioTrackPlay;

    public boolean isVoice = false;
    public boolean isVoicePlay = false;
    private boolean is_sps_pps_ready = false;//第一次读取到I帧
    private UserMediaEncodec userMediaEncodec;
    private AudioDecoder audioDecoder;
    private SendUDPThread sendUDPThread;
    public YUVSurfaceView myGLSurfaceView;



    public MediaCodePlayYuv mediaCodePlayOpenGL;

    public static PlayH264Udp getInstance() {
        if (playH264Udp == null) {
            playH264Udp = new PlayH264Udp();
        }
        return playH264Udp;
    }

    public void initUDP(Context context, Decodelister mydecodelister , YUVSurfaceView src) {
        this.context = context;
        this.myGLSurfaceView = src;
        mediaCodePlayOpenGL = new MediaCodePlayYuv(mydecodelister);
        mediaCodePlayOpenGL.initDecoder();
        initUDP();
    }


    private void initUDP() {

        if (frame_number != 0) {
            videoQueue = new ConcurrentLinkedQueue();//缓存队列
        }
        manager = (WifiManager) context.getSystemService(WIFI_SERVICE);
        lock = manager.createMulticastLock("test wifi");
        File file = new File(savePath + "H264");
        if (!file.exists()) {
            file.mkdirs();
        }

        Runnable mreceive = new Runnable() {

            @Override
            public void run() {
                //        1.  创建一个DatagramSocket对象，并指定监听的端口号
                DatagramPacket packages = null;
                //            InetAddress serverAddress;

                byte data[] = new byte[64000];
                byte pre_data[] = new byte[500000];
                int index = 0, length = 0, cha = 0;
                isRev = true;
                isplay = true;
                try {
//                    serverAddress = InetAddress.getByName(ip);
                    socket = new DatagramSocket(5000);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                packages = new DatagramPacket(data, data.length);

                while (isRev) {
                    try {

                        lock.acquire();
//                        Log.d("aaa", "video_rev: 1111111111");
                        socket.receive(packages);
                        data = packages.getData();
                        lock.release();
//                        byte b[] = new byte[20];
//                        System.arraycopy(data, 0, b, 0, 20);
//                        Log.d("aaa", "video_rev: "+UIUtils.byte2hex(b));

                        if (data[0] == 0x03) {
//                            Log.d("ggg", "run: index="+(data[1] & 0xff) + " 差值："+((data[1] & 0xff)-index));
                            if ((data[3] & 0xff) == 0) {//第一包
                                length = 0;
                                index = 0;
                                System.arraycopy(data, 9, pre_data, length, packages.getLength() - 9);
                                length += packages.getLength() - 9;
                            } else if ((data[3] & 0xff) == index + 1) {//后续包
                                index = data[3] & 0xff;
                                System.arraycopy(data, 9, pre_data, length, packages.getLength() - 9);
                                length += packages.getLength() - 9;
                            } else {//index不连续丢掉这一帧
                                length = 0;
                                index = 0;
                                Log.d("aaa", "run: index不连续丢掉这一帧");
                                continue;
                            }
                            if ((data[4] & 0xff) == index + 1) {//最后一包
                                fengbao(pre_data, length);
                                length = 0;
                                index = 0;
                            }
                        }       //标准h264的话直接传数据
                        else
                        {
                            fengbao(data, data.length);
                        }




                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        };

        thread = new Thread(mreceive);
        thread.start();
        startTimer(delay_time, 45);

    }


    public void fengbao(byte[] data, int length) {
        fps1++;
        cur++;

        if (videoQueue != null) {
            if (isplay) {
                byte[] b_queue = new byte[length];
                System.arraycopy(data, 0, b_queue, 0, length);
                videoQueue.add(b_queue);
            }
        } else {
            if (isplay) video_decode(data, length);
        }

        final int len = length;
        final int flag = data[1] & 0xff;
        Log.d("aaa", "run: rev socket" + "接受数据：len=" + len + "  flag=" + flag + " fps=" + fps);
    }

    public void video_decode(byte data[], int length) {//解码
        int offset = 0;
//        Log.d("ccc", "video_decode: " + length);
        if ((data[4] & 0x1f) == 7 && !is_sps_pps_ready) {

            mediaCodePlayOpenGL.onFrame(data, 0, length);
            is_sps_pps_ready = true;


        } else if (is_sps_pps_ready && (data[4] & 0x1f) == 7 || (data[4] & 0x1f) == 8) {
            if ((data[4] & 0x1f) == 7) {
                offset = offset + 32;
            } else if ((data[32] & 0x1f) == 8) {
                if (data[7 + 32] == 0 && data[8 + 32] == 0) {
                    offset = offset + 9;
                } else {
                    offset = offset + 10;
                }
            }
            mediaCodePlayOpenGL.onFrame(data, offset, length - offset);

            Log.d("ccc", "run: is_sps_pps_read:" + is_sps_pps_ready);
        } else if (is_sps_pps_ready) {
            mediaCodePlayOpenGL.onFrame(data, 0, length);

        }



    }


    private void decode_audio(final byte[] bytes) {
        audioTrackPlay.audioQueue.add(bytes);
        if (userMediaEncodec != null) {
            byte[] bytes1 = new byte[bytes.length / 2];
            System.arraycopy(bytes, 0, bytes1, 0, bytes.length / 2);
            byte[] bytes2 = new byte[bytes.length / 2];
            System.arraycopy(bytes, bytes.length / 2, bytes2, 0, bytes.length / 2);

            userMediaEncodec.putPCMData(bytes1, bytes.length / 2);//一次有两包数据
            userMediaEncodec.putPCMData(bytes2, bytes.length / 2);
        }

    }



    private byte[] send_udp_state = new byte[10];//0 帧头 1 控制码 2 尾码

    public class SendUDPThread extends Thread {
        @Override
        public void run() {
            super.run();
            DatagramPacket package_send = null;
            InetAddress serverAddress = null;

            try {
//            socket = new DatagramSocket(5088);
                if (socket == null) {
                    Log.e("aaa", "send_udp: socket=null");
                    return;
                }
                serverAddress = InetAddress.getByName("192.168.39.1");//2.创建一个 netAddress相当于是地址

                byte data_send[] = new byte[4];
                package_send = new DatagramPacket(data_send, data_send.length, serverAddress, 6666);//5.创建一个DatagramPacket 对象，并指定要讲这个数据包发送到网络当中的哪个地址，以及端口号

                data_send[0] = send_udp_state[0];
                data_send[1] = send_udp_state[1];

                for (int i = 0; i < 2; i++) {
                    data_send[2] = (byte) ((int) data_send[2] + (int) data_send[i]);
                }
                data_send[3] = send_udp_state[2];

                Log.d("aaa", "send_udp: " + UIUtils.byte2hex(data_send));
                package_send.setData(data_send);
                lock.acquire();
                socket.send(package_send);// 6. 调用DatagramSocket对象的send方法 发送数据
                lock.release();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    public void send_UDP_data(byte[] sendbyte) {
        for (int i = 0; i < sendbyte.length; i++) {
            send_udp_state[i] = sendbyte[i];
        }
        sendUDPThread = null;
        sendUDPThread = new SendUDPThread();
        sendUDPThread.interrupt();
        sendUDPThread.start();
    }

    private Timer timer;
    private TimerTask task;

    private void startTimer(long delay, long period) {
        if (timer == null) timer = new Timer();
        if (task == null) {
            task = new TimerTask() {
                @Override
                public void run() {
                    Message message = new Message();
                    message.what = 5233;
                    handler.sendMessage(message);
                }
            };
            timer.schedule(task, delay, period);
        }

    }

    private void stopTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        if (task != null) {
            task.cancel();
            task = null;
        }
    }


    private int decode_time = 40;//解码时间
    private int fps = 0, fps1 = 0, cur = 0, fps_sec_flag = 0, frame_number = 5;
    private boolean is_frame_time_flag = false;//是否调整解码速度
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message message) {
            switch (message.what) {
                case 5233:
                    fps_sec_flag++;
//                    Log.e("aaa", "fps_sec_flag: " + fps_sec_flag);
                    if (fps_sec_flag % 30 == 0) {
                        fps = fps1;
                        fps1 = 0;
                        if (fps_sec_flag == 6000) fps_sec_flag = 0;
                    }
//                    Log.d("aaa", "handleMessage: "+fps_sec_flag);
                    if (videoQueue != null && videoQueue.size() != 0 && frame_number >= 3) {
                        if (videoQueue.size() <= frame_number - 2 & !is_frame_time_flag) {
                            stopTimer();
                            startTimer(0, 45);
                            is_frame_time_flag = true;
                            decode_time = 45;
//                            Log.e("aaa", "handleMessage: 11111");
                        } else if (videoQueue.size() >= frame_number + 3 & !is_frame_time_flag) {
                            stopTimer();
                            startTimer(0, 35);
                            is_frame_time_flag = true;
                            decode_time = 35;
//                            Log.e("aaa", "handleMessage: 22222");
                        } else if (is_frame_time_flag & frame_number + 1 <= videoQueue.size()) {
                            stopTimer();
                            startTimer(0, 40);
                            is_frame_time_flag = false;
                            decode_time = 40;
//                            Log.e("aaa", "handleMessage: 33333");
                        }
                    }
                    if (videoQueue != null && videoQueue.size() > 0 & isplay) {
                        final byte[] data = (byte[]) videoQueue.poll();
                        if (data != null) {
                            video_decode(data, data.length);
//                            Log.d("aaa", "handleMessage: " + fps_sec_flag);
                        }

                    } else {
//                        if (videoQueue != null)
//                            Log.d("ccc", "handleMessage: videoQueue.size:" + videoQueue.size());
                    }

                    break;

            }
        }
    };

    public void setBufferNum(int flag) {//设置缓存帧数
        frame_number = flag;
    }


    public void socket_photo() {//拍照
        if (mediaCodePlayOpenGL != null && !mediaCodePlayOpenGL.photo_codec_flag) {
            mediaCodePlayOpenGL.photo_codec_flag = true;
        }
    }


    public void decodePlayAudioAAC(String path) {
        audioDecoder = new AudioDecoder(path);
        audioDecoder.audioListener = audioListener;
        audioDecoder.isVoicePlay = true;
        audioDecoder.isCallBackPcmData = true;
        audioDecoder.start();

    }

    public void releaseDecodeAudioAAC() {
        if (audioDecoder != null) {
            audioDecoder.audioListener = null;
            audioDecoder.mWorker.release();
            audioDecoder.stop();
            audioDecoder = null;
        }
    }

    AudioDecoder.AudioListener audioListener = new AudioDecoder.AudioListener() {
        @Override
        public void endPlay() {
            releaseDecodeAudioAAC();
        }

        @Override
        public void onPcmData(byte[] pcmdata, int size) {
            if (userMediaEncodec != null) userMediaEncodec.putPCMData(pcmdata, size);
            Log.d("ccc", "onPcmData: size=" + size);
        }
    };


    public void setSavePath(String savePath) {
        this.savePath = savePath;
    }

    public String getSavePath() {
        return savePath;
    }

    public void hn_UDP_Resume() {
        isplay = true;
        isVoicePlay = true;
    }

    public void hn_UDP_Pause() {
        isVoicePlay = false;
        is_sps_pps_ready = false;
    }

    public void hn_UDP_release() {
        cur = 0;
        isRev = false;
        is_sps_pps_ready = false;

        stopTimer();
        if (videoQueue != null) {
            videoQueue.clear();
            videoQueue = null;
        }
        if (socket != null) socket.close();
        socket = null;

        if (mediaCodecEncoderAudio != null) mediaCodecEncoderAudio.release();
        if (audioTrackPlay != null) audioTrackPlay.Audiorelease();
    }

    public void write(String path, byte[] b, int offset, int lenght) {
        try {
            //判断实际是否有SD卡，且应用程序是否有读写SD卡的能力，有则返回true
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                // 获取SD卡的目录

                File dir = new File(path);

                File targetFile = new File(path);
                if (!targetFile.exists()) {
                    targetFile.createNewFile();
                }
                //使用RandomAccessFile是在原有的文件基础之上追加内容，
                //而使用outputstream则是要先清空内容再写入
                RandomAccessFile raf = new RandomAccessFile(targetFile, "rw");
                //光标移到原始文件最后，再执行写入
                raf.seek(targetFile.length());
                raf.write(b, offset, lenght);
                raf.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public boolean isRecording() {
        return is_video_flag;
    }



    /**
     * @param save_mp4_path 保存MP4路径
     * @param h264_path     读取H264路径
     * @param acc_path      读取acc路径 如果没有ACC 传“”
     * @param flag          是否有音频
     */
    public void H264toMP4(String save_mp4_path, String h264_path, String acc_path, boolean flag) {
        FileOutputStream fos = null;
        AACTrackImpl aacTrack = null;
        Movie m;
        Log.d("ccc", "H264toMP4: h264_path=" + h264_path + " acc_path=" + acc_path);
        try {
            //这里传入的file是指H264格式对应的文件
            H264TrackImpl h264Track = new H264TrackImpl(new FileDataSourceImpl(h264_path));
            if (flag) {
                aacTrack = new AACTrackImpl(new FileDataSourceImpl(acc_path));
                m = new Movie();
                m.addTrack(h264Track);
                m.addTrack(aacTrack);
            } else {
                m = new Movie();
                m.addTrack(h264Track);
            }

            Container out = new DefaultMp4Builder().build(m);
            //这里传入的就是要保存的mp4文件目录
            fos = new FileOutputStream(save_mp4_path);
            FileChannel fc = fos.getChannel();
            out.writeContainer(fc);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (null != fos)
                    fos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    //新版录像

    public boolean socket_video(boolean flag,int recodewith,int recodeheght) {// flag 0关闭 1打开
        if (recodewith != 0 && recodeheght != 0) {
            is_video_flag = flag;
            if (is_video_flag) {

                String name = System.currentTimeMillis()+ ".mp4";
                userMediaEncodec = new UserMediaEncodec(context, myGLSurfaceView.getFbotextureid());
                userMediaEncodec.initEncodec(myGLSurfaceView.getEglContext(), getSavePath() + "/" + name,
                        recodewith, recodeheght, 16000, 2, isVoice);
                userMediaEncodec.setOnMediaInfoListener(new UserMediaEncodec.OnMediaInfoListener() {
                    @Override
                    public void onMediaTime(int times) {
                        Log.d("ywl5320", "time is : " + times);
                    }
                });
                userMediaEncodec.startRecord();

            } else {
                userMediaEncodec.stopRecord();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        userMediaEncodec = null;
                    }
                }, 500);

            }
        }
        return is_video_flag;
    }

}
