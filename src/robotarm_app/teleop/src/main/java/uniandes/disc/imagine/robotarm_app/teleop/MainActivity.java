package uniandes.disc.imagine.robotarm_app.teleop;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.RadioGroup;
import android.widget.Toast;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.ros.address.InetAddressFactory;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.NetworkInterface;
import java.net.ProtocolException;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Properties;
import java.util.TreeMap;

import javax.net.ssl.HttpsURLConnection;

import uniandes.disc.imagine.robotarm_app.teleop.interfaces.ManipulationInterfaces;
import uniandes.disc.imagine.robotarm_app.teleop.interfaces.NavigationInterfaces;

public class MainActivity extends ActionBarActivity implements PopupMenu.OnMenuItemClickListener {

    private static final String TAG = "MainActivity";

    public static Properties PREFERENCES;

    private EditText rosIP;
    private EditText rosPort;
    private EditText hostIP;
    private EditText userN;
    private TreeMap<String,String> hostNameIPs;
    private ImageView navigationInterfaces;
    private ImageView manipulationInterfaces;
    private MenuItem[] language;
    private PopupMenu deviceIps;
    private RadioGroup recordPref;
    private RadioGroup streamPref;
    private RadioGroup controlPref;
    private RadioGroup navigationPref;
    private RadioGroup manipulationPref;
    private String publicIP;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        publicIP = null;
        updatePublicIP(true);
        findAllDeviceIPs();
        loadGUI();
    }

    private void loadGUI(){
        setContentView(R.layout.activity_main);

        rosIP = (EditText) findViewById(R.id.editIP);
        rosPort = (EditText) findViewById(R.id.editPort);
        hostIP = (EditText) findViewById(R.id.hostNameIP);
        userN = (EditText) findViewById(R.id.editUserNumber);

        navigationInterfaces = (ImageView) findViewById(R.id.imageViewNavigation);
        manipulationInterfaces = (ImageView) findViewById(R.id.imageViewManipulation);
        recordPref = (RadioGroup) findViewById(R.id.recordData);
        streamPref = (RadioGroup) findViewById(R.id.streamPref);
        controlPref = (RadioGroup) findViewById(R.id.controlPref);
        navigationPref = (RadioGroup) findViewById(R.id.navigationPref);
        manipulationPref = (RadioGroup) findViewById(R.id.manipulationPref);

        PREFERENCES = new Properties();

        if(hostNameIPs.containsKey( getString(R.string.default_comm) ))
            hostIP.setText(hostNameIPs.get( getString(R.string.default_comm) ));
        else if (publicIP!=null)
            hostIP.setText(publicIP);
        else
            hostIP.setText(InetAddressFactory.newNonLoopback().getHostAddress());
        deviceIps = new PopupMenu( this, hostIP);
        deviceIps.setOnMenuItemClickListener(this);
        int id = 0;
        for (String interfaces : hostNameIPs.keySet()){
            id++;
            deviceIps.getMenu().add(2, id, id, interfaces +": " + hostNameIPs.get(interfaces));
        }

        Button pingButton = (Button) findViewById(R.id.pingButton);
        pingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startSendingPing();
            }
        });

        Button testButton = (Button) findViewById(R.id.testButton);
        testButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                testMaster(rosIP.getText().toString(), Integer.parseInt(rosPort.getText().toString()));
            }
        });

        Button changeButton = (Button) findViewById(R.id.changeIP);
        changeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deviceIps.getMenu().clear();
                updatePublicIP(false);
                findAllDeviceIPs();
                int id=0;
                for (String interfaces : hostNameIPs.keySet()){
                    id++;
                    deviceIps.getMenu().add(2, id, id, interfaces +": " + hostNameIPs.get(interfaces));
                }
                deviceIps.show();
            }
        });

        navigationPref.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int id) {
                if (id == R.id.navigation01) {
                    navigationInterfaces.setImageResource(R.drawable.interface_navigation_01);
                }
                if (id == R.id.navigation02) {
                    navigationInterfaces.setImageResource(R.drawable.interface_navigation_02);
                }
                if (id == R.id.navigation03) {
                    navigationInterfaces.setImageResource(R.drawable.interface_navigation_03);
                }
            }
        });

        manipulationPref.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int id) {
                if(id == R.id.manipulation01){
                    manipulationInterfaces.setImageResource(R.drawable.interface_manipulation_01);
                }
                if(id == R.id.manipulation02){
                    manipulationInterfaces.setImageResource(R.drawable.interface_manipulation_02);
                }
                if(id == R.id.manipulation03){
                    manipulationInterfaces.setImageResource(R.drawable.interface_manipulation_03);
                }
            }
        });

        recordPref.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int id) {
                userN.setEnabled(id == R.id.recordYes);
            }
        });



        navigationInterfaces.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startNavigationActivity();

            }
        });

        manipulationInterfaces.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startManipulationActivity();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        language = new MenuItem[3]; //for now...
        language[0]= menu.add(1, 1, Menu.NONE, getString(R.string.english));
        language[1]= menu.add(1, 2, Menu.NONE, getString(R.string.spanish));

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int gid = item.getGroupId();
        int iid = item.getItemId();

        if(gid==1 && iid==1){
            //english
            String languageToLoad  = "en";
            Locale locale = new Locale(languageToLoad);
            Locale.setDefault(locale);
            Configuration config = new Configuration();
            config.locale = locale;
            getBaseContext().getResources().updateConfiguration(config, getBaseContext().getResources().getDisplayMetrics());
            loadGUI();
            return true;
        }else if(gid==1 && iid==2){
            //spanish
            String languageToLoad  = "es";
            Locale locale = new Locale(languageToLoad);
            Locale.setDefault(locale);
            Configuration config = new Configuration();
            config.locale = locale;
            getBaseContext().getResources().updateConfiguration(config, getBaseContext().getResources().getDisplayMetrics());
            loadGUI();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onMenuItemClick(MenuItem menuItem) {
        switch (menuItem.getGroupId()) {
            case 2:
                hostIP.setText(hostNameIPs.get(menuItem.getTitle().toString().split(":")[0]));
                return true;
            default:
                return false;
        }
    }

    private void startSendingPing(){
        int exit = pingHost(rosIP.getText().toString(), 5, true);
        if (exit!=0){
            Toast.makeText(getApplicationContext(), rosIP.getText().toString() + " is not reachable!!!", Toast.LENGTH_LONG).show();
        }
    }

    private void startNavigationActivity(){
        if (isMasterValid()){
            Intent myIntent = new Intent(MainActivity.this, NavigationInterfaces.class);
            MainActivity.this.startActivity(myIntent);
        }
    }

    private void startManipulationActivity(){
        if (isMasterValid()){
            Intent myIntent = new Intent(MainActivity.this, ManipulationInterfaces.class);
            MainActivity.this.startActivity(myIntent);
        }
    }

    private void findAllDeviceIPs(){
        hostNameIPs = new TreeMap<String,String>();
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()){
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();

                while (inetAddresses.hasMoreElements()){
                    InetAddress inetAddress = inetAddresses.nextElement();
                    if ( !inetAddress.getHostAddress().contains("%") || !inetAddress.getHostAddress().contains(":")) {
                        hostNameIPs.put(networkInterface.getName(), inetAddress.getHostAddress());
                    }
                }
            }
            if(publicIP!=null)
                hostNameIPs.put(getString(R.string.public_ip_name), publicIP);
        } catch (SocketException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void updatePublicIP(final boolean overrideHostIP){

        Thread thread = new Thread(){
            public void run(){
                try{
                    URL url = new URL("https://ifcfg.me/ip");
                    HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
                    conn.setConnectTimeout(5000);
                    conn.setRequestMethod("POST");
                    conn.getResponseCode();
                    InputStream in = new BufferedInputStream(conn.getInputStream());
                    publicIP = org.apache.commons.io.IOUtils.toString(in, "UTF-8").trim();
                    if(overrideHostIP && hostIP != null){
                        runOnUiThread(new Runnable() {
                            public void run() {
                                hostIP.setText(publicIP);
                            }
                        });
                    }
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                } catch (ProtocolException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        thread.start();
    }

    private boolean isMasterValid(){

        int exit = pingHost(rosIP.getText().toString(), 1, false);
        if (exit!=0){
            Toast.makeText(getApplicationContext(), rosIP.getText().toString() + " is not reachable!!!", Toast.LENGTH_LONG).show();
            return false;
        }

        PREFERENCES.clear();
        PREFERENCES.setProperty( getString(R.string.HOSTNAME), hostIP.getText().toString());
        PREFERENCES.setProperty( getString(R.string.MASTER), rosIP.getText().toString());
        PREFERENCES.setProperty( getString(R.string.MASTER_URI), "http://" + rosIP.getText().toString() + ":" + rosPort.getText().toString() );
        PREFERENCES.setProperty(getString(R.string.STREAM_URL), "http://" + rosIP.getText().toString() + ":" + getString(R.string.mjpeg_port) + "/stream?type=ros_compressed&topic=/android/image_raw");

        if (recordPref.getCheckedRadioButtonId() == R.id.recordYes ) {
            PREFERENCES.setProperty(getString(R.string.record), "");
            PREFERENCES.setProperty(getString(R.string.user), userN.getText().toString() );
        }

        if( streamPref.getCheckedRadioButtonId() == R.id.streamMjpeg )
            PREFERENCES.setProperty( getString(R.string.mjpeg), "" );
        if( streamPref.getCheckedRadioButtonId() == R.id.streamROSCImage )
            PREFERENCES.setProperty( getString(R.string.ros_cimage), "" );
        if( controlPref.getCheckedRadioButtonId() == R.id.controlUDP )
            PREFERENCES.setProperty( getString(R.string.udp), "" );
        if( controlPref.getCheckedRadioButtonId() == R.id.controlTCP )
            PREFERENCES.setProperty( getString(R.string.tcp), "" );

        if( navigationPref.getCheckedRadioButtonId() == R.id.navigation01 )
            PREFERENCES.setProperty( getString(R.string.navigation01), "" );
        if( navigationPref.getCheckedRadioButtonId() == R.id.navigation02 )
            PREFERENCES.setProperty( getString(R.string.navigation02), "" );
        if( navigationPref.getCheckedRadioButtonId() == R.id.navigation03 )
            PREFERENCES.setProperty( getString(R.string.navigation03), "" );
        if( manipulationPref.getCheckedRadioButtonId() == R.id.manipulation01 )
            PREFERENCES.setProperty( getString(R.string.manipulation01), "" );
        if( manipulationPref.getCheckedRadioButtonId() == R.id.manipulation02 )
            PREFERENCES.setProperty( getString(R.string.manipulation02), "" );
        if( manipulationPref.getCheckedRadioButtonId() == R.id.manipulation03 )
            PREFERENCES.setProperty( getString(R.string.manipulation03), "" );

        return true;
    }

    private void testMaster(final String host, final int port){

        Thread thread = new Thread(){
            public void run(){
                int state=-1;
                try {
                    Socket socket = new Socket();
                    socket.connect(new InetSocketAddress(host, port), 1000);
                    if(socket.isConnected()){
                        state=0;
                    }
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                final int status = state;

                runOnUiThread(new Runnable() {
                    public void run() {
                        if(status==0)
                            Toast.makeText(getApplicationContext(), "Connection successful", Toast.LENGTH_LONG).show();
                        else
                            Toast.makeText(getApplicationContext(), "Can't connect to " + host + ":" + port + ". Is roscore running?", Toast.LENGTH_LONG).show();
                    }
                });
            }
        };
        thread.start();
    }

    private int pingHost(String host, int tries, boolean showStats){
        int exit = -1;
        try {
            Runtime runtime = Runtime.getRuntime();
            Process proc = runtime.exec("ping -c " + tries + " " + host); // telnet ip 80
            proc.waitFor();
            if(showStats){
                BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getInputStream()));
                DecimalFormat numberFormat = new DecimalFormat("#.00");
                float minTime= Float.MAX_VALUE;
                float maxTime= Float.MIN_VALUE;
                float avgTime=0;
                float n=0.f;
                String result_line;
                while ((result_line = stdInput.readLine()) != null) {
                    if(!result_line.contains("time="))
                        continue;
                    String s_time = result_line.split("time=")[1].split(" ")[0];
                    float currentTime = Float.parseFloat(s_time);
                    avgTime += currentTime;
                    minTime = minTime < currentTime ? minTime : currentTime;
                    maxTime = maxTime > currentTime ? maxTime : currentTime;
                    n++;
                }
                avgTime/=n;
                Toast.makeText(getApplicationContext(), "Avg: " + numberFormat.format(avgTime) + "ms Min: " + numberFormat.format(minTime) + "ms Max: " + numberFormat.format(maxTime) + "ms", Toast.LENGTH_LONG).show();
            }
            exit = proc.exitValue();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }

        return exit;
    }

}
