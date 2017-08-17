package uniandes.disc.imagine.robotarm_app.teleop.interfaces;

import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Toast;
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


public class DirectManipulationInterface extends RosActivity implements SensorEventListener {
	
	private static final String TAG = "DirectManipulationInterface";
    private static final String NODE_NAME="/android_"+TAG.toLowerCase();

    private NodeMainExecutorService nodeMain;
    private StandardGestureDetector standarGestureDetector = null;

    private CustomVirtualJoystickView virtualJoystickNodeMain;
    private RosImageView<CompressedImage> imageStreamNodeMain;
    private ScrollerView scroller = null;
    private ToggleButton toggleOmnidirectional;
    private ToggleButton toggleGripperPose1;
    private ToggleButton toggleGripperPose2;
    private ToggleButton toggleGripperPose3;
    private ToggleButton toggleCamera1;
    private ToggleButton toggleCamera2;
    private ToggleButton toggleCamera3;
    private ToggleButton toggleCamera4;

    private AndroidNode androidNode;
    private Float32Topic arm_graspTopic;
    private Int32Topic viewSelectionTopic;
    private Int32Topic poseSelectionTopic;
    private TwistTopic robot_navTopic;
    private TwistTopic arm_navTopic;

    private UDPComm udpCommCommand;
    private MjpegView mjpegView;
    private boolean isRunning = true;
    private boolean isNavigation = true;
    private boolean isManipulation = false;
    private boolean isOmnidirectional = false;


    public DirectManipulationInterface() {
        super(TAG, TAG, URI.create(MainActivity.PREFERENCES.getProperty("ROS_MASTER_URI")));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    	Intent intent = getIntent();
        setContentView(R.layout.interface_directmanipulation);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //textPTZ = (TextView) findViewById(R.id.rotationTextView); UDP

        mjpegView = (MjpegView) findViewById(R.id.mjpegView);
        mjpegView.setDisplayMode(MjpegView.SIZE_BEST_FIT);
        mjpegView.showFps(true);

        toggleOmnidirectional = (ToggleButton)findViewById(R.id.toggleOmmidirectional);
        toggleGripperPose1 = (ToggleButton)findViewById(R.id.toggleGripperPose1);
        toggleGripperPose2 = (ToggleButton)findViewById(R.id.toggleGripperPose2);
        toggleGripperPose3 = (ToggleButton)findViewById(R.id.toggleGripperPose3);
        toggleCamera1 = (ToggleButton)findViewById(R.id.toggleCamera1);
        toggleCamera2 = (ToggleButton)findViewById(R.id.toggleCamera2);
        toggleCamera3 = (ToggleButton)findViewById(R.id.toggleCamera3);
        toggleCamera4 = (ToggleButton)findViewById(R.id.toggleCamera4);

        scroller = (ScrollerView) findViewById(R.id.scrollerView);
        scroller.setTopValue(-0.1f);
        scroller.setBottomValue(0.1f);
        scroller.setFontSize(13);
        scroller.setMaxTotalItems(5);
        scroller.setMaxVisibleItems(5);
        scroller.beginAtMiddle();

        virtualJoystickNodeMain = (CustomVirtualJoystickView) findViewById(R.id.virtual_joystick);
        virtualJoystickNodeMain.setHolonomic(true);

        imageStreamNodeMain = (RosImageView<CompressedImage>) findViewById(R.id.streamingView);
        imageStreamNodeMain.setTopicName(getString(R.string.topic_streaming));
        imageStreamNodeMain.setMessageType(getString(R.string.topic_streaming_msg));
        imageStreamNodeMain.setMessageToBitmapCallable(new BitmapFromCompressedImage());
        imageStreamNodeMain.setScaleType(ImageView.ScaleType.FIT_CENTER);

        robot_navTopic =  new TwistTopic();
        robot_navTopic.publishTo(getString(R.string.topic_robot_nav), false, 10);
        robot_navTopic.setPublishingFreq(10);

        arm_navTopic =  new TwistTopic();
        arm_navTopic.publishTo(getString(R.string.topic_r_arm_nav), false, 10);
        arm_navTopic.setPublishingFreq(10);

        arm_graspTopic = new Float32Topic();
        arm_graspTopic.publishTo(getString(R.string.topic_r_arm_grasp), false, 10);
        arm_graspTopic.setPublishingFreq(10);
        arm_graspTopic.setPublisher_float(0.0f);

        viewSelectionTopic = new Int32Topic();
        viewSelectionTopic.publishTo(getString(R.string.topic_camera_number), false, 100);
        viewSelectionTopic.setPublishingFreq(10);
        viewSelectionTopic.setPublisher_int(0);
        viewSelectionTopic.publishNow();

        poseSelectionTopic = new Int32Topic();
        poseSelectionTopic.publishTo("/android/gripper_pose/selection", false, 100);
        poseSelectionTopic.setPublishingFreq(10);
        poseSelectionTopic.setPublisher_int(0);
        poseSelectionTopic.publishNow();

        androidNode = new AndroidNode(NODE_NAME);
        androidNode.addTopics(viewSelectionTopic, poseSelectionTopic);

        if ( MainActivity.PREFERENCES.containsKey((getString(R.string.tcp))) )
            androidNode.addTopics(robot_navTopic, arm_navTopic, arm_graspTopic);
        if ( MainActivity.PREFERENCES.containsKey((getString(R.string.ros_cimage))) ) {
            androidNode.addNodeMain(imageStreamNodeMain);
            standarGestureDetector = new StandardGestureDetector(this, imageStreamNodeMain);
        }else
            imageStreamNodeMain.setVisibility(View.GONE);
        if ( !MainActivity.PREFERENCES.containsKey((getString(R.string.mjpeg))) )
            mjpegView.setVisibility(View.GONE);
        else {
            standarGestureDetector = new StandardGestureDetector(this, mjpegView);
        }

        if ( MainActivity.PREFERENCES.containsKey((getString(R.string.udp))) )
            udpCommCommand = new UDPComm( MainActivity.PREFERENCES.getProperty( getString(R.string.MASTER) ) , Integer.parseInt(getString(R.string.udp_port)));

        toggleOmnidirectional.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton toggleButton, boolean isChecked) {
                isOmnidirectional = isChecked;
                //Toast.makeText(getApplicationContext(), getString(R.string.camera1_name), Toast.LENGTH_SHORT).show();
            }
        });

        toggleGripperPose1.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton toggleButton, boolean isChecked) {
                if (isChecked) {
                    toggleGripperPose2.setChecked(false);
                    toggleGripperPose3.setChecked(false);
                    poseSelectionTopic.setPublisher_int(1);
                    poseSelectionTopic.publishNow();
                }
            }
        });

        toggleGripperPose2.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton toggleButton, boolean isChecked) {
                if (isChecked) {
                    toggleGripperPose1.setChecked(false);
                    toggleGripperPose3.setChecked(false);
                    poseSelectionTopic.setPublisher_int(2);
                    poseSelectionTopic.publishNow();
                }
            }
        });

        toggleGripperPose3.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton toggleButton, boolean isChecked) {
                if (isChecked) {
                    toggleGripperPose1.setChecked(false);
                    toggleGripperPose2.setChecked(false);
                    poseSelectionTopic.setPublisher_int(3);
                    poseSelectionTopic.publishNow();
                }
            }
        });

        toggleCamera1.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton toggleButton, boolean isChecked) {
                if (isChecked) {
                    isNavigation = true;
                    isManipulation = false;
                    Toast.makeText(getApplicationContext(), getString(R.string.camera1_name), Toast.LENGTH_SHORT).show();
                    toggleCamera1.setChecked(true);
                    toggleCamera2.setChecked(false);
                    toggleCamera3.setChecked(false);
                    toggleCamera4.setChecked(false);
                    viewSelectionTopic.setPublisher_int(0);
                    viewSelectionTopic.publishNow();
                    virtualJoystickNodeMain.setVisibility(View.INVISIBLE);
                    toggleOmnidirectional.setVisibility(View.VISIBLE);
                }
            }
        });

        toggleCamera2.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton toggleButton, boolean isChecked) {
                if (isChecked) {
                    isNavigation = false;
                    isManipulation = true;
                    Toast.makeText(getApplicationContext(), getString(R.string.camera2_name), Toast.LENGTH_SHORT).show();
                    toggleCamera1.setChecked(false);
                    toggleCamera2.setChecked(true);
                    toggleCamera3.setChecked(false);
                    toggleCamera4.setChecked(false);
                    viewSelectionTopic.setPublisher_int(1);
                    viewSelectionTopic.publishNow();
                    virtualJoystickNodeMain.setVisibility(View.VISIBLE);
                    toggleOmnidirectional.setVisibility(View.INVISIBLE);
                }
            }
        });

        toggleCamera3.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton toggleButton, boolean isChecked) {
                if (isChecked) {
                    isNavigation = false;
                    isManipulation = true;
                    Toast.makeText(getApplicationContext(), getString(R.string.camera3_name), Toast.LENGTH_SHORT).show();
                    toggleCamera1.setChecked(false);
                    toggleCamera2.setChecked(false);
                    toggleCamera3.setChecked(true);
                    toggleCamera4.setChecked(false);
                    viewSelectionTopic.setPublisher_int(2);
                    viewSelectionTopic.publishNow();
                    virtualJoystickNodeMain.setVisibility(View.VISIBLE);
                    toggleOmnidirectional.setVisibility(View.INVISIBLE);
                }
            }
        });

        toggleCamera4.setOnCheckedChangeListener( new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton toggleButton, boolean isChecked) {
                if(isChecked){
                    isNavigation = false;
                    isManipulation = true;
                    Toast.makeText(getApplicationContext(), getString(R.string.camera4_name), Toast.LENGTH_SHORT).show();
                    toggleCamera1.setChecked(false);
                    toggleCamera2.setChecked(false);
                    toggleCamera3.setChecked(false);
                    toggleCamera4.setChecked(true);
                    viewSelectionTopic.setPublisher_int(4);
                    viewSelectionTopic.publishNow();
                    virtualJoystickNodeMain.setVisibility(View.VISIBLE);
                }
            }
        });

        Thread threadGestures = new Thread(){
            public void run(){
                if ( MainActivity.PREFERENCES.containsKey((getString(R.string.mjpeg))) )
                    mjpegView.setSource(MjpegInputStream.read(MainActivity.PREFERENCES.getProperty(getString(R.string.STREAM_URL), "")));
                while(isRunning){
                    try {
                        Thread.sleep(20);
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
    }
    
    @Override
    protected void onPause() {
        mjpegView.stopPlayback();
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

        runOnUiThread(new Runnable() {
            public void run() {
                scroller.updateView();
            }
        });

        /*if(!standarGestureDetector.isDetectingGesture()){
            return;
        }*/

        float robot_axisX = 0.f;
        float robot_axisY = 0.f;
        float robot_axisZ = 0.f;
        float robot_axisRZ = 0.f;
        float arm_axisX = 0.f;
        float arm_axisY= 0.f;
        float arm_axisZ = 0.f;
        float arm_axisRX = 0.f;
        float arm_axisRY= 0.f;
        float arm_axisRZ = 0.f;
        float grasp = 1-standarGestureDetector.getGrasp();
        float axisX = standarGestureDetector.getTargetX();
        float axisY = standarGestureDetector.getTargetY();
        float axisZ = standarGestureDetector.getThridDimension();
        float axisRX = virtualJoystickNodeMain.getAxisX();
        float axisRY = virtualJoystickNodeMain.getAxisY();
        float axisRZ = standarGestureDetector.getRotation()/180.f;


        if(Math.abs(axisX) < 0.01f)
            axisX=0.f;
        if(Math.abs(axisY) < 0.01f)
            axisY=0.f;
        if(Math.abs(axisZ) < 0.01f)
            axisZ=0.f;
        if(Math.abs(axisRZ) < 0.01f)
            axisRZ=0.f;

        if (isNavigation){
            robot_axisX=-axisY;
            robot_axisY=-axisX;
            robot_axisZ=-axisZ;
            robot_axisRZ=axisRZ;

            if(false){
                robot_axisY=0.f;
                robot_axisRZ=-axisX;
            }

            robot_navTopic.setPublisher_linear(new float[]{robot_axisX, robot_axisY, robot_axisZ});
            robot_navTopic.setPublisher_angular(new float[]{0, 0, robot_axisRZ});
            robot_navTopic.publishNow();
        }
        if (isManipulation){
            arm_axisX=-axisY/2.f;
            arm_axisY=-axisX/2.f;
            arm_axisZ=-axisZ/2.f;
            // Yaw=Z, Pitch=Y, Roll=X
            arm_axisRX = -axisRX/3.f;
            arm_axisRY = -axisRZ;
            arm_axisRZ = axisRY/3.f;
            //arm_axisZ=scroller.getValue();

            arm_navTopic.setPublisher_linear(new float[]{arm_axisX, arm_axisY, arm_axisZ});
            arm_navTopic.setPublisher_angular(new float[]{arm_axisRX, arm_axisRY, arm_axisRZ});
            arm_graspTopic.setPublisher_float(grasp);
            arm_graspTopic.publishNow();
        }
        arm_navTopic.publishNow();

        /*
        String data="velocity;"+acceleration+";"+steer;
        if(clameraNumberTopic.getPublisher_int()==2 && ptz!=-1){
            data+=";ptz;"+ptz;
        }else{
            ptz=-1;
        }

        if ( MainActivity.PREFERENCES.containsKey((getString(R.string.udp))) )
            udpCommCommand.sendData(data.getBytes());
        */

        if ( MainActivity.PREFERENCES.containsKey((getString(R.string.tcp))) ){
            //send topics
        }

    }

    @Override
    public void onSensorChanged(SensorEvent event) {

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