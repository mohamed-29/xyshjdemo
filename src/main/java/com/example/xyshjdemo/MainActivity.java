package com.example.xyshjdemo;

import android.os.Handler;
import android.os.Message;
import android.serialport.SerialPort;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.Queue;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    ListView listView;
    ListAdapter listAdapter;

    Thread mThread;
    SerialPort serialPort;
    String devPath;
    int baudrate;
    int no = 0;
    byte[] ackBytes = new byte[]{(byte) 0xFA,(byte)0xFB,0x42,0x00,0x43};
    private ByteArrayOutputStream mBuffer = new ByteArrayOutputStream();
    Queue<byte[]> queue =  new LinkedList<byte[]>();

    Handler handler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        listView = findViewById(R.id.listView1);
        listAdapter = new ListAdapter();
        listAdapter.setContext(this);
        listView.setAdapter(listAdapter);

        findViewById(R.id.connect).setOnClickListener(this);
        findViewById(R.id.driverhd).setOnClickListener(this);
        findViewById(R.id.querydhstatus).setOnClickListener(this);
    }
    @Override
    public void onClick(View view) {
        switch (view.getId())
        {
            case R.id.connect:{
                if(null==serialPort)
                    bindSerialPort();
                else{
                    try{
                        serialPort.close();
                        serialPort = null;
                        ((Button)view).setText("连接");
                    }
                    catch (Exception e)
                    {

                    }
                }
            }
            break;
            case R.id.driverhd:{
                try {
                    int hdh = Integer.parseInt(((EditText) findViewById(R.id.hdh)).getText().toString());
                    short[] hdhbyte = HexDataHelper.Int2Short16_2(hdh);

                    //货道号补齐两字节
                    if(hdhbyte.length == 1)
                    {
                        short temp = hdhbyte[0];
                        hdhbyte = new short[2];
                        hdhbyte[0]= 0;
                        hdhbyte[1] = temp;
                    }


                    byte[] data  = new byte[]{(byte) 0xFA, (byte) 0xFB, 0x06, 0x05, (byte) getNextNo(), 0x01, 0x00, (byte) hdhbyte[0], (byte) hdhbyte[1], 0x00};
                    data[data.length - 1] = (byte) HexDataHelper.computerXor(data, 0, data.length - 1);
                    queue.add(data);
                }
                catch (Exception e)
                {

                }
            }
            break;
            case R.id.querydhstatus:{
                try {
                    int hdh = Integer.parseInt(((EditText) findViewById(R.id.hdh)).getText().toString());
                    short[] hdhbyte = HexDataHelper.Int2Short16_2(hdh);

                    //货道号补齐两字节
                    if(hdhbyte.length == 1)
                    {
                        short temp = hdhbyte[0];
                        hdhbyte = new short[2];
                        hdhbyte[0]= 0;
                        hdhbyte[1] = temp;
                    }


                    byte[] data  = new byte[]{(byte) 0xFA, (byte) 0xFB, 0x01, 0x03, (byte) getNextNo(), (byte) hdhbyte[0], (byte) hdhbyte[1], 0x00};
                    data[data.length - 1] = (byte) HexDataHelper.computerXor(data, 0, data.length - 1);
                    queue.add(data);
                }
                catch (Exception e)
                {

                }
            }
            break;
            default:{

            }
        }
    }

    public int getNextNo(){
        no++;
        if(no>=255){
            no=0;
        }
        return no;
    }

    public void onSerialPortConnectStateChanged(boolean connected){
        if(connected){
            ((Button)findViewById(R.id.connect)).setText("断开");
        }
        else{
            ((Button)findViewById(R.id.connect)).setText("连接");
        }
    }
    private void  bindSerialPort()
    {
        devPath = ((EditText)findViewById(R.id.dev)).getText().toString();
        baudrate = Integer.parseInt(((EditText)findViewById(R.id.baudrate)).getText().toString());
        mThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File serialFile = new File(devPath);
                    if (!serialFile.exists() || baudrate == -1) {
                        return;
                    }

                    try {
                        serialPort = new SerialPort(serialFile, baudrate, 0);
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                onSerialPortConnectStateChanged(true);
                            }
                        });
                        readSerialPortData();
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                catch (Exception ex)
                {
                    ex.printStackTrace();
                }
                finally
                {
                    if(null!=serialPort){
                        try
                        {
                            serialPort.close();
                            serialPort = null;
                        }
                        catch (Exception e) {
                        }
                    }

                }
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        onSerialPortConnectStateChanged(false);
                    }
                });
            }
        });
        mThread.start();
    }
    private void readSerialPortData()
    {
        while (true)
        {
            try{
                if(null == serialPort){
                    Thread.sleep(1000);
                    continue;
                }
                int available = serialPort.getInputStream().available();
                if(0 == available){
                    Thread.sleep(10);
                    continue;
                }

                byte[] data = readBytes(serialPort.getInputStream(),available);
                mBuffer.write(data);
                while(true) {
                    byte[] bytes = mBuffer.toByteArray();
                    int start = 0;
                    int cmdCount = 0;
                    boolean shuldBreak = false;
                    for(; start<= bytes.length-5; start++)
                    {
                        if((short) (bytes[start] & 0xff)==0xFA&&(short) (bytes[start+1] & 0xff)==0xFB) {
                            try {
                                int len = bytes[start + 3];
                                byte[] cmd = new byte[len + 5];
                                System.arraycopy(bytes, start, cmd, 0, cmd.length);
                                cmdCount++;
                                proccessCmd(cmd);


                                //计算还有多少剩余字节要解析，没有的跳出等待接收新的字节，有则继续处理
                                int remain = bytes.length - start - cmd.length;
                                if(0 == remain)
                                {
                                    shuldBreak = true;
                                    mBuffer.reset();
                                    break;
                                }
                                byte[] buffer2 = new byte[remain];
                                System.arraycopy(bytes, start + cmd.length, buffer2, 0, buffer2.length);
                                mBuffer.reset();
                                mBuffer.write(buffer2);
                            }
                            catch (Exception e)
                            {
                                shuldBreak = true;
                                //因数据包不全，导致越界异常，直接跳出即可
                            }
                            break;
                        }
                    }
                    if(0==cmdCount||shuldBreak)
                    {
                        break;
                    }

                }

            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }
    public byte[] readBytes(InputStream stream, int length) throws IOException {
        byte[] buffer = new byte[length];

        int total = 0;

        while (total < length) {
            int count =stream.read(buffer, total, length - total);
            if (count == -1) {
                break;
            }
            total += count;
        }

        if (total != length) {
            throw new IOException(String.format("Read wrong number of bytes. Got: %s, Expected: %s.", total, length));
        }

        return buffer;
    }
    public void writeCmd(byte[] cmd)
    {
        try{
            serialPort.getOutputStream().write(cmd);
            serialPort.getOutputStream().flush();
            addText(">> "+ HexDataHelper.hex2String(cmd));
        }
        catch (Exception e)
        {

        }
    }
    public void proccessCmd(byte[] cmd)
    {
        addText("<<"+ HexDataHelper.hex2String(cmd));

        if(0x41==(short) (cmd[2] & 0xff)){
            //收到POLL包

            if(queue.size()==0){
                writeCmd(ackBytes);
            }
            else{
                writeCmd(queue.poll());
            }
        }
        else  if(0x42==(short) (cmd[2] & 0xff)){
            // 收到ACK
        }
        else{
            if(0x02==(short) (cmd[2] & 0xff)&&0x04==(short) (cmd[3] & 0xff)) {
                //查询货道状态的返回值
                handler.post(new RunableEx(HexDataHelper.hex2String(cmd)) {
                    public void run() {
                        ((TextView) findViewById(R.id.dhstatus)).setText(text);
                    }
                });
            }
            writeCmd(ackBytes);
        }
    }
    public void addText(String text){
        handler.post(new RunableEx(text) {
            public void run() {
                listAdapter.addText(text);
            }
        });

    }
}
