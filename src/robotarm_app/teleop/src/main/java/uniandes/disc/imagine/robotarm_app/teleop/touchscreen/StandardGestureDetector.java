package uniandes.disc.imagine.robotarm_app.teleop.touchscreen;

import android.app.Activity;
import android.content.Context;
import android.os.Vibrator;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

/**
 * Created by DarkNeoBahamut on 23/07/2015.
 */
public class StandardGestureDetector extends GestureDetector.SimpleOnGestureListener{

    private static final String TAG = "StandardGestureDetector";

    private Activity mActivity;
    private View mView;
    private GestureDetector mGestureDetector;
    private static final int INVALID_POINTER_ID = -1;
    private static final int FAST_GRASPING_TIME_THREASHOLD = 300;//ms
    private static final int FAST_GRASPING_DISTANCE_THREASHOLD = 100;//dpi
    private static final float REF_DISTANCE_DIP = 600;
    private Vibrator vibrator;

    private static final float MAX_GRASP = 1.f;
    private static final float MIN_GRASP = .0f;

    private boolean detectingGesture =false;
    private float ptrID3_initX, ptrID3_initY, ptrID2_initX, ptrID2_initY, ptrID1_initX, ptrID1_initY;
    private float ptrID3_endX, ptrID3_endY, ptrID2_endX, ptrID2_endY, ptrID1_endX, ptrID1_endY;
    private int ptrID1, ptrID2, ptrID3;

    private float initDistance;
    private float initGrasp;
    private float currDistance;
    private float currGrasp;

    private float thridDimension;
    private float rotation;
    private float grasp;
    private float posX;
    private float posY;
    private float targetX;
    private float targetY;

    private long initTime=0;
    private long endTime=0;

    public StandardGestureDetector(Activity activity, View view){
        vibrator = (Vibrator) activity.getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE);
        mGestureDetector = new GestureDetector(activity, this);
        ptrID1 = INVALID_POINTER_ID;
        ptrID2 = INVALID_POINTER_ID;
        ptrID3 = INVALID_POINTER_ID;
        grasp = MAX_GRASP;
        rotation = 0f;
        mActivity = activity;
        mView = view;

        setupListener();
    }

    private void setupListener(){
        mView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                calculateGestures(view, event);
                mGestureDetector.onTouchEvent(event);
                return true;
            }
        });
    }

    private void calculateGestures(View view, MotionEvent event) {

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                ptrID1 = event.getPointerId(event.getActionIndex());
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                detectingGesture = true;
                //if(ptrID1 == INVALID_POINTER_ID || ptrID2 != INVALID_POINTER_ID) //to ignore 3 or more finger inputs

                if(ptrID1 != INVALID_POINTER_ID && ptrID2 == INVALID_POINTER_ID){
                    ptrID2 = event.getPointerId(event.getActionIndex());
                    ptrID1_initX = event.getX(event.findPointerIndex(ptrID1));
                    ptrID1_initY = event.getY(event.findPointerIndex(ptrID1));
                    ptrID2_initX = event.getX(event.findPointerIndex(ptrID2));
                    ptrID2_initY = event.getY(event.findPointerIndex(ptrID2));
                    initTime =  event.getEventTime();
                    initGrasp = getGrasp();
                    initDistance = (float) Math.sqrt(Math.pow(ptrID1_initX - ptrID2_initX, 2) + Math.pow(ptrID1_initY - ptrID2_initY, 2));
                    initDistance = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, initDistance, mActivity.getResources().getDisplayMetrics());
                }else if(ptrID1 != INVALID_POINTER_ID && ptrID2 != INVALID_POINTER_ID && ptrID3 == INVALID_POINTER_ID){
                    ptrID3 = event.getPointerId(event.getActionIndex());
                    ptrID3_initX = event.getX(event.findPointerIndex(ptrID3));
                    ptrID3_initY = event.getY(event.findPointerIndex(ptrID3));
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (ptrID1 != INVALID_POINTER_ID && ptrID2 != INVALID_POINTER_ID && ptrID3 == INVALID_POINTER_ID ) {
                    //2 finger gestures
                    detectingGesture = true;
                    ptrID1_endX = event.getX(event.findPointerIndex(ptrID1));
                    ptrID1_endY = event.getY(event.findPointerIndex(ptrID1));
                    ptrID2_endX = event.getX(event.findPointerIndex(ptrID2));
                    ptrID2_endY = event.getY(event.findPointerIndex(ptrID2));

                    //Dragging
                    targetX = (ptrID1_endX - ptrID1_initX + ptrID2_endX - ptrID2_initX)/(2f*REF_DISTANCE_DIP);
                    targetY = (ptrID1_endY - ptrID1_initY + ptrID2_endY - ptrID2_initY)/(2f*REF_DISTANCE_DIP);

                    //Rotating
                    rotation = getAngleBetweenLines(ptrID2_initX, ptrID2_initY, ptrID1_initX, ptrID1_initY, ptrID2_endX, ptrID2_endY, ptrID1_endX, ptrID1_endY);

                    //Grasping
                    currDistance = (float) Math.sqrt(Math.pow(ptrID1_endX - ptrID2_endX, 2) + Math.pow(ptrID1_endY - ptrID2_endY, 2));
                    currDistance = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, currDistance, mActivity.getResources().getDisplayMetrics());
                    float normalizedDistance = (currDistance-initDistance)/(4.f*REF_DISTANCE_DIP);
                    currGrasp = Math.max(MIN_GRASP,Math.min(initGrasp - normalizedDistance, MAX_GRASP));
                    setGrasp(currGrasp);
                }else if(ptrID1 != INVALID_POINTER_ID && ptrID2 != INVALID_POINTER_ID && ptrID3 != INVALID_POINTER_ID){
                    detectingGesture = true;
                    ptrID3_endX = event.getX(event.findPointerIndex(ptrID3));
                    ptrID3_endY = event.getY(event.findPointerIndex(ptrID3));

                    float distance = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, ptrID3_endY - ptrID3_initY, mActivity.getResources().getDisplayMetrics());
                    thridDimension = distance/(4.f*REF_DISTANCE_DIP);
                }

                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                int id = event.getPointerId(event.getActionIndex());
                endTime = event.getEventTime();
                if(id==ptrID1)
                    ptrID1 = INVALID_POINTER_ID;
                if(id==ptrID2)
                    ptrID2 = INVALID_POINTER_ID;
                if(id==ptrID3){
                    ptrID3 = INVALID_POINTER_ID;
                    thridDimension = 0.f;
                }
                if(ptrID1 == INVALID_POINTER_ID || ptrID2 == INVALID_POINTER_ID){
                    detectingGesture = false;
                    rotation = 0.f;
                    targetX = 0.f;
                    targetY = 0.f;
                    if( endTime-initTime < FAST_GRASPING_TIME_THREASHOLD){
                        if(currDistance-initDistance > FAST_GRASPING_DISTANCE_THREASHOLD)
                            currGrasp = MIN_GRASP;
                        else if(initDistance-currDistance > FAST_GRASPING_DISTANCE_THREASHOLD)
                            currGrasp = MAX_GRASP;
                        setGrasp(currGrasp);
                    }
                }

                break;
            case MotionEvent.ACTION_CANCEL:
                ptrID1 = INVALID_POINTER_ID;
                ptrID2 = INVALID_POINTER_ID;
                ptrID3 = INVALID_POINTER_ID;
                detectingGesture = false;
                thridDimension = 0.f;
                rotation = 0.f;
                targetX = 0.f;
                targetY = 0.f;
                break;
        }

    }

    @Override
    public void onLongPress(MotionEvent event) {
        targetX =event.getX();
        targetY =event.getY();
        vibrator.vibrate(200);
    }


    private float getAngleBetweenLines (float fX, float fY, float sX, float sY, float nfX, float nfY, float nsX, float nsY) {
        float angle1 = (float) Math.atan2((fY - sY), (fX - sX));
        float angle2 = (float) Math.atan2((nfY - nsY), (nfX - nsX));

        float angle = ((float) Math.toDegrees(angle1 - angle2)) % 360;
        if (angle < -180.f) angle += 360.0f;
        if (angle > 180.f) angle -= 360.0f;
        return angle;
    }


    public float getTargetX() {
        return targetX;
    }

    public void setTargetX(float targetX) {
        this.targetX = targetX;
    }

    public float getTargetY() {
        return targetY;
    }

    public void setTargetY(float targetY) {
        this.targetY = targetY;
    }

    public float getRotation() {
        return rotation;
    }

    public void setRotation(float rotation) {
        this.rotation = rotation;
    }

    public float getGrasp() {
        return grasp;
    }

    public void setGrasp(float grasp) {
        this.grasp = grasp;
    }

    public float getPosX() {
        return posX;
    }

    public void setPosX(float posX) {
        this.posX = posX;
    }

    public float getPosY() {
        return posY;
    }

    public void setPosY(float posY) {
        this.posY = posY;
    }

    public float getThridDimension() {
        return thridDimension;
    }

    public void setThridDimension(float thridDimension) {
        this.thridDimension = thridDimension;
    }

    public boolean isDetectingGesture() {
        return detectingGesture;
    }

    public void setDetectingGesture(boolean detectingGesture) {
        this.detectingGesture = detectingGesture;
    }

}