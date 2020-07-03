package com.ihunuo.tzyplayer;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.Handler;
import android.os.Parcelable;
import android.util.Log;

import com.ihunuo.tzyplayer.communication.PlayH264Udp;
import com.ihunuo.tzyplayer.communication.PlayJPGUdp;
import com.ihunuo.tzyplayer.communication.TzyTcp;
import com.ihunuo.tzyplayer.lisrener.Decodelister;
import com.ihunuo.tzyplayer.lisrener.TcpLister;
import com.ihunuo.tzyplayer.surfaceviews.JPGSurfaceView;
import com.ihunuo.tzyplayer.units.UIUtils;

/**
 * 作者:tzy on 2020-06-12.
 * 邮箱:215475174@qq.com
 * 功能介绍:xxx
 */
public class DeviceJPGManager {

    private final Activity activity;
    public PlayJPGUdp playJPGUdp;
    public TzyTcp tzyTcp;
    private int bufferNum;

    Handler handler = new Handler();
    private Decodelister decodelister;

    public int isMjpeg = 0;
    public String Path_Photo_Video_Save = "";
    int modeSize = 0;
    private boolean isTaking = false;
    private JPGSurfaceView jpgSurfaceView;

    public DeviceJPGManager(Activity activity) {
        this.activity = activity;
        playJPGUdp = PlayJPGUdp.getInstance();
    }

    public void start(JPGSurfaceView myjpgSurfaceView, Decodelister mydecodelister) {
        this.decodelister = mydecodelister;
        this.jpgSurfaceView = myjpgSurfaceView;

        playJPGUdp.setBufferNum(bufferNum);
        playJPGUdp.initUDP(activity, jpgSurfaceView, decodelister);//使用Surface + openglse2.0渲染

        //可以使用tcp进行局域网通讯 暂时没用
        tzyTcp = new TzyTcp(new TcpLister() {
            @Override
            public void creatTcpSusscced() {

            }

            @Override
            public void reviceData(byte[] data) {

            }
        });
        tzyTcp.start();

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

}
