package com.clay.wificar;

import android.app.Service;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.nio.ByteBuffer;

public class control extends MainActivity {//注意，这个类是继承MainActivity的！！！需要使用输出流outputStream

    private static final String TAG = "control";

    ProgressBar progressBarSpeed;
    CheckBox checkBoxGravity;
    SensorManager sensorManager;
    Sensor sensor;
    float X_lateral;//X方向角度
    int Speed=0;//速度
    TextView textViewSpeed;//显示速度值

    ImageButton imageButton31;//前进
    ImageButton imageButton32;//后退
    ImageButton imageButton33;//右转
    ImageButton imageButton34;//左转
    boolean forward = false;
    boolean back = false;
    boolean right = false;
    boolean left = false;

    Vibrator vibrator;//按钮按下震动

    byte[] sendbyte = new byte[4];//发送的数据缓存
    boolean SendDataFlag = true;//发送数据任务控制
    SendMsgThread sendMsgThread;//发送数据任务
    boolean stopcar = false;//执行一次发送停车数据

    static long exitTime = 0;//按键计时

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.control);

        checkBoxGravity = (CheckBox) findViewById(R.id.checkBox31);
        progressBarSpeed = (ProgressBar) findViewById(R.id.progressBar31);
        textViewSpeed = (TextView) findViewById(R.id.textView33);
        checkBoxGravity.setOnCheckedChangeListener(checkBoxGravityCheckedChangeListener);


        imageButton31 = (ImageButton) findViewById(R.id.imageButton31);//前进
        imageButton32 = (ImageButton) findViewById(R.id.imageButton32);//后退
        imageButton33 = (ImageButton) findViewById(R.id.imageButton33);//右转
        imageButton34 = (ImageButton) findViewById(R.id.imageButton34);//左转
        imageButton31.setOnTouchListener(imageButton31Touch);//前进
        imageButton32.setOnTouchListener(imageButton32Touch);//后退
        imageButton33.setOnTouchListener(imageButton33Touch);//右转
        imageButton34.setOnTouchListener(imageButton34Touch);//左转

        vibrator=(Vibrator)getSystemService(Service.VIBRATOR_SERVICE);//震动
    }

    /***
     * 前进按钮
     */
    private View.OnTouchListener imageButton31Touch = new View.OnTouchListener() {

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            // TODO Auto-generated method stub
            if (event.getAction()==MotionEvent.ACTION_DOWN) {
                forward = true;
                back=false;
                imageButton31.setImageResource(R.drawable.advance_down);

                //根据指定的模式进行震动
                //第一个参数：该数组中第一个元素是等待多长的时间才启动震动，第二个元素是震动时间(ms)
                //第二个参数：重复震动时在pattern中的索引，如果设置为-1则表示不重复震动
                vibrator.vibrate(new long[]{0,20}, -1);//震动
            }
            if (event.getAction()==MotionEvent.ACTION_UP) {
                forward = false;
                imageButton31.setImageResource(R.drawable.advance_up);
            }
            return false;
        }
    };

    /***
     * 后退按钮
     */
    private View.OnTouchListener imageButton32Touch = new View.OnTouchListener() {

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            // TODO Auto-generated method stub
            if (event.getAction()==MotionEvent.ACTION_DOWN) {
                back=true;
                forward=false;
                imageButton32.setImageResource(R.drawable.back_down);
                vibrator.vibrate(new long[]{0,20}, -1);
            }
            if (event.getAction()==MotionEvent.ACTION_UP) {
                back=false;
                imageButton32.setImageResource(R.drawable.back_up);
            }
            return false;
        }
    };

    /***
     * 右转按钮
     */
    private View.OnTouchListener imageButton33Touch = new View.OnTouchListener() {

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            // TODO Auto-generated method stub
            if (event.getAction()==MotionEvent.ACTION_DOWN) {
                right=true;
                left=false;
                imageButton33.setImageResource(R.drawable.right_down);
                vibrator.vibrate(new long[]{0,20}, -1);
            }
            if (event.getAction()== MotionEvent.ACTION_UP) {
                right=false;
                imageButton33.setImageResource(R.drawable.right_up);
            }
            return false;
        }
    };

    /***
     * 左转按钮
     */
    private View.OnTouchListener imageButton34Touch = new View.OnTouchListener() {

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            // TODO Auto-generated method stub
            if (event.getAction()==MotionEvent.ACTION_DOWN) {
                left=true;
                right=false;
                imageButton34.setImageResource(R.drawable.left_down);
                vibrator.vibrate(new long[]{0,20}, -1);
            }
            if (event.getAction()==MotionEvent.ACTION_UP) {
                left=false;
                imageButton34.setImageResource(R.drawable.left_up);
            }
            return false;
        }
    };

    /**
     *左转大于右转大于后退大于前进
     *(单个按钮)谁按下执行谁
     *
     */
    class SendMsgThread extends Thread
    {
        public void run()
        {
            while(SendDataFlag)
            {
                sendbyte[0] = (byte)0xaa;
                sendbyte[1] = (byte)0x55;
                if (!checkBoxGravity.isChecked()) {//没有打开重力传感器速度默认50
                    sendbyte[3] = 50;
                }
                if (forward) {//前进
                    sendbyte[2] = (byte)0x01;
                }
                if (back) {//后退
                    sendbyte[2] = (byte)0x02;
                }
                if (right) {//右转
                    sendbyte[2] = (byte)0x03;
                }
                if (left) {//左转
                    sendbyte[2] = (byte)0x04;
                }

                if (forward || back || right || left) //有按下的按钮
                {
                    stopcar = true;//有过按钮操作
                    netSend(sendbyte);
                }
                else//没有按下的按钮发送一次停车指令
                {
                    if (stopcar) //有过按钮操作
                    {
                        stopcar = false;
                        sendbyte[2] = (byte)0x05;
                        sendbyte[3] = (byte)0x00;
                        netSend(sendbyte);
                    }
                }

                try {
                    Thread.sleep(100);//延时100ms
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 发送数据
     * @param byt
     */
    private void netSend(byte[] byt)
    {
        int crc = 0;
        ByteBuffer Crcbyte = ByteBuffer.allocate(4);//创建4个字节的  ByteBuffer

        byte[] sendbyte = new byte[byt.length + 2];//后面加2是原来存储CRC

        for (int i = 0; i < byt.length; i++)//copy数据
        {
            sendbyte[i] = byt[i];
        }

        crc = crc16_modbus(byt, byt.length);//计算CRC
        Crcbyte.putInt(crc);//把int转成byte--默认是转成4个字节的,,所以上面定义了4个字节的↑↑

        sendbyte[sendbyte.length - 2] = Crcbyte.get(3);//低位在前----java看来默认的大端模式存储数据
        sendbyte[sendbyte.length - 1] = Crcbyte.get(2);//高位在后


        try
        {
            outputStream = socket.getOutputStream();
            outputStream.write(sendbyte);
        }
        catch (IOException e)
        {
            SendDataFlag = false;
            socket = null;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // TODO Auto-generated method stub
                    Toast.makeText(getApplicationContext(), "与服务器断开连接,请重新连接", Toast.LENGTH_SHORT).show();
                }
            });
            Intent intent = new Intent(control.this, MainActivity.class);
            startActivity(intent);

        }
    }

    /***
     * 单选框事件
     */
    private CompoundButton.OnCheckedChangeListener checkBoxGravityCheckedChangeListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            // TODO Auto-generated method stub
            if (isChecked)
            {
                sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);//获取手机里面的传感器
                sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);//选择获取重力传感器
                //监听函数                        重力传感器对象              工作频率
                sensorManager.registerListener(mySensorEventListener, sensor,  SensorManager.SENSOR_DELAY_NORMAL);// SENSOR_DELAY_GAME
            }
            else
            {
                sensorManager.unregisterListener(mySensorEventListener);//释放传感器
            }
        }
    };

    /**
     * 重力传感器监听事件
     */
    SensorEventListener mySensorEventListener = new SensorEventListener() {

        @Override
        public void onSensorChanged(SensorEvent event) {
            // TODO Auto-generated method stub
            if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
            {
                X_lateral = event.values[0]+10; //把-10到10的数据变为0-20
                Speed = (int)((100-(X_lateral*10/2))*2);//变为0-200
                if (Speed>100) {
                    Speed = 100;
                }
                textViewSpeed.setText(String.valueOf(Speed));

                runOnUiThread(new Runnable() {
                    public void run()
                    {
                        progressBarSpeed.setProgress(Speed);
                    }
                });

//                Log.e(TAG, event.values[0]+"" );
            }
            else {
                sensorManager.unregisterListener(mySensorEventListener);
                runOnUiThread(new Runnable() {
                    public void run() {
                        checkBoxGravity.setChecked(false);
                        Toast.makeText(getApplicationContext(), "传感器不存在!!!", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // TODO Auto-generated method stub

        }
    };


    /**
     * CRC检验值
     * @param modbusdata
     * @param length
     * @return CRC检验值
     */
    protected int crc16_modbus(byte[] modbusdata, int length)
    {
        int i=0, j=0;

        int crc = 0;//WIFI的值是0，有些仪器仪表是0xFFFF

        try
        {
            for (i = 0; i < length; i++)
            {
                crc ^= (modbusdata[i]&(0xff));//注意这里要&0xff，原因http://bbs.csdn.net/topics/260061974
                for (j = 0; j < 8; j++)
                {
                    if ((crc & 0x01) == 1)
                    {
                        crc = (crc >> 1) ;
                        crc = crc ^ 0xa001;
                    }
                    else
                    {
                        crc >>= 1;
                    }
                }
            }
        }
        catch (Exception e)
        {

        }

        return crc;
    }

    /**
     * CRC校验正确标志 注意，这个函数在本程序实际没有用到！！！
     * @param modbusdata
     * @param length
     * @return 0-failed 1-success
     */
    protected int crc16_flage(byte[] modbusdata, int length)
    {
        int Receive_CRC = 0, calculation = 0;//接收到的CRC,计算的CRC

        Receive_CRC = crc16_modbus(modbusdata, length);
        calculation = modbusdata[length + 1];
        calculation <<= 8;
        calculation += modbusdata[length];
        if (calculation != Receive_CRC)
        {
            return 0;
        }
        return 1;
    }

    public boolean onKeyDown(int keyCode,KeyEvent event)
    {
        if (keyCode == KeyEvent.KEYCODE_BACK
                && event.getAction() == KeyEvent.ACTION_DOWN) {
            // 判断间隔时间 大于2秒就退出应用
            if ((System.currentTimeMillis() - exitTime) > 2000) {
                Toast.makeText(getApplicationContext(), "再按一次返回连接界面",
                        Toast.LENGTH_SHORT).show();
                exitTime = System.currentTimeMillis();
            }
            else
            {
                Intent intent = new Intent(control.this, MainActivity.class);
                startActivity(intent);
            }
            return false;
        }
        return false;
    }

    /*
    界面一部分不可见时！
     */
    protected void onPause() {
        super.onPause();
        if(sensorManager != null) {
            sensorManager.unregisterListener(mySensorEventListener);
        }
    }
}
