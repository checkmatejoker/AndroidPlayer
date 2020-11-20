# AndroidPlayer
## 一.项目介绍：
本项目是接受h264流后显示在手机端，并且添加可以拍照，录像的小例子

## 二.支持版本
android4.3以上，项目配置如下
```
compileSdkVersion 29

minSdkVersion 21

argetSdkVersion 29
```

## 三.接入方式
在需要使用的模块中引入
```
implementation 'com.tzyandroid:player:1.0.6'
```
## 四.使用方法
### 步骤一 布局文件中使用YUVSurfaceView：
在布局文件中布局YUVSurfaceView这是一个通过yuv数据进行图像显示的SurfaceView
```
  <com.ihunuo.tzyplayer.surfaceviews.YUVSurfaceView
      android:id="@+id/yuvsurfaceview"
      android:layout_width="match_parent"
      android:layout_height="match_parent"></com.ihunuo.tzyplayer.surfaceviews.YUVSurfaceView>
           
```

### 步骤二 初始化管理类DeviceH264Manager 
DeviceH264Manager 是处理解析h264的流的以及对外暴露接口的主要类，其使用方法为
```
1.初始化 
  deviceH264Manager = new DeviceH264Manager(this);在所属activity进行初始化

2.设置录像拍照路径 
  deviceH264Manager.setVideoPath(PICTRUEPATH);
  
3.与YUVSurfaceView关联并开始监听UDP端口进行数据解析
  deviceH264Manager.start(yuvSurfaceView, new Decodelister()  
```
### 步骤三 设置监听数据类已经相关逻辑处
Decodelister 是解析h264的回调处理类其暴露的接口为
```
  1.public void firstDecode() //第一次解析到完整H264帧会发生回调

  2.public void decodeYUV(byte[] yuv, int width, int hieght) // 解析h264成功转化为YUV数据处理显示

  3.public void takePhoto(Bitmap bmp) // 拍照回调

  4.public void redata(byte[] data, int len)//预留 作为传输数据暴露的接口

```
 ### 步骤四 拍照
 ```
  deviceH264Manager.takePhoto();
  Toast.makeText(MainActivity.this,"拍照成功",Toast.LENGTH_LONG);
  UIUtils.shootSound(MainActivity.this);
 ```
 ### 步骤四 录像
 ```
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
 ```
 ## 五，类以及方法说明
 ```
 YUVSurfaceView 
 功能：用于显示YUV数据的SurfaceView 
 主要方法： 
 setYUVData(yuv, width, hieght);//注意这里的宽高是解码出来原始的宽高，有数据源决定
 ```
 ```
 Decodelister 
 功能：解析数据时的回调类
 主要方法：
  1.public void firstDecode() //第一次解析到完整H264帧会发生回调

  2.public void decodeYUV(byte[] yuv, int width, int hieght) // 解析h264成功转化为YUV数据处理显示

  3.public void takePhoto(Bitmap bmp) // 拍照回调

  4.public void redata(byte[] data, int len)//预留 作为传输数据暴露的接口
 ```
  ```
 DeviceH264Manager 
 功能：控制开流，拍照，录像，传输解析数据的主要类
 主要方法： 
 1.public void start(YUVSurfaceView myyuvSurfaceView, Decodelister mydecodelister) //绑定显示View，设置回调

 2.public void takePhoto()//拍照

 3.public void recodervodie(boolean isrecoder,int width,int height);//录像宽高可以自定义，做了默认的差值处理

```
 ## 六，项目计划
 目前该项目实现了的开流，拍照，录像，后续功能会持续更新，欢迎提出宝贵的意见。我的邮箱215475174@qq.com，欢迎大家互相交流学习








