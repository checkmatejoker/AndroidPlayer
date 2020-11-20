package com.ihunuo.androidplayer;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.ihunuo.tzyplayer.DeviceH264Manager;
import com.ihunuo.tzyplayer.lisrener.Decodelister;
import com.ihunuo.tzyplayer.surfaceviews.YUVSurfaceView;
import com.ihunuo.tzyplayer.units.UIUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Timer;
import java.util.TimerTask;

import static com.ihunuo.tzyplayer.units.UIUtils.scanIntoMediaStore;

public class MainActivity extends AppCompatActivity {

    YUVSurfaceView yuvSurfaceView ;
    Button mphot;
    Button mrecoder;
    TextView recodetext;
    DeviceH264Manager deviceH264Manager;
    boolean isrecode =false;
    public Timer mCountTimeTask;
    private static int recordTimeCount = 0;
    Handler mhandler = new Handler();

    public  static   String PICTRUEPATH ="";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        verifyStoragePermissions(this);
        PICTRUEPATH = Environment.getExternalStorageDirectory() + "/MyPlayer";
        File f = new File(PICTRUEPATH);
        if (!f.exists()) {
            f.mkdirs();
        }
        yuvSurfaceView = findViewById(R.id.yuvsurfaceview);
        mphot= findViewById(R.id.photho);
        mrecoder= findViewById(R.id.luxiang);
        recodetext = findViewById(R.id.textrecodtime);
        deviceH264Manager = new DeviceH264Manager(this);
        deviceH264Manager.setVideoPath(PICTRUEPATH);
        recodetext.setVisibility(View.GONE);
        mphot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                deviceH264Manager.takePhoto();
                Toast.makeText(MainActivity.this,"拍照成功",Toast.LENGTH_LONG);
                UIUtils.shootSound(MainActivity.this);
            }
        });
        mrecoder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                isrecode =!isrecode;
                if (isrecode)
                {
                    mrecoder.setText("停止");
                    UIUtils.startAlarm(MainActivity.this);
                    recodetext.setVisibility(View.VISIBLE);
                    deviceH264Manager.recodervodie(isrecode,1280,720);
                    timercountTask();
                }
                else
                {
                    mrecoder.setText("开始");
                    recodetext.setVisibility(View.GONE);
                    deviceH264Manager.recodervodie(isrecode,1280,720);
                    clanccounttimerTask();
                }

            }
        });


        deviceH264Manager.start(yuvSurfaceView, new Decodelister() {
            @Override
            public void firstDecode() {

            }

            @Override
            public void decodeYUV(byte[] yuv, int width, int hieght) {
                yuvSurfaceView.setYUVData(yuv, width, hieght);
            }

            @Override
            public void takePhoto(Bitmap bmp) {
                if ( bmp != null) {
                    String ss1 = MainActivity.PICTRUEPATH+"/"+System.currentTimeMillis()+".jpeg";
                    String ss2 =Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath()+"/Camera/"+System.currentTimeMillis()+".jpeg";
                    File  file = new File(ss1);
                    if(file.exists()){
                        file.delete();
                    }
                    FileOutputStream out;
                    try{
                        out = new FileOutputStream(file);
                        // 格式为 JPEG，照相机拍出的图片为JPEG格式的，PNG格式的不能显示在相册中
                        if(bmp.compress(Bitmap.CompressFormat.JPEG, 90, out))
                        {
                            out.flush();
                            out.close();
                        }
                    }
                    catch (FileNotFoundException e)
                    {
                        e.printStackTrace();
                    }
                    catch (IOException e)
                    {
                        e.printStackTrace();

                    }
                    copyFile(ss1,ss2,MainActivity.this);

                }
            }

            @Override
            public void redata(byte[] data, int len) {

            }
        });

    }


    public void timercountTask() {
        //创建定时线程执行更新任务
        clanccounttimerTask();
        mCountTimeTask = new Timer();
        mCountTimeTask.schedule(new TimerTask() {
            @Override
            public void run() {
                mhandler.post(new Runnable() {
                    @Override
                    public void run() {

                        recordTimeCount++;
                        recodetext.setText(formatTime(recordTimeCount)+"");
                    }
                });

            }
        }, 1000, 1000);// 定时任务
    }

    public void clanccounttimerTask()
    {
        if(mCountTimeTask!=null){
            recordTimeCount =0;
            mCountTimeTask.cancel();// 退出之前的mTimer

        }
    }

    private String formatTime(int time) {
        int hour;
        int minute;
        int second;
        if (time > 3600) {
            hour = time / 3600;
            minute = (time % 3600) / 60;
            second = (time % 3600) % 60;
            return String.format("%02d:%02d:%02d", hour, minute, second);
        } else {
            minute = time / 60;
            second = time % 60;
            return String.format("%02d:%02d", minute, second);
        }
    }
    public static void copyFile(String oldPath, String newPath, Context context) {
        try {
            int bytesum = 0;
            int byteread = 0;
            File oldfile = new File(oldPath);
            if (oldfile.exists()) { //文件存在时
                InputStream inStream = new FileInputStream(oldPath); //读入原文件
                FileOutputStream fs = new FileOutputStream(newPath);
                byte[] buffer = new byte[1444];
                int length;
                while ((byteread = inStream.read(buffer)) != -1) {
                    bytesum += byteread; //字节数 文件大小
                    System.out.println(bytesum);
                    fs.write(buffer, 0, byteread);
                }
                inStream.close();
                File f = new File(newPath);
                scanIntoMediaStore(context, f);
            }
        } catch (Exception e) {
            System.out.println("复制单个文件操作出错");
            e.printStackTrace();

        }

    }


    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.ACCESS_COARSE_LOCATION",};

    public static boolean verifyStoragePermissions(Activity activity) {

        try {
            //检测是否有写的权限
            int permission = ActivityCompat.checkSelfPermission(activity,
                    "android.permission.WRITE_EXTERNAL_STORAGE");
            if (permission != PackageManager.PERMISSION_GRANTED) {
                // 没有写的权限，去申请写的权限，会弹出对话框
                ActivityCompat.requestPermissions(activity, PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE);
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }
}
