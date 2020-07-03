package com.ihunuo.tzyplayer.communication;

import android.app.Activity;
import android.net.wifi.WifiManager;
import android.util.Log;

import com.ihunuo.tzyplayer.units.UIUtils;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import static android.content.Context.WIFI_SERVICE;

/**
 * 作者:tzy on 2020-06-11.
 * 邮箱:215475174@qq.com
 * 功能介绍:基本的Tcp通讯可以用来交互数据或者读取传送的code值
 */
public class TzyUdp {

    private Runnable mreceive;
    private DatagramSocket socket = null;
    private WifiManager.MulticastLock lock;
    private static SendUdp mysend_udp;

    private static TzyUdp udpManger;
    private InetAddress serverAddress;
    int port = 5000;//5013 8888之前的端口
    private boolean isRun = true;
    public static TzyUdp getUdpManger() {
        if (udpManger == null) {
            udpManger = new TzyUdp();
        }
        return udpManger;
    }


    //Udp部分

    public void initUDP(Activity activity) {
        WifiManager manager = (WifiManager) activity.getApplicationContext().getSystemService(WIFI_SERVICE);
        lock = manager.createMulticastLock("test wifi");

        mreceive = new Runnable() {
            @Override
            public void run() {
                //        1.创建一个DatagramSocket对象，并指定监听的端口号
                DatagramPacket packages = null;
                //       InetAddress serverAddress;

                byte data[] = new byte[15];
                try {
                    //      serverAddress = InetAddress.getByName("192.168.1.1");
                    socket = new DatagramSocket(port);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                packages = new DatagramPacket(data, data.length);

                while (isRun) {
                    try {
                        lock.acquire();
                        if (socket == null) return;
                        socket.receive(packages);
                        data = packages.getData();
                        lock.release();

                        String buf = UIUtils.byte2hex(data);
                        Log.d("aaa", "rev: buf: " + buf);

                            if (udpListen != null) {
                                udpListen.revUdpData();
                            }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        };

        new Thread(mreceive).start();


    }


    public void SendData(byte[] sendData) {
        mysend_udp = null;
        mysend_udp = new SendUdp(sendData);
        mysend_udp.setSendData(sendData);
        mysend_udp.start();
    }

    public class SendUdp extends Thread {

        byte[] sendData;

        SendUdp(byte[] data) {
            sendData = data;
        }

        public void setSendData(byte[] data) {
            sendData = data;
        }

        @Override
        public void run() {
            super.run();
            //                        1.  创建一个DatagramSocket对象
            DatagramPacket package_send = null;
            InetAddress serverAddress = null;

            try {
//            socket = new DatagramSocket(5088);
                if (socket == null) {
                    Log.e("CamreaFragment", "SendUdp: socket=null");
                    return;
                }
                serverAddress = InetAddress.getByName("192.168.100.1");//uav2.创建一个 netAddress相当于是地址

                package_send = new DatagramPacket(sendData, sendData.length, serverAddress, port);
                package_send.setData(sendData);

//                Log.d("aaa", "SendUdp: " + UIUtils.byte2hex(sendData));

                lock.acquire();
                socket.send(package_send);// uav6. 调用DatagramSocket对象的send方法 发送数据
                lock.release();

            } catch (Exception e) {
                e.printStackTrace();
            }

        }

    }


    public void stop() {
        isRun = false;
        udpManger = null;
        if (socket != null) {
            socket.close();
            socket = null;
        }
    }


    public UdpListen udpListen;
    public interface UdpListen {
        void revUdpData();
        void sendUdpData();
    }
}
