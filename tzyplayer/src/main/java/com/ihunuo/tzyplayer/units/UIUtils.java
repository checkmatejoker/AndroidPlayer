package com.ihunuo.tzyplayer.units;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import com.coremedia.iso.boxes.Container;
import com.googlecode.mp4parser.FileDataSourceImpl;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder;
import com.googlecode.mp4parser.authoring.tracks.AACTrackImpl;
import com.googlecode.mp4parser.authoring.tracks.h264.H264TrackImpl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.lang.reflect.Method;
import java.nio.channels.FileChannel;
import java.util.ArrayList;

/**
 * 作者:tzy on 2020-06-11.
 * 邮箱:215475174@qq.com
 * 功能介绍:xxx
 */
public class UIUtils {
    private static MediaMetadataRetriever media;

    // /////////////////dip和px转换//////////////////////////


    //bytec转 16H String
    public static String byte2hex(byte[] buffer) {
        String h = "";

        for (int i = 0; i < buffer.length; i++) {
            String temp = Integer.toHexString(buffer[i] & 0xFF);
            if (temp.length() == 1) {
                temp = "0" + temp;
            }
            h = h + " " + temp;
        }
        return h;
    }

    //byte转2进制
    public static String byte2bits(byte b) {
        int z = b;
        z |= 256;
        String str = Integer.toBinaryString(z);
        int len = str.length();
        return str.substring(len - 8, len);
    }


    public static byte[] latlngtobyte(double i) {
        byte b[] = new byte[4];
        int a = (int) (i * 10000000.0f);
        b[0] = (byte) (a >> 24);
        b[1] = (byte) (a >> 16);
        b[2] = (byte) (a >> 8);
        b[3] = (byte) (a & 0x00ff);

        return b;
    }


    /**
     * 判断服务是否开启
     *
     * @return
     */
    public static boolean isServiceRunning(Context context, String ServiceName) {
        if (("").equals(ServiceName) || ServiceName == null)
            return false;
        ActivityManager myManager = (ActivityManager) context
                .getSystemService(Context.ACTIVITY_SERVICE);
        ArrayList<ActivityManager.RunningServiceInfo> runningService = (ArrayList<ActivityManager.RunningServiceInfo>) myManager
                .getRunningServices(30);
        for (int i = 0; i < runningService.size(); i++) {
            if (runningService.get(i).service.getClassName().toString()
                    .equals(ServiceName)) {
                return true;
            }
        }
        return false;
    }

    //获取是否存在NavigationBar
    public static boolean checkDeviceHasNavigationBar(Context context) {
        boolean hasNavigationBar = false;
        Resources rs = context.getResources();
        int id = rs.getIdentifier("config_showNavigationBar", "bool", "android");
        if (id > 0) {
            hasNavigationBar = rs.getBoolean(id);
        }
        try {
            Class systemPropertiesClass = Class.forName("android.os.SystemProperties");
            Method m = systemPropertiesClass.getMethod("get", String.class);
            String navBarOverride = (String) m.invoke(systemPropertiesClass, "qemu.hw.mainkeys");
            if ("1".equals(navBarOverride)) {
                hasNavigationBar = false;
            } else if ("0".equals(navBarOverride)) {
                hasNavigationBar = true;
            }
        } catch (Exception e) {
        }
        return hasNavigationBar;
    }

    //获取虚拟按键的高度
    public static int getNavigationBarHeight(Context context) {
        int result = 0;
        if (checkDeviceHasNavigationBar(context)) {
            Resources res = context.getResources();
            int resourceId = res.getIdentifier("navigation_bar_height", "dimen", "android");
            if (resourceId > 0) {
                result = res.getDimensionPixelSize(resourceId);
            }
        }
        return result;
    }


    public static int getDpi(Context context, int i) {
        int dpi = 0;
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = windowManager.getDefaultDisplay();
        DisplayMetrics displayMetrics = new DisplayMetrics();
        @SuppressWarnings("rawtypes")
        Class c;
        try {
            c = Class.forName("android.view.Display");
            @SuppressWarnings("unchecked")
            Method method = c.getMethod("getRealMetrics", DisplayMetrics.class);
            method.invoke(display, displayMetrics);
            if (i == 0) {
                dpi = displayMetrics.widthPixels;
            } else if (i == 1) {
                dpi = displayMetrics.heightPixels;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return dpi;
    }

    /**
     * 比较两个byte数组数据是否相同,相同返回 true
     *
     * @param data1
     * @param data2
     * @param len
     * @return
     */
    public static boolean memcmp(byte[] data1, byte[] data2, int len) {
        if (data1 == null && data2 == null) {
            return true;
        }
        if (data1 == null || data2 == null) {
            return false;
        }
        if (data1 == data2) {
            return true;
        }

        boolean bEquals = true;
        int i;
        for (i = 0; i < data1.length && i < data2.length && i < len; i++) {
            if (data1[i] != data2[i]) {
                bEquals = false;
                break;
            }
        }

        return bEquals;
    }

    public static void showNormalDialog(final int flag, Context context) {
        /* @setIcon 设置对话框图标
         * @setTitle 设置对话框标题
         * @setMessage 设置对话框消息提示
         * setXXX方法返回Dialog对象，因此可以链式设置属性
         */
        final AlertDialog.Builder normalDialog = new AlertDialog.Builder(context);
//        normalDialog.setTitle("");
        if (flag == 0) {
            normalDialog.setMessage("");
        }

        normalDialog.setPositiveButton("确定",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });
        normalDialog.setNegativeButton("关闭",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                });
        // 显示
        normalDialog.show();
    }

    //读取本地图片
    public static Bitmap filetobitmap(String path) {
        Bitmap bitmap = null;
        if (!path.equals("") && path != null) {
            try {
//                File PHOTO_DIR = new File(path);//设置保存路径
                File avaterFile = new File(path);
                if (avaterFile.exists()) {
                    bitmap = BitmapFactory.decodeFile(path);
                }
            } catch (Exception e) {

            }
        }
        return bitmap;
    }

    /**
     * 删除单个文件
     *
     * @param fileName 要删除的文件的文件名
     * @return 单个文件删除成功返回true，否则返回false
     */
    public static boolean deleteFile(String fileName) {
        File file = new File(fileName);
        // 如果文件路径所对应的文件存在，并且是一个文件，则直接删除
        if (file.exists() && file.isFile()) {
            if (file.delete()) {
                System.out.println("删除单个文件" + fileName + "成功！");
                return true;
            } else {
                System.out.println("删除单个文件" + fileName + "失败！");
                return false;
            }
        } else {
            System.out.println("删除单个文件失败：" + fileName + "不存在！");
            return false;
        }
    }

    //提示音
    public static void startAlarm(Context context) {
        Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        if (notification == null) return;
        Ringtone r = RingtoneManager.getRingtone(context, notification);
        r.play();
    }

    /**
     * 播放系统拍照声音
     */
    public static void shootSound(Context context) {
        MediaPlayer shootMP;
        AudioManager meng = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        int volume = meng.getStreamVolume( AudioManager.STREAM_NOTIFICATION);

        if (volume != 0) {

            shootMP = MediaPlayer.create(context, Uri.parse("file:///system/media/audio/ui/camera_click.ogg"));
            if (shootMP != null)
                shootMP.start();
        }
    }

    public static ContentValues getVideoContentValues(Context paramContext, File paramFile, long paramLong) {
        ContentValues localContentValues = new ContentValues();
        localContentValues.put("title", paramFile.getName());
        localContentValues.put("_display_name", paramFile.getName());
        localContentValues.put("mime_type", "video/mp4");
        localContentValues.put("datetaken", Long.valueOf(paramLong));
        localContentValues.put("date_modified", Long.valueOf(paramLong));
        localContentValues.put("date_added", Long.valueOf(paramLong));
        localContentValues.put("_data", paramFile.getAbsolutePath());
        localContentValues.put("_size", Long.valueOf(paramFile.length()));
        return localContentValues;
    }


    //针对非系统影音资源文件夹
    public static void insertIntoMediaStore(Context context, boolean isVideo, File saveFile, long createTime) {
        ContentResolver mContentResolver = context.getContentResolver();
        if (createTime == 0)
            createTime = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.TITLE, saveFile.getName());
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, saveFile.getName());
        //值一样，但是还是用常量区分对待
        values.put(isVideo
                ? MediaStore.Video.VideoColumns.DATE_TAKEN
                : MediaStore.Images.ImageColumns.DATE_TAKEN, createTime);
        values.put(MediaStore.MediaColumns.DATE_MODIFIED, System.currentTimeMillis());
        values.put(MediaStore.MediaColumns.DATE_ADDED, System.currentTimeMillis());
        if (!isVideo)
            values.put(MediaStore.Images.ImageColumns.ORIENTATION, 0);
        values.put(MediaStore.MediaColumns.DATA, saveFile.getAbsolutePath());
        values.put(MediaStore.MediaColumns.SIZE, saveFile.length());
        values.put(MediaStore.MediaColumns.MIME_TYPE, isVideo ? "video/3gp" : "image/jpeg");
        //插入
        mContentResolver.insert(isVideo
                ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                : MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
    }

    /**
     * 复制单个文件
     *
     * @param oldPath String 原文件路径 如：c:/fqf.txt
     * @param newPath String 复制后路径 如：f:/fqf.txt
     * @return boolean
     */
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

    //针对系统文夹只需要扫描
    public static void scanIntoMediaStore(Context context, File file) {
        Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        intent.setData(Uri.fromFile(file));
        context.sendBroadcast(intent);
    }


    public static Bitmap getVideoBitmap(String path) {
        if (media == null) media = new MediaMetadataRetriever();
        media.setDataSource(path);
        Bitmap bitmap = media.getFrameAtTime();
        return bitmap;
    }

    //    保存文件：（FileOutputStream 保存地址；data/data/包名/files/, 下面是写入的四种模式）
//    MODE_APPEND：即向文件尾写入数据
//    MODE_PRIVATE：即仅打开文件可写入数据
//    MODE_WORLD_READABLE：所有程序均可读该文件数据
//    MODE_WORLD_WRITABLE：即所有程序均可写入数据。

    public static void write(String path, byte[] b, int offset, int lenght) {
        try {
            //判断实际是否有SD卡，且应用程序是否有读写SD卡的能力，有则返回true
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                // 获取SD卡的目录
                File sdCardDir = Environment.getExternalStorageDirectory();
                File dir = new File(sdCardDir + path);

                File targetFile = new File(sdCardDir + path);
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

    public static void H264toMP4(String save_mp4_path,String h264_path,String acc_path,boolean flag){
        FileOutputStream fos = null;
        AACTrackImpl aacTrack = null;
        Movie m;
        try {
            //这里传入的file是指H264格式对应的文件
            H264TrackImpl h264Track = new H264TrackImpl(new FileDataSourceImpl(h264_path));
            if (flag) {
                aacTrack = new AACTrackImpl(new FileDataSourceImpl(acc_path));
                m = new Movie();
                m.addTrack(h264Track);
                m.addTrack(aacTrack);
            }else {
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

    //将bitmap保存为本地文件
    public static boolean bitmaptofile(Bitmap bitmap, String name, String path) {
        Log.d("bbb", "bitmaptofile path:" + path + " name:" + name);
        boolean ret = false;
        File avaterFile;

        File PHOTO_DIR = new File(path);//设置保存路径
        if (!PHOTO_DIR.exists()) {              //如果不存在，那就建立这个文件夹
            boolean b = PHOTO_DIR.mkdirs();
        }
        avaterFile = new File(PHOTO_DIR, name + ".jpeg");//设置文件名称
        if (avaterFile.exists()) {
            avaterFile.delete();
        }
        try {
            avaterFile.createNewFile();
            FileOutputStream fos = new FileOutputStream(avaterFile);
            ret = bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.flush();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return ret;
    }

}

