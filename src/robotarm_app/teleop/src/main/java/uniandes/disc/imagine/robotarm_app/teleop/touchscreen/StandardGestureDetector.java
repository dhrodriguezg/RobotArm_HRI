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

    private static final String TAG = "NavigationGesturesDetector";

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

    private boolean detectingOneFingerGesture = false;
    private boolean detectingTwoFingersGesture = false;
    private boolean detectingThreeFingersGesture = false;
    private float ptrID3_initX, ptrID3_initY, ptrID2_initX, ptrID2_initY, ptrID1_initX, ptrID1_initY;
    private float ptrID3_endX, ptrID3_endY, ptrID2_endX, ptrID2_endY, ptrID1_endX, ptrID1_endY;
    private int ptrID1, ptrID2, ptrID3;

    private float initDistance;
    private float initPinch;
    private float currDistance;
    private float currentPinch;

    private float oneFingerDragX;
    private float oneFingerDragY;
    private float twoFingerDragX;
    private float twoFingerDragY;
    private float twoFingerPinch;
    private float twoFingerRotation;
    private float threeFingerDragY;
    private float threeFingerDragX;

    private float posX;
    private float posY;


    private long initTime=0;
    private long endTime=0;

    public StandardGestureDetector(Activity activity, View view){
        vibrator = (Vibrator) activity.getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE);
        mGestureDetector = new GestureDetector(activity, this);
        ptrID1 = INVALID_POINTER_ID;
        ptrID2 = INVALID_POINTER_ID;
        ptrID3 = INVALID_POINTER_ID;
        twoFingerPinch = MAX_GRASP;
        twoFingerRotation = 0f;
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
                ptrID1_initX = event.getX(event.findPointerIndex(ptrID1));
                ptrID1_initY = event.getY(event.findPointerIndex(ptrID1));
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                detectingTwoFingersGesture = true;
                if(ptrID1 != INVALID_POINTER_ID && ptrID2 == INVALID_POINTER_ID){
                    ptrID2 = event.getPointerId(event.getActionIndex());
                    ptrID1_initX = event.getX(event.findPointerIndex(ptrID1));
                    ptrID1_initY = event.getY(event.findPointerIndex(ptrID1));
                    ptrID2_initX = event.getX(event.findPointerIndex(ptrID2));
                    ptrID2_initY = event.getY(event.findPointerIndex(ptrID2));
                    initTime =  event.getEventTime();
                    initPinch = getTwoFingerPinch();
                    initDistance = (float) Math.sqrt(Math.pow(ptrID1_initX - ptrID2_initX, 2) + Math.pow(ptrID1_initY - ptrID2_initY, 2));
                    initDistance = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, initDistance, mActivity.getResources().getDisplayMetrics());
                }else if(ptrID1 != INVALID_POINTER_ID && ptrID2 != INVALID_POINTER_ID && ptrID3 == INVALID_POINTER_ID){
                    ptrID3 = event.getPointerId(event.getActionIndex());
                    ptrID3_initX = event.getX(event.findPointerIndex(ptrID3));
                    ptrID3_initY = event.getY(event.findPointerIndex(ptrID3));
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (ptrID1 != INVALID_POINTER_ID && ptrID2 == INVALID_POINTER_ID && ptrID3 == INVALID_POINTER_ID ) {
                    detectingTwoFingersGesture = true;
                    ptrID1_endX = event.getX(event.findPointerIndex(ptrID1));
                    ptrID1_endY = event.getY(event.findPointerIndex(ptrID1));

                    //Dragging
                    oneFingerDragX = (ptrID1_endX - ptrID1_initX)/REF_DISTANCE_DIP;
                    oneFingerDragY = (ptrID1_endY - ptrID1_initY)/REF_DISTANCE_DIP;

                    detectingOneFingerGesture = true;
                }
                if (ptrID1 != INVALID_POINTER_ID && ptrID2 != INVALID_POINTER_ID && ptrID3 == INVALID_POINTER_ID ) {
                    //2 finger gestures
                    ptrID1_endX = event.getX(event.findPointerIndex(ptrID1));
                    ptrID1_endY = event.getY(event.findPointerIndex(ptrID1));
                    ptrID2_endX = event.getX(event.findPointerIndex(ptrID2));
                    ptrID2_endY = event.getY(event.findPointerIndex(ptrID2));

                    //Dragging
                    twoFingerDragX = (ptrID1_endX - ptrID1_initX + ptrID2_endX - ptrID2_initX)/(2f*REF_DISTANCE_DIP);
                    twoFingerDragY = (ptrID1_endY - ptrID1_initY + ptrID2_endY - ptrID2_initY)/(2f*REF_DISTANCE_DIP);

                    //Rotating
                    twoFingerRotation = getAngleBetweenLines(ptrID2_initX, ptrID2_initY, ptrID1_initX, ptrID1_initY, ptrID2_endX, ptrID2_endY, ptrID1_endX, ptrID1_endY);

                    //Grasping
                    currDistance = (float) Math.sqrt(Math.pow(ptrID1_endX - ptrID2_endX, 2) + Math.pow(ptrID1_endY - ptrID2_endY, 2));
                    currDistance = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, currDistance, mActivity.getResources().getDisplayMetrics());
                    float normalizedDistance = (currDistance-initDistance)/(4.f*REF_DISTANCE_DIP);
                    currentPinch = Math.max(MIN_GRASP,Math.min(initPinch - normalizedDistance, MAX_GRASP));
                    setTwoFingerPinch(currentPinch);

                    detectingTwoFingersGesture = true;
                }else if(ptrID1 != INVALID_POINTER_ID && ptrID2 != INVALID_POINTER_ID && ptrID3 != INVALID_POINTER_ID){
                    // 3 fingers
                    detectingTwoFingersGesture = true;
                    ptrID3_endX = event.getX(event.findPointerIndex(ptrID3));
                    ptrID3_endY = event.getY(event.findPointerIndex(ptrID3));

                    //float distanceY = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, ptrID3_endY - ptrID3_initY, mActivity.getResources().getDisplayMetrics());
                    //float distanceX = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, ptrID3_endX - ptrID3_initX, mActivity.getResources().getDisplayMetrics());
                    threeFingerDragY = (ptrID3_endY - ptrID3_initY)/REF_DISTANCE_DIP;
                    threeFingerDragX = (ptrID3_endX - ptrID3_initX)/REF_DISTANCE_DIP;

                    detectingThreeFingersGesture = true;
                }

                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                int id = event.getPointerId(event.getActionIndex());
                endTime = event.getEventTime();
                if(id==ptrID1){
                    detectingOneFingerGesture = false;
                    ptrID1 = INVALID_POINTER_ID;
                    oneFingerDragY = 0.f;
                    oneFingerDragX = 0.f;
                }
                if(id==ptrID2)
                    ptrID2 = INVALID_POINTER_ID;
                if(id==ptrID3){
                    detectingThreeFingersGesture = false;
                    ptrID3 = INVALID_POINTER_ID;
                    threeFingerDragY = 0.f;
                    threeFingerDragX = 0.f;
                }
                if(ptrID1 == INVALID_POINTER_ID || ptrID2 == INVALID_POINTER_ID){
                    detectingTwoFingersGesture = false;
                    twoFingerRotation = 0.f;
                    twoFingerDragX = 0.f;
                    twoFingerDragY = 0.f;
                    if( endTime-initTime < FAST_GRASPING_TIME_THREASHOLD){
                        if(currDistance-initDistance > FAST_GRASPING_DISTANCE_THREASHOLD)
                            currentPinch = MIN_GRASP;
                        else if(initDistance-currDistance > FAST_GRASPING_DISTANCE_THREASHOLD)
                            currentPinch = MAX_GRASP;
                        setTwoFingerPinch(currentPinch);
                    }
                }

                break;
            case MotionEvent.ACTION_CANCEL:
                ptrID1 = INVALID_POINTER_ID;
                ptrID2 = INVALID_POINTER_ID;
                ptrID3 = INVALID_POINTER_ID;
                detectingOneFingerGesture = false;
                detectingTwoFingersGesture = false;
                detectingThreeFingersGesture = false;
                threeFingerDragY = 0.f;
                threeFingerDragX = 0.f;
                twoFingerRotation = 0.f;
                twoFingerDragX = 0.f;
                twoFingerDragY = 0.f;
                break;
        }

    }

    @Override
    public void onLongPress(MotionEvent event) {
        twoFingerDragX = event.getX();
        twoFingerDragY = event.getY();
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

    public float getOneFingerDragX() {
        return oneFingerDragX;
    }

    public void setOneFingerDragX(float oneFingerDragX) {
        this.oneFingerDragX = oneFingerDragX;
    }

    public float getOneFingerDragY() {
        return oneFingerDragY;
    }

    public void setOneFingerDragY(float oneFingerDragY) {
        this.oneFingerDragY = oneFingerDragY;
    }

    public float getTwoFingerDragX() {
        return twoFingerDragX;
    }

    public void setTwoFingerDragX(float twoFingerDragX) {
        this.twoFingerDragX = twoFingerDragX;
    }

    public float getTwoFingerDragY() {
        return twoFingerDragY;
    }

    public void setTwoFingerDragY(float twoFingerDragY) {
        this.twoFingerDragY = twoFingerDragY;
    }

    public float getTwoFingerRotation() {
        return twoFingerRotation;
    }

    public void setTwoFingerRotation(float twoFingerRotation) {
        this.twoFingerRotation = twoFingerRotation;
    }

    public float getTwoFingerPinch() {
        return twoFingerPinch;
    }

    public void setTwoFingerPinch(float twoFingerPinch) {
        this.twoFingerPinch = twoFingerPinch;
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

    public float getThreeFingerDragY() {
        return threeFingerDragY;
    }

    public void setThreeFingerDragY(float threeFingerDragY) {
        this.threeFingerDragY = threeFingerDragY;
    }

    public float getThreeFingerDragX() {
        return threeFingerDragX;
    }

    public void setThreeFingerDragX(float threeFingerDragX) {
        this.threeFingerDragX = threeFingerDragX;
    }

    public boolean isDetectingOneFingerGesture() {
        return detectingOneFingerGesture;
    }

    public void setDetectingOneFingerGesture(boolean detectingOneFingerGesture) {
        this.detectingOneFingerGesture = detectingOneFingerGesture;
    }

    public boolean isDetectingTwoFingersGesture() {
        return detectingTwoFingersGesture;
    }

    public void setDetectingTwoFingersGesture(boolean detectingTwoFingersGesture) {
        this.detectingTwoFingersGesture = detectingTwoFingersGesture;
    }

    public boolean isDetectingThreeFingersGesture() {
        return detectingThreeFingersGesture;
    }

    public void setDetectingThreeFingersGesture(boolean detectingThreeFingersGesture) {
        this.detectingThreeFingersGesture = detectingThreeFingersGesture;
    }

}