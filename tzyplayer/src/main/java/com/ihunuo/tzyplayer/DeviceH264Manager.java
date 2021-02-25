package com.ihunuo.tzyplayer;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.Handler;
import android.os.Parcelable;
import android.util.Log;

import com.ihunuo.tzyplayer.communication.PlayH264Udp;
import com.ihunuo.tzyplayer.communication.TzyTcp;
import com.ihunuo.tzyplayer.lisrener.Decodelister;
import com.ihunuo.tzyplayer.lisrener.TcpLister;
import com.ihunuo.tzyplayer.surfaceviews.NormalSurfaceView;
import com.ihunuo.tzyplayer.surfaceviews.YUVSurfaceView;
import com.ihunuo.tzyplayer.units.UIUtils;

/**
 * 作者:tzy on 2020-06-12.
 * 邮箱:215475174@qq.com
 * 功能介绍:xxx
 */
public class DeviceH264Manager {
    private final Activity activity;
    public PlayH264Udp playH264Udp;
//    public TzyTcp tzyTcp;
    private int bufferNum;

    Handler handler = new Handler();
    private Decodelister decodelister;

    public String Path_Photo_Video_Save = "";

    int modeSize = 0;
    private boolean isTaking = false;
    private YUVSurfaceView yuvSurfaceView;
    private NormalSurfaceView normalSurfaceView;
    public DeviceH264Manager(Activity activity) {
        this.activity = activity;
        playH264Udp = PlayH264Udp.getInstance();
    }

    public void start(YUVSurfaceView myyuvSurfaceView, Decodelister mydecodelister) {
        this.decodelister = mydecodelister;
        this.yuvSurfaceView = myyuvSurfaceView;
        playH264Udp.initUDP(activity, decodelister,myyuvSurfaceView);//使用Surface + openglse2.0渲染
//        hnSocketMjpegUDP.send_UDP_open_signalframe();//UDP开流，解决宾远协议历史遗留问题


//        tzyTcp = new TzyTcp(new TcpLister() {
//            @Override
//            public void creatTcpSusscced() {
//
//            }
//
//            @Override
//            public void reviceData(byte[] data) {
//
//            }
//        });
//        tzyTcp.start();
//            sendTCPHeartbeat();//开始发送心跳

        IntentFilter intentFilter = new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        intentFilter.addAction("com.ihunuo.record");
        activity.registerReceiver(mReceiver, intentFilter);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                flagBroadcastReceiver = false;
            }
        }, 10000);


    }

    public void start(NormalSurfaceView mynormalSurfaceView, Decodelister mydecodelister) {
        this.decodelister = mydecodelister;
        this.normalSurfaceView = mynormalSurfaceView;
        playH264Udp.initUDP(activity, decodelister,mynormalSurfaceView);//使用Surface + openglse2.0渲染
//        hnSocketMjpegUDP.send_UDP_open_signalframe();//UDP开流，解决宾远协议历史遗留问题


//        tzyTcp = new TzyTcp(new TcpLister() {
//            @Override
//            public void creatTcpSusscced() {
//
//            }
//
//            @Override
//            public void reviceData(byte[] data) {
//
//            }
//        });
//        tzyTcp.start();
//            sendTCPHeartbeat();//开始发送心跳

        IntentFilter intentFilter = new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        intentFilter.addAction("com.ihunuo.record");
        activity.registerReceiver(mReceiver, intentFilter);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                flagBroadcastReceiver = false;
            }
        }, 10000);


    }


    public void setRotate(int rotate, int rotateX, int rotateY, int rotateZ) {
//            if (tzyGLSurfaceView != null) {
//                tzyGLSurfaceView.setRotate(rotate, rotateX, rotateY, rotateZ);
//            }
    }

    /**
     * 设置视频缓存
     *
     * @param var 缓存数
     */
    public void setBufferNum(int var) {
        bufferNum = var;
    }

    public void setVideoPath(String path)
    {
        playH264Udp.setSavePath(path);
    }

    boolean flagBroadcastReceiver = true;
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String action = intent.getAction();
            switch (action) {
                case WifiManager.NETWORK_STATE_CHANGED_ACTION: {
                    Parcelable parcelableExtra = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                    if (null != parcelableExtra) {
                        NetworkInfo networkInfo = (NetworkInfo) parcelableExtra;
                        NetworkInfo.State state = networkInfo.getState();
                        if (state == NetworkInfo.State.CONNECTED) {

                            if (!flagBroadcastReceiver) {
                                flagBroadcastReceiver = true;
                            }
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    flagBroadcastReceiver = false;
                                }
                            }, 2000);
                        }
                    }

                    break;
                }
                case "com.ihunuo.record": {

                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Thread.sleep(5000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            String savePath = intent.getStringExtra("savePath");
                            Log.d("ccc", "savePath="+savePath);
                            String saveMp4Name = savePath.substring(savePath.lastIndexOf("/") + 1);
                            String newPath = Environment.getExternalStorageDirectory().getAbsolutePath()
                                    + "/DCIM/Camera/" + saveMp4Name;
                            Log.d("ccc", "isADD_DCIM: " + newPath);
                            UIUtils.copyFile(savePath, newPath, context);
                        }
                    }).start();
                    break;
                }


            }
        }
    };

    //拍照录像接口

    public void takePhoto()
    {
        if (playH264Udp!=null)
        {
            playH264Udp.socket_photo();
        }
    }

    public void recodervodie(boolean isrecoder,int width,int height)
    {
        if (playH264Udp!=null)
        {
            playH264Udp.socket_video(isrecoder,width,height);
        }
    }


}
