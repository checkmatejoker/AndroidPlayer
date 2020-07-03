package com.ihunuo.tzyplayer.communication;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.ihunuo.tzyplayer.R;
import com.ihunuo.tzyplayer.audio.AudioDecoder;
import com.ihunuo.tzyplayer.audio.AudioTrackPlay;
import com.ihunuo.tzyplayer.decode.MediaCodecEncoderAudio;
import com.ihunuo.tzyplayer.encode.BaseMediaEncoder;
import com.ihunuo.tzyplayer.encode.UserMediaEncodec;
import com.ihunuo.tzyplayer.lisrener.Decodelister;
import com.ihunuo.tzyplayer.filter.GPUImageBeautyFilter;
import com.ihunuo.tzyplayer.surfaceviews.JPGSurfaceView;
import com.ihunuo.tzyplayer.units.UIUtils;

import java.io.File;
import java.io.RandomAccessFile;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;

import jp.co.cyberagent.android.gpuimage.GPUImage;
import jp.co.cyberagent.android.gpuimage.filter.GPUImageBulgeDistortionFilter;
import jp.co.cyberagent.android.gpuimage.filter.GPUImageColorInvertFilter;
import jp.co.cyberagent.android.gpuimage.filter.GPUImageEmbossFilter;
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilterGroup;
import jp.co.cyberagent.android.gpuimage.filter.GPUImageGlassSphereFilter;
import jp.co.cyberagent.android.gpuimage.filter.GPUImageGrayscaleFilter;
import jp.co.cyberagent.android.gpuimage.filter.GPUImageMonochromeFilter;
import jp.co.cyberagent.android.gpuimage.filter.GPUImageSketchFilter;
import jp.co.cyberagent.android.gpuimage.filter.GPUImageSwirlFilter;

import static android.content.Context.WIFI_SERVICE;

/**
 * 作者:tzy on 2020-06-12.
 * 邮箱:215475174@qq.com
 * 功能介绍:xxx
 */
public class PlayJPGUdp {

    private static PlayJPGUdp playJPGUdp;
    private WifiManager.MulticastLock lock;
    private WifiManager manager;
    private DatagramSocket socket = null;
    private boolean isplay = true;//是否开启解码
    private boolean isRev = true;//是否接受
    private int delay_time = 200;//启动延时
    private Queue videoQueue = null;//缓存队列
    private boolean is_video_flag = false;//是否开始录像
    public  Context context;
    private String savePath;
    private Thread thread;
    private BitmapFactory.Options mBitmapOptions = new BitmapFactory.Options();
    public  JPGSurfaceView jpgSurfaceView;
    public  Decodelister decodelister;
    private MediaCodecEncoderAudio mediaCodecEncoderAudio;
    private boolean isFirst = true;
    public Bitmap resIdBitmap, bitmap;
    private AudioTrackPlay audioTrackPlay;

    public boolean isVoice = false;
    public boolean isVoicePlay = false;
    public boolean aiThreadFlag = false;
    public int width, height;//拍照

    public int isMjpeg = 0;
    private UserMediaEncodec userMediaEncodec;
    private String name;
    private AudioDecoder audioDecoder;
    private SendUDPThread sendUDPThread;
    public int filter = 0;
    private GPUImage gpuImage;

    public static PlayJPGUdp getInstance() {
        if (playJPGUdp == null) {
            playJPGUdp = new PlayJPGUdp();
        }
        return playJPGUdp;
    }

    public void initUDP(Context context, final JPGSurfaceView myjpgSurfaceView, Decodelister mydecodelister) {
        this.context = context;
        this.jpgSurfaceView = myjpgSurfaceView;
        this.decodelister = mydecodelister;
        mediaCodecEncoderAudio = new MediaCodecEncoderAudio();
        gpuImage = new GPUImage(context);

        audioTrackPlay = new AudioTrackPlay();
        audioTrackPlay.initAudioTrackPlay();

        savePath = Environment.getExternalStorageDirectory() + "/" + context.getString(R.string.app_name);

        initUDP();

    }

    public void initUDP() {

        if (frame_number != 0) {
            videoQueue = new ConcurrentLinkedQueue();//缓存队列
        }
        manager = (WifiManager) context.getSystemService(WIFI_SERVICE);
        lock = manager.createMulticastLock("test wifi");
        File file = new File(savePath + "/H264");
        if (!file.exists()) {
            file.mkdirs();
        }

        final Runnable mreceive = new Runnable() {

            @Override
            public void run() {
                //        1.  创建一个DatagramSocket对象，并指定监听的端口号
                DatagramPacket packages = null;
                //            InetAddress serverAddress;

                byte data[] = new byte[64 * 1024];
                byte pre_data[] = new byte[500000];
                int index = 0, length = 0, cha = 0, sameFrame = 0;
                isRev = true;
                isplay = true;
                try {
                    InetAddress serverAddress = InetAddress.getByName("192.168.1.1");
                    socket = new DatagramSocket(5555);
                    socket.setReceiveBufferSize(1024 * 64);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                packages = new DatagramPacket(data, data.length);
                while (isRev) {
                    try {
                        lock.acquire();
                        if (socket == null) return;

                        socket.receive(packages);
                        data = packages.getData();
                        lock.release();
                        if (data[0] == 0x03 && isplay) {//video
//                            Log.d("ggg", "run: index="+(data[1] & 0xff) + " 差值："+((data[1] & 0xff)-index));
                            if ((data[3] & 0xff) == 0) {//第一包
                                sameFrame = data[2] & 0xff;
                                length = 0;
                                index = 0;
                                System.arraycopy(data, 9, pre_data, length, packages.getLength() - 9);
                                length += packages.getLength() - 9;
                            } else if ((data[3] & 0xff) == index + 1 && sameFrame == (data[2] & 0xff)) {//后续包
                                index = data[3] & 0xff;
                                System.arraycopy(data, 9, pre_data, length, packages.getLength() - 9);
                                length += packages.getLength() - 9;
                            } else {//index不连续丢掉这一帧
                                Log.e("fff", "run: index不连续丢掉这一帧");
                                length = 0;
                                index = 0;
                                continue;
                            }
                            if ((data[4] & 0xff) == index + 1 && sameFrame == (data[2] & 0xff)) {//最后一包
                                fengbao(pre_data, length);
                                length = 0;
                                index = 0;
                            }
                        } else if (data[0] == 0x04 && isplay) {
//                            Log.d("rev_audio", "run: " + packages.getLength());
                            if (isVoicePlay) {
                                byte[] bytes = new byte[packages.getLength() - 3];
                                System.arraycopy(data, 1, bytes, 0, packages.getLength() - 3);
                                decode_audio(bytes);
                            }
                        } else if ((data[0] & 0xff) == 0x66 && (data[1] & 0xff) == 0x3e) {
                            if (decodelister != null)
                                decodelister.redata(data, data.length);
                            int power = data[2];
                            int wifipower = data[8];
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        };

        thread = new Thread(mreceive);
        thread.start();
//        startTimer(delay_time, 33);

    }

    public void fengbao(byte[] data, int length) {
        fps1++;
        cur++;
        if (videoQueue != null) {
            byte[] b_queue = new byte[length];
            System.arraycopy(data, 0, b_queue, 0, length);
            videoQueue.add(b_queue);
        } else {
            video_decode(data, length);
        }
        final int len = length;
        final int flag = data[1] & 0xff;

//        Log.d("aaa", "rev socket" + "接受数据：len=" + len + "  flag=" + flag + " fps=" + fps);

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

    public void video_decode(byte data[], int length) {//解码

        if (isFirst) {
            decodelister.firstDecode();
            isFirst = false;
        }

        mBitmapOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, length, mBitmapOptions);
        if (bitmap == null) return;
        this.bitmap = bitmap;
        width = bitmap.getWidth();
        height = bitmap.getHeight();
//        Log.d("ccc", "video_decode: length=" + length + " " + width + height);
        if (filter == 1) {//灰度
            GPUImage gpuImage = new GPUImage(context);
            gpuImage.setImage(bitmap);
            gpuImage.setFilter(new GPUImageGrayscaleFilter());
            this.bitmap = bitmap = gpuImage.getBitmapWithFilterApplied();
        } else if (filter == 2) {//美颜
            GPUImage gpuImage = new GPUImage(context);
            gpuImage.setImage(bitmap);

            GPUImageFilterGroup magicFilterGroup = new GPUImageFilterGroup();
            magicFilterGroup.addFilter(new GPUImageBeautyFilter());

            gpuImage.setFilter(magicFilterGroup);

            this.bitmap = bitmap = gpuImage.getBitmapWithFilterApplied();

        } else if (filter == 3) {//怀旧
            GPUImage gpuImage = new GPUImage(context);
            gpuImage.setImage(bitmap);
            gpuImage.setFilter(new GPUImageMonochromeFilter());
            this.bitmap = bitmap = gpuImage.getBitmapWithFilterApplied();
        } else if (filter == 4) {//鱼眼
            GPUImage gpuImage = new GPUImage(context);
            gpuImage.setImage(bitmap);
            gpuImage.setFilter(new GPUImageBulgeDistortionFilter());
            this.bitmap = bitmap = gpuImage.getBitmapWithFilterApplied();
        } else if (filter == 5) {//胶片
            GPUImage gpuImage = new GPUImage(context);
            gpuImage.setImage(bitmap);
            gpuImage.setFilter(new GPUImageColorInvertFilter());
            this.bitmap = bitmap = gpuImage.getBitmapWithFilterApplied();
        } else if (filter == 6) {//浮雕
            GPUImage gpuImage = new GPUImage(context);
            gpuImage.setImage(bitmap);
            gpuImage.setFilter(new GPUImageEmbossFilter());
            this.bitmap = bitmap = gpuImage.getBitmapWithFilterApplied();
        } else if (filter == 7) {//旋涡
            GPUImage gpuImage = new GPUImage(context);
            gpuImage.setImage(bitmap);
            gpuImage.setFilter(new GPUImageSwirlFilter());
            this.bitmap = bitmap = gpuImage.getBitmapWithFilterApplied();
        } else if (filter == 8) {//素描
            GPUImage gpuImage = new GPUImage(context);
            gpuImage.setImage(bitmap);
            gpuImage.setFilter(new GPUImageSketchFilter());
            this.bitmap = bitmap = gpuImage.getBitmapWithFilterApplied();
        } else if (filter == 9) {//水晶
            GPUImage gpuImage = new GPUImage(context);
            gpuImage.setImage(bitmap);
            gpuImage.setFilter(new GPUImageGlassSphereFilter());
            this.bitmap = bitmap = gpuImage.getBitmapWithFilterApplied();
        }

        jpgSurfaceView.setCurrentBitmap(bitmap);


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
                serverAddress = InetAddress.getByName("192.168.1.1");//2.创建一个 netAddress相当于是地址

                byte data_send[] = new byte[4];
                package_send = new DatagramPacket(data_send, data_send.length, serverAddress, 5555);//5.创建一个DatagramPacket 对象，并指定要讲这个数据包发送到网络当中的哪个地址，以及端口号

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
                    message.what = 520756;
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


    private int decode_time = 100;//解码时间
    private int fps = 0, fps1 = 0, cur = 0, fps_sec_flag = 0, frame_number = 0;
    private boolean is_frame_time_flag = false;//是否调整解码速度
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message message) {
            switch (message.what) {
                case 520756:
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
                            startTimer(0, 120);
                            is_frame_time_flag = true;
                            decode_time = 120;
                            Log.e("aaa", "handleMessage: 11111");
                        } else if (videoQueue.size() >= frame_number + 3 & !is_frame_time_flag) {
                            stopTimer();
                            startTimer(0, 80);
                            is_frame_time_flag = true;
                            decode_time = 80;
                            Log.e("aaa", "handleMessage: 22222");
                        } else if (is_frame_time_flag & frame_number + 1 <= videoQueue.size()) {
                            stopTimer();
                            startTimer(0, 100);
                            is_frame_time_flag = false;
                            decode_time = 100;
                            Log.e("aaa", "handleMessage: 33333");
                        }
                    }
                    if (videoQueue != null && videoQueue.size() > 0) {
                        final byte[] data = (byte[]) videoQueue.poll();
                        if (data != null) {
                            video_decode(data, data.length);
                            Log.d("aaa", "handleMessage: " + fps_sec_flag);
                        }

                    }

                    WifiManager wifi_service = (WifiManager) context.getSystemService(WIFI_SERVICE);
                    WifiInfo wifiInfo = wifi_service.getConnectionInfo();
                    int wifiInfoRssi = wifiInfo.getRssi();
                    int speed = wifiInfo.getLinkSpeed();
                    break;

            }
        }
    };

    public void setBufferNum(int flag) {//设置缓存帧数
        frame_number = flag;
    }

    public boolean hn_socket_photo(boolean var) {//拍照
        if (width != 0 && height != 0) {
            jpgSurfaceView.imgVideoRender.talkPhoto(var, getSavePath(), width, height);
        }
        return jpgSurfaceView.imgVideoRender.isTalkPhoto;
    }

    public boolean hn_socket_video(boolean flag) {// flag 0关闭 1打开
        if (width != 0 && height != 0) {
            is_video_flag = flag;
            if (is_video_flag) {

                name = System.currentTimeMillis() + ".mp4";
                userMediaEncodec = new UserMediaEncodec(context, jpgSurfaceView.getFbotextureid());
                userMediaEncodec.initEncodec(jpgSurfaceView.getEglContext(), getSavePath() + "/" + name,
                        width, height, 16000, 2, isVoice);
                userMediaEncodec.setOnMediaInfoListener(new BaseMediaEncoder.OnMediaInfoListener() {
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
        File file = new File(this.savePath + "/H264");
        if (!file.exists()) {
            file.mkdirs();
        }
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
    }

    public void hn_UDP_release() {
        cur = 0;
        isRev = false;

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

    public void renderBg(boolean var) {
        if (resIdBitmap != null && var) {
            jpgSurfaceView.setCurrentBitmap(resIdBitmap);

        }
    }
}
