package uniandes.disc.imagine.robotarm_app.teleop.interfaces;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
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

import java.net.URI;

import sensor_msgs.CompressedImage;
import uniandes.disc.imagine.robotarm_app.teleop.MainActivity;
import uniandes.disc.imagine.robotarm_app.teleop.R;
import uniandes.disc.imagine.robotarm_app.teleop.topic.Int32Topic;
import uniandes.disc.imagine.robotarm_app.teleop.topic.TwistTopic;
import uniandes.disc.imagine.robotarm_app.teleop.touchscreen.StandardGestureDetector;
import uniandes.disc.imagine.robotarm_app.teleop.utils.AndroidNode;
import uniandes.disc.imagine.robotarm_app.teleop.utils.MjpegInputStream;
import uniandes.disc.imagine.robotarm_app.teleop.utils.MjpegView;
import uniandes.disc.imagine.robotarm_app.teleop.utils.UDPComm;
import uniandes.disc.imagine.robotarm_app.teleop.widget.CustomVirtualJoystickView;
import uniandes.disc.imagine.robotarm_app.teleop.widget.ScrollerView;


public class NavigationInterfaces extends RosActivity implements SensorEventListener {

	private static final String TAG = "NavigationInterfaces";
    private static final String NODE_NAME="/android_"+TAG.toLowerCase();

    private NodeMainExecutorService nodeMain;
    private StandardGestureDetector standarGestureDetector = null;

    private RosImageView<CompressedImage> nodeMainImageStream;
    private CustomVirtualJoystickView nodeMainVirtualJoystick01=null;
    private CustomVirtualJoystickView nodeMainVirtualJoystick02=null;
    private ScrollerView scroller = null;

    private ToggleButton toggleStart;
    private Button resetCamera;

    private TextView virtualJoystickTitle01;
    private TextView virtualJoystickTitle02;
    private TextView scrollerTitle;

    private AndroidNode androidNode;
    private TwistTopic robot_navTopic;
    private TwistTopic head_targetTopic;
    private Int32Topic camera_selectionTopic;
    private Int32Topic interface_numberTopic;

    private UDPComm udpCommCommand;
    private MjpegView mjpegView;
    private boolean isRunning = true;

    private static final float NS2S = 1.0f / 1000000000.0f;
    private static final float MS2S = 1.0f / 1000.0f;
    private static final float MAXRADSPS = 20.f / 57.3f ; //10 degrees per second
    private float headRotZ, headRotY;
    private float nanotimestamp;
    private int datarate = 20;

    private SensorManager sensorManager;
    private Sensor gyroscope;

    private int NAV_INTERFACE;
    private int INTERFACE_01;
    private int INTERFACE_02;
    private int INTERFACE_03;

    public NavigationInterfaces() {
        super(TAG, TAG, URI.create(MainActivity.PREFERENCES.getProperty("ROS_MASTER_URI")));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        androidNode = new AndroidNode(NODE_NAME);
        INTERFACE_01 = Integer.parseInt(getString(R.string.interface_navigation_01_number));
        INTERFACE_02 = Integer.parseInt(getString(R.string.interface_navigation_02_number));
        INTERFACE_03 = Integer.parseInt(getString(R.string.interface_navigation_03_number));

        headRotZ = 0.f;
        headRotY = 0.f;

        if (MainActivity.PREFERENCES.containsKey((getString(R.string.navigation01))))
            NAV_INTERFACE=INTERFACE_01;
        if (MainActivity.PREFERENCES.containsKey((getString(R.string.navigation02))))
            NAV_INTERFACE=INTERFACE_02;
        if (MainActivity.PREFERENCES.containsKey((getString(R.string.navigation03))))
            NAV_INTERFACE=INTERFACE_03;

        //Intent intent = getIntent();
        setContentView(R.layout.navigation_interfaces);
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

        toggleStart = (ToggleButton) findViewById(R.id.toggleStart);
        resetCamera = (Button) findViewById(R.id.resetCameraButton);

        virtualJoystickTitle01 = (TextView) findViewById(R.id.scrollerTextView01);
        virtualJoystickTitle02 = (TextView) findViewById(R.id.scrollerTextView02);
        scrollerTitle = (TextView) findViewById(R.id.scrollTextView);


        if (NAV_INTERFACE==INTERFACE_01){

            scroller.setVisibility(View.GONE);
            scrollerTitle.setVisibility(View.GONE);

            nodeMainVirtualJoystick01.setHolonomic(true);
            nodeMainVirtualJoystick02.setHolonomic(true);
            nodeMainVirtualJoystick01.setVisibility(View.VISIBLE);
            nodeMainVirtualJoystick02.setVisibility(View.VISIBLE);
            virtualJoystickTitle01.setVisibility(View.VISIBLE);
            virtualJoystickTitle02.setVisibility(View.VISIBLE);
        }
        if (NAV_INTERFACE==INTERFACE_02){

            nodeMainVirtualJoystick01.setVisibility(View.GONE);
            nodeMainVirtualJoystick02.setVisibility(View.GONE);
            virtualJoystickTitle01.setVisibility(View.GONE);
            virtualJoystickTitle02.setVisibility(View.GONE);
            scroller.setVisibility(View.GONE);
            scrollerTitle.setVisibility(View.GONE);

            if ( MainActivity.PREFERENCES.containsKey((getString(R.string.ros_cimage))) ) {
                standarGestureDetector = new StandardGestureDetector(this, nodeMainImageStream);
            }else if( MainActivity.PREFERENCES.containsKey((getString(R.string.mjpeg))) ){
                standarGestureDetector = new StandardGestureDetector(this, mjpegView);
            }

        }
        if (NAV_INTERFACE==INTERFACE_03){

            nodeMainVirtualJoystick01.setVisibility(View.GONE);
            nodeMainVirtualJoystick02.setVisibility(View.GONE);
            virtualJoystickTitle01.setVisibility(View.GONE);
            virtualJoystickTitle02.setVisibility(View.GONE);

            sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_FASTEST);
            scroller.setTopValue(-1.f);
            scroller.setBottomValue(1.f);
            scroller.setFontSize(13);
            scroller.setMaxTotalItems(3);
            scroller.setMaxVisibleItems(3);
            scroller.beginAtMiddle();
            scroller.resetOnRelease();
            scroller.setVisibility(View.VISIBLE);
            scrollerTitle.setVisibility(View.VISIBLE);
        }

        resetCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                headRotZ = 0.f;
                headRotY = 0.f;
            }
        });

        robot_navTopic =  new TwistTopic();
        robot_navTopic.publishTo(getString(R.string.topic_robot_nav), false, 10);
        robot_navTopic.setPublishingFreq(10);

        head_targetTopic =  new TwistTopic();
        head_targetTopic.publishTo(getString(R.string.topic_head_target), false, 10);
        head_targetTopic.setPublishingFreq(10);

        camera_selectionTopic = new Int32Topic();
        camera_selectionTopic.publishTo(getString(R.string.topic_camera_number), true, 10);
        camera_selectionTopic.setPublishingFreq(100);
        camera_selectionTopic.setPublisher_int(1);
        camera_selectionTopic.publishNow();

        interface_numberTopic = new Int32Topic();
        interface_numberTopic.publishTo(getString(R.string.topic_interfacenumber), true, 10);
        interface_numberTopic.setPublishingFreq(100);
        interface_numberTopic.setPublisher_int(NAV_INTERFACE);
        interface_numberTopic.publishNow();

        androidNode.addTopics(camera_selectionTopic, interface_numberTopic);

        if ( MainActivity.PREFERENCES.containsKey((getString(R.string.tcp))) )
            androidNode.addTopics(robot_navTopic, head_targetTopic);
        else if ( MainActivity.PREFERENCES.containsKey((getString(R.string.udp))) )
            udpCommCommand = new UDPComm( MainActivity.PREFERENCES.getProperty( getString(R.string.MASTER) ) , Integer.parseInt(getString(R.string.udp_port)));

        if ( MainActivity.PREFERENCES.containsKey((getString(R.string.ros_cimage))) ) {
            mjpegView.setVisibility(View.GONE);
            androidNode.addNodeMain(nodeMainImageStream);
        }else if( MainActivity.PREFERENCES.containsKey((getString(R.string.mjpeg))) ){
            nodeMainImageStream.setVisibility(View.GONE);
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
    }

    @Override
    public void onResume() {
        super.onResume();
        isRunning =true;
        if (NAV_INTERFACE==INTERFACE_03)
            sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_FASTEST);
    }
    
    @Override
    protected void onPause() {
        mjpegView.stopPlayback();
        if (NAV_INTERFACE==INTERFACE_03)
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

        float robot_axisX = 0.f;
        float robot_axisY = 0.f;
        float robot_axisZ = 0.f;
        float robot_axisRX = 0.f;
        float robot_axisRY = 0.f;
        float robot_axisRZ = 0.f;

        float head_axisX = 0.f;
        float head_axisY = 0.f;
        float head_axisZ = 0.f;
        float head_axisRX = 0.f;
        float head_axisRY = 0.f;
        float head_axisRZ = 0.f;

        if ( MainActivity.PREFERENCES.containsKey((getString(R.string.udp))) ){
            //Do something with UDPcommand...
            return;
        }

        final float dT = datarate*MS2S;

        if(NAV_INTERFACE == INTERFACE_01){

            robot_axisX =  nodeMainVirtualJoystick01.getAxisX();
            robot_axisRZ = nodeMainVirtualJoystick01.getAxisY();

            headRotY -= (nodeMainVirtualJoystick02.getAxisX()*dT*MAXRADSPS);
            headRotZ += (nodeMainVirtualJoystick02.getAxisY()*dT*MAXRADSPS);
            head_axisRY= headRotY;
            head_axisRZ= headRotZ;

        }
        if(NAV_INTERFACE == INTERFACE_02){
            if(!standarGestureDetector.isDetectingGesture())
                return;
            float axisX = standarGestureDetector.getTargetX();
            float axisY = standarGestureDetector.getTargetY();
            float axisZ = standarGestureDetector.getThridDimension();
            float axisRZ = standarGestureDetector.getRotation()/180.f;
        }
        if(NAV_INTERFACE == INTERFACE_03){
            runOnUiThread(new Runnable() {
                public void run() {
                    scroller.updateView();
                }
            });
            robot_axisX=scroller.getValue();
            head_axisRY= headRotY;
            head_axisRZ= headRotZ;
        }

        if(Math.abs(robot_axisX) < 0.01f)
            robot_axisX=0.f;
        if(Math.abs(robot_axisY) < 0.01f)
            robot_axisY=0.f;
        if(Math.abs(robot_axisZ) < 0.01f)
            robot_axisZ=0.f;


        robot_navTopic.setPublisher_linear(new float[]{robot_axisX, robot_axisY, robot_axisZ});
        robot_navTopic.setPublisher_angular(new float[]{robot_axisRX, robot_axisRY, robot_axisRZ});
        robot_navTopic.publishNow();
        head_targetTopic.setPublisher_linear(new float[]{head_axisX, head_axisY, head_axisZ});
        head_targetTopic.setPublisher_angular(new float[]{head_axisRX, head_axisRY, head_axisRZ});
        head_targetTopic.publishNow();

    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        if (NAV_INTERFACE!=INTERFACE_03)
            return;

        if(!toggleStart.isChecked())
            return;

        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {

            if (nanotimestamp != 0) {
                final float dT = (event.timestamp - nanotimestamp) * NS2S;
                float axisZ = event.values[0] * dT;
                float axisY = event.values[1] * dT;

                if(Math.abs(axisY) < 0.001)
                    axisY=0.0f;
                if(Math.abs(axisZ) < 0.001)
                    axisZ=0.0f;

                headRotY += axisY;
                headRotZ += axisZ;
            }
            nanotimestamp = event.timestamp;
        }

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
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