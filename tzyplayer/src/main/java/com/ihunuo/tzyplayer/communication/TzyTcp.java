package com.ihunuo.tzyplayer.communication;

import android.util.Log;

import com.ihunuo.tzyplayer.lisrener.TcpLister;
import com.ihunuo.tzyplayer.units.UIUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 作者:tzy on 2020-06-11.
 * 邮箱:215475174@qq.com
 * 功能介绍:基本的tcp通讯可以用来交互数据或者读取传送的code值
 */
public class TzyTcp extends Thread{
    private String TAG = "TzyTcp";
    private String ip = "192.168.39.1";
    private int port = 6468;

    private Socket socket = null;
    private OutputStream write = null;
    private InputStream read = null;

    public boolean isRead, isWrite;
    private TcpLister tcpLister;



    public TzyTcp(TcpLister mytcplister) {
        this.tcpLister = mytcplister;
    }

    public TzyTcp(String ip, int port, TcpLister mytcpLister) {
        this.ip = ip;
        this.port = port;
        this.tcpLister = mytcpLister;
    }

    @Override
    public void run() {
        try {
            isRead = true;
            socket = new Socket();

            socket.connect(new InetSocketAddress(ip,port));

            write = socket.getOutputStream();
            read = socket.getInputStream();
            writeThread.start();
            if (socket != null && write != null && read != null)
                tcpLister.creatTcpSusscced();
            while (isRead) {
                byte[] readBuf = new byte[1024];
                if (read != null) {
                    int ret = read.read(readBuf);
                    if (ret < 0) {
                        isRead = false;
                        Log.d(TAG, "run: tcp Read false;");
                        break;
                    }
                    Log.d(TAG, "rev: " + readBuf);
                    if (tcpLister!=null)
                    {
                        tcpLister.reviceData(readBuf);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            close();
        }
    }

    public void send(final byte[] buf) {
        sendBufQueue.add(buf);
        onResume();
    }

    public Queue sendBufQueue = new ConcurrentLinkedQueue();//缓存队列
    private Object mPauseLock = new Object();
    private boolean mPauseFlag = false;
    Thread writeThread = new Thread(new Runnable() {
        @Override
        public void run() {
            isWrite = true;
            while (isWrite) {
                try {
                    if (sendBufQueue != null && write != null && sendBufQueue.size() != 0) {
                        byte[] writebuf = (byte[]) sendBufQueue.poll();
                        Log.d(TAG, "run:  write" + UIUtils.byte2hex(writebuf));
                        write.write(writebuf);
                        write.flush();
                    } else {
                        Log.e(TAG, "run: sendBufQueue.size()=" + sendBufQueue.size());
                        onPause();
                        pauseThread();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    });

    public void onPause() {
        synchronized (mPauseLock) {
            mPauseFlag = true;
        }
    }

    public void onResume() {
        synchronized (mPauseLock) {
            mPauseFlag = false;
            mPauseLock.notifyAll();
        }
    }

    private void pauseThread() {
        synchronized (mPauseLock) {
            if (mPauseFlag) {
                try {
                    mPauseLock.wait();
                } catch (Exception e) {
                    Log.v("thread", "fails");
                }
            }
        }
    }



    public void close() {
        isRead = false;
        isWrite = false;
        try {
            if (socket != null) {
                interrupt();
                read.close();
                write.close();
                read = null;
                write = null;
                socket.close();
                socket = null;
                Log.d(TAG, "close: socket");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}
