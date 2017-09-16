package uniandes.disc.imagine.robotarm_app.teleop.interfaces;

import android.app.AlertDialog;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ToggleButton;

import org.ros.android.BitmapFromCompressedImage;
import org.ros.android.NodeMainExecutorService;
import org.ros.android.RosActivity;
import org.ros.android.view.RosImageView;
import org.ros.node.NodeConfiguration;
import org.ros.node.NodeMainExecutor;

import java.math.RoundingMode;
import java.net.URI;
import java.text.DecimalFormat;

import sensor_msgs.CompressedImage;
import uniandes.disc.imagine.robotarm_app.teleop.MainActivity;
import uniandes.disc.imagine.robotarm_app.teleop.R;
import uniandes.disc.imagine.robotarm_app.teleop.topic.Float32Topic;
import uniandes.disc.imagine.robotarm_app.teleop.topic.Int32Topic;
import uniandes.disc.imagine.robotarm_app.teleop.topic.TwistTopic;
import uniandes.disc.imagine.robotarm_app.teleop.touchscreen.StandardGestureDetector;
import uniandes.disc.imagine.robotarm_app.teleop.utils.AndroidNode;
import uniandes.disc.imagine.robotarm_app.teleop.utils.MjpegInputStream;
import uniandes.disc.imagine.robotarm_app.teleop.utils.MjpegView;
import uniandes.disc.imagine.robotarm_app.teleop.utils.UDPComm;
import uniandes.disc.imagine.robotarm_app.teleop.widget.CustomVirtualJoystickView;
import uniandes.disc.imagine.robotarm_app.teleop.widget.ScrollerView;


public class ManipulationInterfaces extends RosActivity implements SensorEventListener {

    private static final String TAG = "ManipulationInterfaces";
    private static final String NODE_NAME="/android_"+TAG.toLowerCase();

    private NodeMainExecutorService nodeMain;
    private StandardGestureDetector standardGestureDetector = null;

    private RosImageView<CompressedImage> nodeMainImageStream;
    private CustomVirtualJoystickView nodeMainVirtualJoystick01=null;
    private CustomVirtualJoystickView nodeMainVirtualJoystick02=null;
    private ScrollerView scroller = null;

    private ToggleButton toggleStart;
    private ToggleButton toggleCamera1;
    private ToggleButton toggleCamera2;

    private Button buttonEndEffectorPose1;
    private Button buttonEndEffectorPose2;

    private TextView virtualJoystickTitle01;
    private TextView virtualJoystickTitle02;
    private TextView scrollerTitle;
    private TextView messageView;

    private AndroidNode androidNode;
    private Float32Topic endeffector_graspTopic;
    private TwistTopic endeffector_navTopic;
    private TwistTopic user_measuresTopic;
    private Int32Topic endeffector_presetTopic;
    private Int32Topic camera_selectionTopic;
    private Int32Topic interface_numberTopic;
    private Float32Topic target_distanceTopic;

    private UDPComm udpCommCommand;
    private MjpegView mjpegView;
    private boolean isRunning = true;
    private boolean isShowingMsg = false;
    private boolean isRecording = false;
    private long timeMeasurement;
    private String userNumber;

    private static final float NS2S = 1.0f / 1000000000.0f;
    private static final float MS2S = 1.0f / 1000.0f;
    private static final float MAXRADSPS = 20.f / 57.3f ; //10 degrees per second
    private float deviceRotX, deviceRotY, deviceRotZ;
    private float deviceAccX, deviceAccY, deviceAccZ;
    private float prev_deviceAccX, prev_deviceAccY, prev_deviceAccZ;
    private float ref_deviceRotX, ref_deviceRotY, ref_deviceRotZ;
    private float ref_deviceAccX, ref_deviceAccY, ref_deviceAccZ;
    private boolean firstPress=true;

    private float nanotimestamp_gyro;
    private float nanotimestamp_acce;
    private int datarate = 20;

    private SensorManager sensorManager;
    private Sensor gyroscope, accelerometer;

    private int MAN_INTERFACE;
    private int INTERFACE_01;
    private int INTERFACE_02;
    private int INTERFACE_03;

    public ManipulationInterfaces() {
        super(TAG, TAG, URI.create(MainActivity.PREFERENCES.getProperty("ROS_MASTER_URI")));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        androidNode = new AndroidNode(NODE_NAME);
        INTERFACE_01 = Integer.parseInt(getString(R.string.interface_manipulation_01_number));
        INTERFACE_02 = Integer.parseInt(getString(R.string.interface_manipulation_02_number));
        INTERFACE_03 = Integer.parseInt(getString(R.string.interface_manipulation_03_number));

        deviceRotX = 0.f; deviceRotY = 0.f; deviceRotZ = 0.f;
        deviceAccX = 0.f; deviceAccY = 0.f; deviceAccZ = 0.f;
        ref_deviceRotX = 0.f; ref_deviceRotY = 0.f; ref_deviceRotZ = 0.f;
        ref_deviceAccX = 0.f; ref_deviceAccY = 0.f; ref_deviceAccZ = 0.f;
        prev_deviceAccX = 0.f; prev_deviceAccY = 0.f; prev_deviceAccZ = 0.f;

        if (MainActivity.PREFERENCES.containsKey((getString(R.string.manipulation01))))
            MAN_INTERFACE = INTERFACE_01;
        if (MainActivity.PREFERENCES.containsKey((getString(R.string.manipulation02))))
            MAN_INTERFACE = INTERFACE_02;
        if (MainActivity.PREFERENCES.containsKey((getString(R.string.manipulation03))))
            MAN_INTERFACE = INTERFACE_03;

        isRecording=MainActivity.PREFERENCES.containsKey((getString(R.string.record)));
        userNumber=MainActivity.PREFERENCES.getProperty(getString(R.string.user));

        //Intent intent = getIntent();
        setContentView(R.layout.manipulation_interfaces);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        nodeMainImageStream = (RosImageView<CompressedImage>) findViewById(R.id.streamingView);
        nodeMainImageStream.setTopicName(getString(R.string.topic_streaming));
        nodeMainImageStream.setMessageType(getString(R.string.topic_streaming_msg));
        nodeMainImageStream.setMessageToBitmapCallable(new BitmapFromCompressedImage());
        nodeMainImageStream.setScaleType(ImageView.ScaleType.FIT_CENTER);

        mjpegView = (MjpegView) findViewById(R.id.mjpegView);
        mjpegView.setDisplayMode(MjpegView.SIZE_BEST_FIT);
        mjpegView.showFps(true);

        nodeMainVirtualJoystick01 = (CustomVirtualJoystickView) findViewById(R.id.virtual_joystick_01);
        nodeMainVirtualJoystick02 = (CustomVirtualJoystickView) findViewById(R.id.virtual_joystick_02);
        scroller = (ScrollerView) findViewById(R.id.scrollerView);
        messageView = (TextView) findViewById(R.id.textViewMessage);

        toggleStart = (ToggleButton) findViewById(R.id.toggleStart);
        toggleCamera1 = (ToggleButton) findViewById(R.id.toggleCamera1);
        toggleCamera2 = (ToggleButton) findViewById(R.id.toggleCamera2);

        buttonEndEffectorPose1 = (Button) findViewById(R.id.buttonEndEffectorPose1);
        buttonEndEffectorPose2 = (Button) findViewById(R.id.buttonEndEffectorPose2);

        virtualJoystickTitle01 = (TextView) findViewById(R.id.scrollerTextView01);
        virtualJoystickTitle02 = (TextView) findViewById(R.id.scrollerTextView02);
        scrollerTitle = (TextView) findViewById(R.id.scrollTextView);


        if (MAN_INTERFACE ==INTERFACE_01){
            nodeMainVirtualJoystick01.setHolonomic(true);
            nodeMainVirtualJoystick02.setHolonomic(true);
            nodeMainVirtualJoystick01.setVisibility(View.VISIBLE);
            nodeMainVirtualJoystick02.setVisibility(View.VISIBLE);
            virtualJoystickTitle01.setVisibility(View.VISIBLE);
            virtualJoystickTitle02.setVisibility(View.VISIBLE);
            scrollerTitle.setVisibility(View.VISIBLE);
            scroller.setVisibility(View.VISIBLE);
            scroller.setTopValue(0.f);
            scroller.setBottomValue(0.1f);
            scroller.setFontSize(13);
            scroller.setMaxTotalItems(3);
            scroller.setMaxVisibleItems(3);
            scroller.beginAtTop();
        }
        if (MAN_INTERFACE ==INTERFACE_02){

            nodeMainVirtualJoystick01.setVisibility(View.GONE);
            nodeMainVirtualJoystick02.setVisibility(View.GONE);
            virtualJoystickTitle01.setVisibility(View.GONE);
            virtualJoystickTitle02.setVisibility(View.GONE);
            scroller.setVisibility(View.GONE);
            scrollerTitle.setVisibility(View.GONE);

            if ( MainActivity.PREFERENCES.containsKey((getString(R.string.ros_cimage))) ) {
                standardGestureDetector = new StandardGestureDetector(this, nodeMainImageStream);
            }else if( MainActivity.PREFERENCES.containsKey((getString(R.string.mjpeg))) ){
                standardGestureDetector = new StandardGestureDetector(this, mjpegView);
            }

        }
        if (MAN_INTERFACE ==INTERFACE_03){

            nodeMainVirtualJoystick01.setVisibility(View.GONE);
            //nodeMainVirtualJoystick02.setVisibility(View.INVISIBLE);
            virtualJoystickTitle01.setVisibility(View.GONE);
            virtualJoystickTitle02.setVisibility(View.GONE);

            nodeMainVirtualJoystick02.setHolonomic(true);
            nodeMainVirtualJoystick02.setVisibility(View.VISIBLE);
            virtualJoystickTitle02.setVisibility(View.VISIBLE);

            sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
            sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_FASTEST);
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
            scrollerTitle.setVisibility(View.VISIBLE);
            scroller.setVisibility(View.VISIBLE);
            scroller.setTopValue(0.f);
            scroller.setBottomValue(0.1f);
            scroller.setFontSize(13);
            scroller.setMaxTotalItems(3);
            scroller.setMaxVisibleItems(3);
            scroller.beginAtTop();

            if ( MainActivity.PREFERENCES.containsKey((getString(R.string.ros_cimage))) ) {
                standardGestureDetector = new StandardGestureDetector(this, nodeMainImageStream);
            }else if( MainActivity.PREFERENCES.containsKey((getString(R.string.mjpeg))) ){
                standardGestureDetector = new StandardGestureDetector(this, mjpegView);
            }
        }

        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        toggleStart.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton toggleButton, boolean isChecked) {
                if (isChecked) {
                    timeMeasurement=System.currentTimeMillis();
                } else {
                    toggleStart.setVisibility(View.INVISIBLE);
                    timeMeasurement=System.currentTimeMillis()-timeMeasurement;
                    float testTime=((float)timeMeasurement)/1000.f;
                    DecimalFormat df = new DecimalFormat("#.####");
                    df.setRoundingMode(RoundingMode.FLOOR);
                    builder.setMessage("CONGRATS").setTitle("You have completed the task in " + df.format(testTime) + "s!");
                    AlertDialog dialog = builder.create();
                    if(isRecording)
                        dialog.show();
                    else
                        userNumber="-1";
                    user_measuresTopic.setPublisher_linear(new float[]{Integer.parseInt(userNumber), MAN_INTERFACE, 0});
                    user_measuresTopic.setPublisher_angular(new float[]{testTime, 0, 0});//precision?
                    user_measuresTopic.publishNow();
                }
            }
        });

        user_measuresTopic = new TwistTopic();
        user_measuresTopic.publishTo(getString(R.string.topic_user_measures), false, 10);
        user_measuresTopic.setPublishingFreq(10);

        endeffector_navTopic =  new TwistTopic();
        endeffector_navTopic.publishTo(getString(R.string.topic_r_arm_nav), false, 10);
        endeffector_navTopic.setPublishingFreq(10);

        endeffector_graspTopic = new Float32Topic();
        endeffector_graspTopic.publishTo(getString(R.string.topic_r_arm_grasp), false, 10);
        endeffector_graspTopic.setPublishingFreq(10);
        endeffector_graspTopic.setPublisher_float(0.0f);

        endeffector_presetTopic = new Int32Topic();
        endeffector_presetTopic.publishTo(getString(R.string.topic_gripper_pose_preset), false, 10);
        endeffector_presetTopic.setPublishingFreq(10);
        endeffector_presetTopic.setPublisher_int(0);
        endeffector_presetTopic.publishNow();

        camera_selectionTopic = new Int32Topic();
        camera_selectionTopic.publishTo(getString(R.string.topic_camera_number), true, 10);
        camera_selectionTopic.setPublishingFreq(100);
        camera_selectionTopic.setPublisher_int(1);
        camera_selectionTopic.publishNow();

        interface_numberTopic = new Int32Topic();
        interface_numberTopic.publishTo(getString(R.string.topic_interfacenumber), true, 10);
        interface_numberTopic.setPublishingFreq(100);
        interface_numberTopic.setPublisher_int(MAN_INTERFACE);
        interface_numberTopic.publishNow();

        target_distanceTopic = new Float32Topic();
        target_distanceTopic.subscribeTo(getString(R.string.topic_target_distance));
        target_distanceTopic.setSubcriber_float(Float.MAX_VALUE);

        androidNode.addTopics(camera_selectionTopic, interface_numberTopic, endeffector_presetTopic);

        if ( MainActivity.PREFERENCES.containsKey((getString(R.string.tcp))) )
            androidNode.addTopics(endeffector_navTopic, endeffector_graspTopic, target_distanceTopic, user_measuresTopic);
        else if ( MainActivity.PREFERENCES.containsKey((getString(R.string.udp))) )
            udpCommCommand = new UDPComm( MainActivity.PREFERENCES.getProperty( getString(R.string.MASTER) ) , Integer.parseInt(getString(R.string.udp_port)));

        if ( MainActivity.PREFERENCES.containsKey((getString(R.string.ros_cimage))) ) {
            mjpegView.setVisibility(View.GONE);
            androidNode.addNodeMain(nodeMainImageStream);
        }else if( MainActivity.PREFERENCES.containsKey((getString(R.string.mjpeg))) ){
            nodeMainImageStream.setVisibility(View.GONE);
        }

        toggleCamera1.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton toggleButton, boolean isChecked) {
                if (isChecked) {
                    toggleCamera2.setChecked(false);
                    camera_selectionTopic.setPublisher_int(1);
                    camera_selectionTopic.publishNow();
                } else if (!toggleCamera2.isChecked()) {
                    toggleCamera1.setChecked(true);
                }
            }
        });

        toggleCamera2.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton toggleButton, boolean isChecked) {
                if (isChecked) {
                    toggleCamera1.setChecked(false);
                    camera_selectionTopic.setPublisher_int(2);
                    camera_selectionTopic.publishNow();
                } else if (!toggleCamera1.isChecked()) {
                    toggleCamera2.setChecked(true);
                }
            }
        });

        buttonEndEffectorPose1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                endeffector_presetTopic.setPublisher_int(3);
                endeffector_presetTopic.publishNow();
            }
        });
        buttonEndEffectorPose2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                endeffector_presetTopic.setPublisher_int(1);
                endeffector_presetTopic.publishNow();
            }
        });

        if(isRecording){
            try{
                Integer.parseInt(userNumber);
            }catch (Exception e){
                builder.setMessage("Go back and write User's #").setTitle("User's Number not well defined");
                runOnUiThread(new Runnable() {
                    public void run() {
                        AlertDialog dialog = builder.create();
                        dialog.show();
                    }
                });
                Thread exitActivity = new Thread(){
                    public void run(){
                        try {
                            Thread.sleep(3000);
                            finish();
                        } catch (InterruptedException e) {
                            e.getStackTrace();
                        }
                    }
                };
                exitActivity.start();
            }
        }

        Thread threadGestures = new Thread(){
            public void run(){
                if ( MainActivity.PREFERENCES.containsKey((getString(R.string.mjpeg))) )
                    mjpegView.setSource(MjpegInputStream.read(MainActivity.PREFERENCES.getProperty(getString(R.string.STREAM_URL), "")));
                while(isRunning){
                    try {
                        Thread.sleep(datarate);
                        sendData2Core();
                    } catch (InterruptedException e) {
                        e.getStackTrace();
                    }
                }
            }
        };
        threadGestures.start();

        Thread thread = new Thread(){
            public void run(){

                while(isRunning){
                    try {
                        Thread.sleep(300);
                        Log.i("IMUrx", "rx:" + (deviceAccX));
                        Log.i("IMUry", "ry:" + (deviceAccY));
                        Log.i("IMUrz", "rz:" + (deviceAccZ));
                    } catch (InterruptedException e) {
                        e.getStackTrace();
                    }
                }
            }
        };
        //thread.start();

    }

    @Override
    public void onResume() {
        super.onResume();
        isRunning =true;
        if (MAN_INTERFACE == INTERFACE_03){
            sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_FASTEST);
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }

    }

    @Override
    protected void onPause() {
        mjpegView.stopPlayback();
        if (MAN_INTERFACE == INTERFACE_03)
            sensorManager.unregisterListener(this);
        super.onPause();
    }

    @Override
    public void onDestroy() {
        nodeMain.forceShutdown();
        if (udpCommCommand!= null)
            udpCommCommand.destroy();
        isRunning =false;
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    private void sendData2Core() {

        if(!toggleStart.isChecked())
            return;

        float endeffector_axisX = 0.f;
        float endeffector_axisY = 0.f;
        float endeffector_axisZ = 0.f;
        float endeffector_axisRX = 0.f;
        float endeffector_axisRY = 0.f;
        float endeffector_axisRZ = 0.f;
        float endeffector_grasp = 0.f;

        if ( MainActivity.PREFERENCES.containsKey((getString(R.string.udp))) ){
            //Do something with UDPcommand...
            return;
        }

        final float dT = datarate*MS2S;

        runOnUiThread(new Runnable() {
            public void run() {
                DecimalFormat df = new DecimalFormat("#.####");
                df.setRoundingMode(RoundingMode.FLOOR);
                messageView.setText("Goal at: " + df.format(target_distanceTopic.getSubcriber_float())+ "m");
                }
        });

        if(MAN_INTERFACE == INTERFACE_01){
            runOnUiThread(new Runnable() {
                public void run() {
                    scroller.updateView();
                }
            });
            endeffector_axisX = nodeMainVirtualJoystick01.getAxisX()/4.f;
            endeffector_axisY = nodeMainVirtualJoystick01.getAxisY()/4.f;
            endeffector_axisZ = nodeMainVirtualJoystick02.getAxisX()/4.f;
            endeffector_axisRY= nodeMainVirtualJoystick02.getAxisY();
            endeffector_grasp = scroller.getValue();
        }
        if(MAN_INTERFACE == INTERFACE_02){
            endeffector_axisX = -standardGestureDetector.getTwoFingerDragY()/2.f;
            endeffector_axisY = -standardGestureDetector.getTwoFingerDragX()/2.f;
            endeffector_axisZ = -standardGestureDetector.getThreeFingerDragY()/2.f;
            endeffector_axisRY = -standardGestureDetector.getTwoFingerRotation()/180.f;
            endeffector_grasp = (1.f-standardGestureDetector.getTwoFingerPinch())/10.f;

        }
        if(MAN_INTERFACE == INTERFACE_03){
            runOnUiThread(new Runnable() {
                public void run() {
                    scroller.updateView();
                }
            });
            if(standardGestureDetector.isDetectingOneFingerGesture()){
                endeffector_axisX = ( deviceRotY - ref_deviceRotY )/2.f;//ok
                endeffector_axisY = ( ref_deviceRotX - deviceRotX )/2.f; //ok
                endeffector_axisRY = ( ref_deviceRotZ - deviceRotZ )*2.f;//ok
            }
            //endeffector_axisRY= nodeMainVirtualJoystick02.getAxisY();
            endeffector_axisZ = nodeMainVirtualJoystick02.getAxisX()/4.f;
            endeffector_grasp = scroller.getValue();
        }

        if(Math.abs(endeffector_axisX) < 0.01f)
            endeffector_axisX=0.f;
        if(Math.abs(endeffector_axisY) < 0.01f)
            endeffector_axisY=0.f;
        if(Math.abs(endeffector_axisZ) < 0.01f)
            endeffector_axisZ=0.f;

        endeffector_navTopic.setPublisher_linear(new float[]{endeffector_axisX, endeffector_axisY, endeffector_axisZ});
        endeffector_navTopic.setPublisher_angular(new float[]{endeffector_axisRX, endeffector_axisRY, endeffector_axisRZ});
        endeffector_navTopic.publishNow() ;
        endeffector_graspTopic.setPublisher_float(endeffector_grasp);
        endeffector_graspTopic.publishNow();

    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        if (MAN_INTERFACE !=INTERFACE_03)
            return;

        if(!toggleStart.isChecked())
            return;

        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {

            if (nanotimestamp_gyro != 0) {
                final float dT = (event.timestamp - nanotimestamp_gyro) * NS2S;
                float axisX = event.values[0] * dT;
                float axisY = event.values[1] * dT;
                float axisZ = event.values[2] * dT;

                if(Math.abs(axisX) < 0.001)
                    axisX=0.0f;
                if(Math.abs(axisY) < 0.001)
                    axisY=0.0f;
                if(Math.abs(axisZ) < 0.001)
                    axisZ=0.0f;

                deviceRotX += axisX;
                deviceRotY += axisY;
                deviceRotZ += axisZ;
            }
            nanotimestamp_gyro = event.timestamp;
        }

        if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {

            if (nanotimestamp_acce != 0) {
                final float dT = (event.timestamp - nanotimestamp_acce) * NS2S;
                float axisX = event.values[0];
                float axisY = event.values[1];
                float axisZ = event.values[2];

                deviceAccX += 10000*axisX*dT*dT;
                deviceAccY += 10000*axisY*dT*dT;
                deviceAccZ += 10000*axisZ*dT*dT;

                //deviceAccX += 1000000*axisX*dT*dT;
                //deviceAccY += 1000000*axisY*dT*dT;
                //deviceAccZ += 1000000*axisZ*dT*dT;
            }
            nanotimestamp_acce = event.timestamp;
        }

        if(standardGestureDetector.isDetectingOneFingerGesture()){
            if(firstPress){
                ref_deviceRotX = deviceRotX; ref_deviceRotY = deviceRotY; ref_deviceRotZ = deviceRotZ;
                ref_deviceAccX = deviceAccX; ref_deviceAccY = deviceAccY; ref_deviceAccZ = deviceAccZ;
                firstPress=false;
            }
        }else{
            firstPress=true;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }


    @Override
    protected void init(NodeMainExecutor nodeMainExecutor) {
        nodeMain=(NodeMainExecutorService)nodeMainExecutor;
        NodeConfiguration nodeConfiguration = NodeConfiguration.newPublic( MainActivity.PREFERENCES.getProperty( getString(R.string.HOSTNAME) ), getMasterUri());
        nodeMainExecutor.execute(androidNode, nodeConfiguration.setNodeName(androidNode.getName()));
    }

}