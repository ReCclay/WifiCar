package com.clay.wificar;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.CountDownTimer;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;

public class MainActivity extends AppCompatActivity {

    Button buttonJump;//第一个界面的跳转按钮

    AlertDialog alertDialogConnect;//定义一个提示框
    View viewConnect;//获取自定义界面
    TextView textViewConnect;
    EditText editTextIP, editTextPort;
    Button buttonCancel, buttonConnect;
    ProgressBar progressBar;

    String stringIP = "";//用户输入的IP地址
    InetAddress ipAddress;//IP对应的主机地址(不必赋初值)
    int port = 0;//端口号
    //连接服务器
    Socket socket = null;//定义socket
    boolean connectServerFlag = true;//连接标志 1-表示接下来要进行连接，此时状态是断开
    OutputStream outputStream=null;//定义输出流
    InputStream inputStream=null;//定义输入流

    private SharedPreferences sharedPreferences;//存储数据
    private SharedPreferences.Editor editor;//存储数据

    int showPointCnt = 0;
    long exitTime1 = 0;//control.java中也有这个退出时间判断的变量，觉得还是不一样的好？突然引发一个问题，java或者说android中变量定义成private、static有啥影响不？还有两个类的变量如果一样，有什么影响不？假如两个类还是继承的关系呢？

    //接收数据
    boolean ReadDataFlage=false;//接收数据标志
    byte[] Readbyte = new byte[1024];
    int ReadbyteLen = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        buttonJump = (Button) findViewById(R.id.button11);
        buttonJump.setOnTouchListener(buttonJumpTouch);
        buttonJump.setOnClickListener(buttonJumpClick);

        /*对话框实现*/
        alertDialogConnect = new AlertDialog.Builder(MainActivity.this).create();
        viewConnect = View.inflate(MainActivity.this, R.layout.dialog, null);
        alertDialogConnect.setView(viewConnect);//设置对话框显示内容

        buttonCancel = (Button) viewConnect.findViewById(R.id.button21);
        buttonConnect = (Button) viewConnect.findViewById(R.id.button22);
        editTextIP = (EditText) viewConnect.findViewById(R.id.editText21);
        editTextPort = (EditText) viewConnect.findViewById(R.id.editText22);
        textViewConnect = (TextView) viewConnect.findViewById(R.id.textView21);
        progressBar = (ProgressBar) viewConnect.findViewById(R.id.progressBar21);
        progressBar.setVisibility(View.INVISIBLE);

        buttonCancel.setOnClickListener(buttonCancelClick);
        buttonConnect.setOnClickListener(buttonConnectClick);

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean issave = sharedPreferences.getBoolean("SAVE", false);//得到save文件存的值，得不到会返回false
        if (issave)
        {
            String string_ip = sharedPreferences.getString("IP", "192.168.4.1");//取出ip,不存在返回192.168.4.1
            String int_port = sharedPreferences.getString("PORT", "8080");//取出端口号,不存在返回8080
            editTextIP.setText(string_ip);
            editTextPort.setText(int_port);
        }
    }

    /***
     * 注意，为何要再加个接收任务呢？通过它来实现从控制界面退出后，如果程序还没退，还可以直接进入控制界面，无需登录。利用是socket，如果与服务器断开连接，那么就是返回-1，所以只需要读取即可判断socket的值！
     * @author 接收消息任务
     *
     */
    class ReadDataThread extends Thread
    {
        public void run()
        {
            while(ReadDataFlage)
            {
                try
                {
                    ReadbyteLen = inputStream.read(Readbyte);
                    if (ReadbyteLen == -1)
                    {
                        Log.e("MainActivity", "接收任务错误");
                        socket = null;
                        ReadDataFlage = false;
                    }
                } catch (IOException e) {
                    Log.e("MainActivity", "接收任务错误");
                    ReadDataFlage = false;
                    socket = null;
                }
            }
        }
    }

    /*第一个界面触摸事件*/
    private View.OnTouchListener buttonJumpTouch = new View.OnTouchListener() {

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            // TODO Auto-generated method stub
            if (event.getAction()==MotionEvent.ACTION_DOWN) {
                buttonJump.setBackgroundResource(R.drawable.button_down);
            }
            if (event.getAction()==MotionEvent.ACTION_UP) {
                buttonJump.setBackgroundResource(R.drawable.button_up);
            }
            return false;
        }
    };

    /*
    第一个界面点击事件
     */
    private View.OnClickListener buttonJumpClick = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            textViewConnect.setText("连接");
            alertDialogConnect.show();

            editTextIP.setFocusable(true);
            editTextIP.setFocusableInTouchMode(true);
            editTextIP.requestFocus();//获取焦点 光标出现
        }
    };

    /*
    第二个界面取消按钮事件
     */
    private View.OnClickListener buttonCancelClick = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            connectServerFlag = false;
            alertDialogConnect.cancel();
            progressBar.setVisibility(View.INVISIBLE);
            Timer.cancel();
        }
    };

    /*
    第二界面连接按钮事件
     */
    private View.OnClickListener buttonConnectClick = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            connectServerFlag = true;
            stringIP = editTextIP.getText().toString().replace(" ", "");//从editTextIP控件获取输入的ip地址，并滤除空格
            port = Integer.valueOf(editTextPort.getText().toString().replace(" ", ""));//editTextPort控件获取端口号，并滤除其中的空格

            Timer.start();
            showPointCnt = 0;
            progressBar.setVisibility(View.VISIBLE);

            ReadDataFlage = false;//注意了，未连接上是false

            ThreadConnectServer threadConnectServer = new ThreadConnectServer();//创建连接任务
            threadConnectServer.start();//启动连接任务

            editor = sharedPreferences.edit();
            editor.putString("IP", stringIP);//记录ip
            editor.putString("PORT", editTextPort.getText().toString());//记录端口号
            editor.putBoolean("SAVE", true);//写入记录标志
            editor.commit();
        }
    };

    /*
    连接服务器线程
     */
    class ThreadConnectServer extends Thread
    {
        public void run()
        {
            while(connectServerFlag) {
                try {
                    ipAddress = InetAddress.getByName(stringIP);//获取IP地址
                    socket = new Socket(ipAddress, port);//创建连接地址和端口

                    connectServerFlag = false;
                    Timer.cancel();
                    alertDialogConnect.cancel();

                    //注意了连接上服务器之后启动数据接收线程相关
                    inputStream = socket.getInputStream();//获取输入流
                    ReadDataFlage = true;
                    ReadDataThread readDataThread = new ReadDataThread();
                    readDataThread.start();

                    Intent intent=new Intent(MainActivity.this, control.class);
                    startActivity(intent);

                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /*
    每隔200ms进入onTick，3000ms后进入onFinsh
    */
    private CountDownTimer Timer = new CountDownTimer(3000,200) {
        @Override
        public void onTick(long millisUntilFinished) {
            if(connectServerFlag){
                showPointCnt++;
                switch (showPointCnt % 9){
                    case 0: textViewConnect.setText("连接"); break;
                    case 1: textViewConnect.setText("连接."); break;
                    case 2: textViewConnect.setText("连接.."); break;
                    case 3: textViewConnect.setText("连接..."); break;
                    case 4: textViewConnect.setText("连接...."); break;
                    case 5: textViewConnect.setText("连接....."); break;
                    case 6: textViewConnect.setText("连接......"); break;
                    case 7: textViewConnect.setText("连接......."); break;
                    case 8: textViewConnect.setText("连接........"); break;
                }
            }
        }

        @Override
        public void onFinish()
        {
            if(connectServerFlag){
                connectServerFlag = false;
                textViewConnect.setText("连接服务器失败！");
                progressBar.setVisibility(View.INVISIBLE);
            }
            Timer.cancel();
        }
    };

    /***
     * 手机返回按钮
     */
    public boolean onKeyDown(int keyCode,KeyEvent event)
    {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN) {
            // 判断间隔时间 大于2秒就退出应用
            if ((System.currentTimeMillis() - exitTime1) > 2000)
            {
                Toast.makeText(getApplicationContext(), "再按一次退出程序",Toast.LENGTH_SHORT).show();
                exitTime1 = System.currentTimeMillis();
            }
            else//注意，虽然注销也会自己关闭这些东西，但是还是处于程序的严谨性，加上比较好！
            {
                try
                {
                    if (socket!=null)
                    {
                        socket.close();
                    }
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
                try
                {
                    if (outputStream!=null)
                    {
                        outputStream.close();
                    }
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
                try
                {
                    if (inputStream!=null)
                    {
                        inputStream.close();
                    }
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
                MainActivity.this.finish();
            }
            return false;
        }
        return false;
    }

}
