package com.aimfire.demo;

/*
 * Copyright (c) 2016 Aimfire Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.aimfire.camarada.BuildConfig;
import com.aimfire.camarada.R;
import com.aimfire.audio.AudioConfigure;
import com.aimfire.audio.AudioContext;
import com.aimfire.constants.ActivityCode;
import com.aimfire.main.MainConsts;
import com.aimfire.v.p;
import com.aimfire.layout.AspectFrameLayout;
import com.aimfire.gallery.cardboard.PhotoActivity;
import com.aimfire.gallery.MediaScanner;
import com.aimfire.gallery.service.PhotoProcessor;
import com.aimfire.grafika.CameraUtils;
import com.aimfire.main.MainActivity;
import com.aimfire.service.AimfireService;
import com.aimfire.service.AimfireServiceConn;
import com.aimfire.utilities.CustomToast;
import com.aimfire.wifidirect.WifiDirectScanner;
import com.google.firebase.analytics.FirebaseAnalytics;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.nfc.NfcAdapter;
import android.support.v7.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.hardware.SensorManager;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.ShutterCallback;
import android.media.AudioManager;
import android.media.ExifInterface;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.provider.MediaStore.Files.FileColumns;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.MotionEventCompat;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

/**
 * This Activity demonstrates 3D/Stereo image capture
 */
@SuppressWarnings("deprecation")
public class CameraActivity extends Activity 
{
    private static final String TAG = "CameraActivity";

    private static final int CAMERA_MODE_IMAGE = 0;
    private static final int CAMERA_MODE_VIDEO = 1;

    private static final int MEDIA_TYPE_IMAGE = 1;
    private static final int MEDIA_TYPE_VIDEO = 2;

    private static final long P2P_LATENCY_US = 500*1000;

    /*
     * desired photo width and height. all cameras should be natively 
     * landscape, meaning width > height. this may or may not be the same
     * as final jpg output, which is determined by how the photo is taken
     * (in portrait or landscape)
     * TODO: make this configurable, and synchronized across two cameras
     */
    private static final int[] PHOTO_DIMENSION = new int[]{1440, 1080};
    //private static final int[] PHOTO_DIMENSION = new int[]{1280, 720};

    /*
     * whether we add our logo to the picture
     */
    private static final boolean ADD_LOGO = false;

    /*
     * firebase analytics
     */
    private FirebaseAnalytics mFirebaseAnalytics;

    /*
     * use google+ api to set creator info
     * TODO: not yet working
     */
    private String mCreatorName;
    private String mCreatorPhotoUrl;

    /*
     * camera instance variables.
     */
    private CameraClient mCameraClient;
    private CameraPreview mPreview;
    private AspectFrameLayout mPreviewLayout;
    private ImageView mCapturedFrameIV;
    private Bitmap mCapturedFrameBitmap;


    @SuppressLint("UseSparseArrays") private Map<Integer, CaptureInfo> mCaptureInfoSelf = new HashMap<Integer, CaptureInfo>();
    @SuppressLint("UseSparseArrays") private Map<Integer, CaptureInfo> mCaptureInfoRemote = new HashMap<Integer, CaptureInfo>();

    /*
     * index of picture within current session
     */
    private int mImgInd = 0;

    /*
     * synchronization variables
     */
    private boolean mIsLeft;
	private long mSyncTimeUs = Long.MAX_VALUE;

    /*
     * handling device orientation changes
     */
    private int mNaturalOrientation; //fixed for device
    private int mLandscapeOrientation; //fixed for device
    private int mCurrDeviceOrientation = -1;
    private OrientationEventListener mOrientationEventListener = null;

	/*
	 * AimfireService
	 */
    private AimfireServiceConn mAimfireServiceConn;
    private AimfireService mAimfireService;
    private boolean mP2pConnected = true;

    /*
     * name of current thumb
     */
    private String mCurrThumbLink = null;

    /*
	 * state of remote camera and ourselves
	 */
	private boolean mRemoteCameraPaused = false;
    private boolean mLocalCameraPaused = false;

    /*
     * flag indicates switching to Camcorder
     */
    private boolean mSwitchPhotoVideo = false;

    /*
     * attempt to detect whether two devices are stacked. set but not used
     */
    private boolean mStacked = false;
    private final Handler mLongPressHandler = new Handler();

    /*
     * shutter sound player
     */
    private MediaPlayer mShutterSoundPlayer = null;

    /*
     * UI elements
     */
    private ImageButton mCaptureButton;
    private ImageButton mView3DButton;
    private ImageButton mExitButton;
    private ImageButton mPvButton;
    private ImageButton mFbButton;
    private Button mLevelButton;
    private ImageButton mModeButton;

    /**
     * BroadcastReceiver for incoming cloud service messages.
     */
    private BroadcastReceiver mAimfireServiceMsgReceiver = new BroadcastReceiver() 
    {
        @Override
        public void onReceive(Context context, Intent intent) 
        {
            int messageCode = intent.getIntExtra(MainConsts.EXTRA_WHAT, -1);
            switch(messageCode)
            {
 		    case MainConsts.MSG_AIMFIRE_SERVICE_P2P_DISCONNECTED:
				launchMain();
	    	    break;
 		    case MainConsts.MSG_AIMFIRE_SERVICE_P2P_FAILURE:
   	    	    /*
	    	     * most likely the underlying P2P link is broken,
	    	     */
                int code = intent.getIntExtra(MainConsts.EXTRA_CMD, -1);
                switch(code)
                {
                    case MainConsts.MSG_REPORT_SEND_COMMAND_RESULT:
                        if(BuildConfig.DEBUG) Log.e(TAG, "onReceive: p2p send file failure");
                        break;
                    case MainConsts.MSG_REPORT_SEND_FILE_RESULT:
                        if(BuildConfig.DEBUG) Log.e(TAG, "onReceive: p2p send file failure");
                        break;
                    case MainConsts.MSG_REPORT_SEND_STRING_RESULT:
                        if(BuildConfig.DEBUG) Log.e(TAG, "onReceive: p2p send string failure");
                        break;
                    case MainConsts.MSG_REPORT_SEND_PEER_LIST_RESULT:
                        if(BuildConfig.DEBUG) Log.e(TAG, "onReceive: p2p send peer list failure");
                        break;
                    case MainConsts.MSG_REPORT_SEND_PEER_INFO_RESULT:
                        if(BuildConfig.DEBUG) Log.e(TAG, "onReceive: p2p send peer info failure");
                        break;
                    default:
                        if(BuildConfig.DEBUG) Log.e(TAG, "onReceive: p2p unknown failure");
                        break;
                }
                CustomToast.show(getActivity(),
                        getString(R.string.error_p2p_failure),
                        Toast.LENGTH_LONG);
                launchMain();
	    	    break;
 		    case MainConsts.MSG_AIMFIRE_SERVICE_P2P_FILE_RECEIVED:
	    	    /*
	    	     * photo received from remote device. process it
	    	     */
                String filename = intent.getStringExtra(MainConsts.EXTRA_FILENAME);
                if(BuildConfig.DEBUG) Log.d(TAG, "onReceive: received file from remote device: "
                        + filename);

                processStereoPair(filename);
	    	    break;
		    case MainConsts.MSG_AIMFIRE_SERVICE_P2P_COMMAND_RECEIVED:
                String commandStr = intent.getStringExtra(MainConsts.EXTRA_CMD);
                String tokens[] = commandStr.split(":");
                int command = Integer.parseInt(tokens[0]);

                switch(command)
                {
                case MainConsts.CMD_DEMO_CAMERA_ACTION_START:
	    	        /*
	    	         * action start and start time received
	    	         */
                    long captureStartUs = mSyncTimeUs + Long.parseLong(tokens[1]);
                    mImgInd = Integer.parseInt(tokens[2]);

   	                /*
   	                 * tell camera to prepare capture: lock AE, WB. it also
   	                 * enables preview callback, within which frames will be
   	                 * captured at the right moment
   	                 */
   	                if(!mCameraClient.prepareCapture(captureStartUs))
   	                {
                	    /*
                	     * previous capture still in progress. this shouldn't happen.
                	     * because the camera will not be sending an CAMERA_ACTION_START
                	     * unless it has finished the previous capture. however, this
                	     * condition may happen in corner cases.
                	     */
                        if(BuildConfig.DEBUG) Log.d(TAG, "CMD_DEMO_CAMERA_ACTION_START received, previous capture still " +
                       		"in progress.");
                        
                        if(mAimfireService != null)
                        {
                            mAimfireService.sendStringToPeer(true,
                       		    MainConsts.CMD_DEMO_CAMERA_REPORT_CAP_TIME + ":" +
                                Integer.toString(mImgInd) + ":" +
                                Integer.toString(Integer.MAX_VALUE) + ":" + 
                                Integer.toString(Integer.MAX_VALUE));
                        }
   	                    mCaptureInfoSelf.put(mImgInd, new CaptureInfo(
                    		new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE}));

                        return;
   	                }

                    long timeToWaitUs = captureStartUs - SystemClock.elapsedRealtimeNanos()/1000;
                    if(BuildConfig.DEBUG) Log.d(TAG, "CMD_DEMO_CAMERA_ACTION_START received, mSyncTimeUs=" + mSyncTimeUs +
                   		", captureStartUs=" + captureStartUs +
                   		", timeToWaitUs=" + timeToWaitUs);

                    if(timeToWaitUs < 0)
                    {
                   	    if(BuildConfig.DEBUG) Log.e(TAG, "CMD_DEMO_CAMERA_ACTION_START: missed capture!");
	                    CustomToast.show(getActivity(), 
        		            getActivity().getString(R.string.error_excessive_p2p_latency) +
        		            ": " + (P2P_LATENCY_US-timeToWaitUs)/1000 + "ms" +
        		            ", index=" + mImgInd,
        		            Toast.LENGTH_LONG);
                    }
                    else if(timeToWaitUs > P2P_LATENCY_US)
                    {
                   	    if(BuildConfig.DEBUG) Log.e(TAG, "CMD_DEMO_CAMERA_ACTION_START: start timing is wrong!");
	                    CustomToast.show(getActivity(), 
        		            getActivity().getString(R.string.error_start_timing_wrong) +
        		            ": " + timeToWaitUs/1000 + "ms" + ", index=" + mImgInd,
        		            Toast.LENGTH_LONG);

                    }
                    mFirebaseAnalytics.logEvent(MainConsts.FIREBASE_CUSTOM_EVENT_SYNC_PHOTO_CAPTURE_START, null);
	    	        break;
                case MainConsts.CMD_DEMO_CAMERA_ACTION_END:
	    	        /*
	    	         * action stop received. not applicable to this demo
	    	         */
	    	        break;
                case MainConsts.CMD_DEMO_CAMERA_ACTION_SWITCH_PHOTO_VIDEO:
               	    /*
               	     * switch photo->video or video->photo mode in sync
               	     * with the remote device
               	     */
               	    switchPhotoVideo(Integer.parseInt(tokens[1]));
	    	        break;
                case MainConsts.CMD_DEMO_CAMERA_ACTION_SWITCH_FRONT_BACK:
               	    /*
               	     * switch front->back or back->front mode in sync
               	     * with the remote device
               	     */
               	    switchFrontBack(Integer.parseInt(tokens[1]));
	    	        break;
                case MainConsts.CMD_DEMO_CAMERA_REPORT_CAP_TIME:
	    	        /*
	    	         * capture time of remote device
	    	         */
 	                int ind = Integer.parseInt(tokens[1]);
 	                int diff1 = Integer.parseInt(tokens[2]);
 	                int diff2 = Integer.parseInt(tokens[3]);
 	                receiveCapTime(ind, diff1, diff2);
	    	        break;
 		        case MainConsts.CMD_DEMO_START:
	    	        /*
	    	         * demo was started. we shouldn't normally get this message
	    	         * as demo is started by AimfireService and not by command
	    	         * from remote device
	    	         */
                    break;
 		        case MainConsts.CMD_DEMO_END:
	    	        /*
	    	         * demo was ended on the remote device. finish it on this device
	    	         */
	    	        launchMain();
                    break;
 		        case MainConsts.CMD_DEMO_STOP:
	    	        /*
	    	         * demo is put to background on the remote device
	    	         */
	                mRemoteCameraPaused = true;
                    break;
 		        case MainConsts.CMD_DEMO_RESTART:
	    	        /*
	    	         * demo is put to foreground on the remote device
	    	         */
	                mRemoteCameraPaused = false;
                    break;
                default:
           	        break;
                }
            }
        }
    };

    /**
     * BroadcastReceiver for incoming media processor messages.
     */
    private BroadcastReceiver mPhotoProcessorMsgReceiver = new BroadcastReceiver() 
    {
        @Override
        public void onReceive(Context context, Intent intent) 
        {
       	    File sbsFile;
       	    String filePath;

            int messageCode = intent.getIntExtra(MainConsts.EXTRA_WHAT, -1);
            switch(messageCode)
            {
            case MainConsts.MSG_PHOTO_PROCESSOR_RESULT:
                filePath = intent.getStringExtra(MainConsts.EXTRA_PATH);
            		
           		if(BuildConfig.DEBUG) Log.d(TAG, "onReceive: " + filePath + " processing done");

           		/*
           		 * toast any error encountered in auto aligning the images
           		 * TODO: save the status in a separate file and show it
           		 * with the image.
           		 */
                boolean isComfy = intent.getBooleanExtra(MainConsts.EXTRA_COMFY, false);

                if(!isComfy && !mLocalCameraPaused)
                {
                    sbsFile = new File(filePath);
	                CustomToast.show(getActivity(), 
           		        getString(R.string.error_too_much_depth) +
	                    " " + sbsFile.getName(), 
        		        Toast.LENGTH_LONG);
                }

                loadCurrThumbnail();
           	    break;
            case MainConsts.MSG_PHOTO_PROCESSOR_ERROR:
                filePath = intent.getStringExtra(MainConsts.EXTRA_PATH);

           		if(BuildConfig.DEBUG) Log.d(TAG, "onReceive: " + filePath + " auto alignment error");

                if(!mLocalCameraPaused)
                {
                    CustomToast.show(getActivity(), 
                        getString(R.string.error_photo_alignment), 
       		            Toast.LENGTH_LONG);
                }
                break;
            default:
           	    break;
            }
        }
    };

    Runnable mAimfireServiceInitTask = new Runnable() {
	    public void run() 
	    {
    	    while((mAimfireService = mAimfireServiceConn.getAimfireService()) == null)
    	    {
    	        try {
				    Thread.sleep(10);
			    } catch (InterruptedException e) {
                    if(BuildConfig.DEBUG) Log.d(TAG, "mAimfireServiceInitTask interrupted, exiting");
				    return;
			    }
    	    }

            /*
             * let AimfireService know current app type as we may have
             * switched from camcorder mode
             */
            mAimfireService.setAppType(ActivityCode.CAMERA.getValue());
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        if(BuildConfig.DEBUG) Log.d(TAG, "create CameraActivity");

        checkPreferences();

        /*
         *  keep the screen on until we turn off the flag 
         */
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        /*
         * Obtain the FirebaseAnalytics instance.
         */
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

        /*
         * disable nfc push
         */
        NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter != null)
            nfcAdapter.setNdefPushMessage(null, this);

        /*
         * get the natural orientation of this device. need to be called before
         * we fix the display orientation
         */
        mNaturalOrientation = getDeviceDefaultOrientation();

        /*
         * force CameraActivity in landscape because it is the natural 
         * orientation of the camera sensor
         */
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        mLandscapeOrientation = getDeviceLandscapeOrientation();

        Bundle extras = getIntent().getExtras();
        if (extras == null) 
        {
            if(BuildConfig.DEBUG) Log.e(TAG, "onCreate: error create CameraActivity, wrong parameter");
   	        finish();
   	        return;
        }

        /*
         *  make sure we have camera
         */
        if(!checkCameraHardware(this))
        {
            if(BuildConfig.DEBUG) Log.e(TAG, "onCreate: error create CameraActivity, cannot find camera!!!");
   	        finish();
   	        return;
        }

        mIsLeft = extras.getBoolean(MainConsts.EXTRA_ISLEFT);

        mView3DButton = (ImageButton) findViewById(R.id.view3D_button);
        mExitButton = (ImageButton) findViewById(R.id.exit_button);
        mCaptureButton = (ImageButton) findViewById(R.id.capture_button);
		mPvButton = (ImageButton) findViewById(R.id.switch_photo_video_button);
		mFbButton = (ImageButton) findViewById(R.id.switch_front_back_button);
        mLevelButton = (Button) findViewById(R.id.level_button);
        mModeButton = (ImageButton) findViewById(R.id.mode_button);

        if(mIsLeft)
        {
            mCaptureButton.setImageResource(R.drawable.ic_photo_camera_black_24dp);
        }
        else
        {
            mCaptureButton.setVisibility(View.INVISIBLE);

            mPvButton.setVisibility(View.INVISIBLE);
            mFbButton.setVisibility(View.INVISIBLE);
        }
        
        mView3DButton.setOnClickListener(oclView3D);
        mExitButton.setOnClickListener(oclExit);
        mPvButton.setOnClickListener(oclPV);
        mFbButton.setOnClickListener(oclFB);
        mCaptureButton.setOnClickListener(oclCapture);

        /*
         * we could get here in two ways: 1) directly after MainActivity ->
         * AimfireService sync with remote device. 2) we could get here 
         * because of a switch from video to photo mode. 
         * 
         * mSyncTimeUs is determined by AudioContext. each device 
         * calculates it, and they correspond to the same absolute moment 
         * in time
         */
        mSyncTimeUs = extras.getLong(MainConsts.EXTRA_SYNCTIME, -1);

        /*
         * start camera client object in a dedicated thread
         */
        mCameraClient = new CameraClient(Camera.CameraInfo.CAMERA_FACING_BACK, 
       		PHOTO_DIMENSION[0], PHOTO_DIMENSION[1]);

        /*
         * create our SurfaceView for preview 
         */
        mPreview = new CameraPreview(this);
        mPreviewLayout = (AspectFrameLayout) findViewById(R.id.cameraPreview_frame);
        mPreviewLayout.addView(mPreview);
        mPreviewLayout.setOnTouchListener(otl);
        if(BuildConfig.DEBUG) Log.d(TAG, "add camera preview view");

        mShutterSoundPlayer = MediaPlayer.create(this,
       		Uri.parse("file:///system/media/audio/ui/camera_click.ogg"));

        /*
         * place UI controls at their initial, default orientation
         */
        adjustUIControls(0);

        /*
         * load the latest thumbnail to the view3D button
         */
        loadCurrThumbnail();

        /*
         * initializes AimfireService, and bind to it
         */
        mAimfireServiceConn = new AimfireServiceConn(this);

        /*
         * binding doesn't happen until later. wait for it to happen in another 
         * thread and connect to p2p peer if necessary
         */
        (new Thread(mAimfireServiceInitTask)).start();

        if(ADD_LOGO)
        {
            /*
             * init our logo that will be embedded in processed photos
             */
            AssetManager assetManager = getAssets();
            p.getInstance().a(assetManager, MainConsts.MEDIA_3D_RAW_PATH, "logo.png"); 
        }

        /*
         * register for AimfireService message broadcast
         */
        LocalBroadcastManager.getInstance(this).registerReceiver(mAimfireServiceMsgReceiver,
            new IntentFilter(MainConsts.AIMFIRE_SERVICE_MESSAGE));

	    /*
	     * register for intents sent by the media processor service
	     */
        LocalBroadcastManager.getInstance(this).registerReceiver(mPhotoProcessorMsgReceiver,
            new IntentFilter(MainConsts.PHOTO_PROCESSOR_MESSAGE));
    }
    
    @Override
    public void onStop()
    {
   	    super.onStop();

        /*
	     * screen can turn off now. 
       	 */
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
   
        /*
         * user decided to put us in background, or we are on our way to
         * be destroyed. here "stop" is in activity lifecycle's sense,
         * it doesn't mean we "end" the demo. If we are switching to 
         * Camcorder mode, we have already stopped camera.
         */
        if(!mSwitchPhotoVideo)
        {
       	    stopCamera();
        	    
            if(mAimfireService != null)
            {
                mAimfireService.stopDemo();
            }
        }

	    /*
	     * de-register for intents sent by the Aimfire service
	     */
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mAimfireServiceMsgReceiver);

        mSwitchPhotoVideo = false;
        mLocalCameraPaused = true;
    }

    @Override
    public void onRestart()
    {
   	    super.onRestart();

        /*
         *  keep the screen on until we turn off the flag 
         */
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        /*
         * start camera client object in a dedicated thread
         */
        mCameraClient = new CameraClient(Camera.CameraInfo.CAMERA_FACING_BACK, 
       		PHOTO_DIMENSION[0], PHOTO_DIMENSION[1]);

        /*
         * create our SurfaceView for preview 
         */
        mPreview = new CameraPreview(this);
        mPreviewLayout = (AspectFrameLayout) findViewById(R.id.cameraPreview_frame);
        mPreviewLayout.addView(mPreview);
        if(BuildConfig.DEBUG) Log.d(TAG, "add camera preview view");

        /*
         * place UI controls at their initial, default orientation
         */
        adjustUIControls(0);

        /*
         * load the latest thumbnail to the view3D button. we need
         * to do this because we (the CameraActivity) has been in
         * the background, and during this time, GalleryActivity may
         * have deleted the thumb and it's associated image that was
         * showing before
         */
        loadCurrThumbnail();

        if(mOrientationEventListener != null)
     	    mOrientationEventListener.enable();
    	    
        LocalBroadcastManager.getInstance(this).registerReceiver(mAimfireServiceMsgReceiver,
            new IntentFilter(MainConsts.AIMFIRE_SERVICE_MESSAGE));

        if((mAimfireService != null) && mAimfireService.isP2pConnected())
        {
            mAimfireService.restartDemo();
        }
        else
        {
       	    /*
       	     * during the time this activity is not in foreground, remote device
       	     * may have been disconnected, in which case we terminate.
       	     */
       	    finish();
        }

        mLocalCameraPaused = false;
    }

    @Override
    public void onDestroy()
    {
        /*
	     * screen can turn off now.
       	 */
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if(mAimfireServiceConn != null)
   	    {
   	        mAimfireServiceConn.unbind();
   	    }

	    /*
	     * de-register for intents sent by the media processor service
	     */
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mPhotoProcessorMsgReceiver);

   	    super.onDestroy();
    }

    @Override
    public void onBackPressed()
    {
   	    /*
   	     * disable the back button as it's easy to accidentally
   	     * touch it while position the cameras
   	     */
   	    //exitCamera();
    }

    Runnable mLongPressRunnable = new Runnable() { 
        public void run() { 
      	    mStacked = true;
       	    if(BuildConfig.DEBUG) Log.d(TAG, "long press detected, devices stacked");
        }   
    };

    OnTouchListener otl = new OnTouchListener() {
        public boolean onTouch(View v, MotionEvent event) 
        {
            int action = MotionEventCompat.getActionMasked(event);
            
            switch(action)
            {
            case (MotionEvent.ACTION_DOWN) :
           	    //if(BuildConfig.DEBUG) Log.d(TAG, "touch major = " + sizeMajor + ", minor = " + sizeMinor);
                mLongPressHandler.postDelayed(mLongPressRunnable, 2000);
                return true;
            case (MotionEvent.ACTION_POINTER_DOWN) :
                return false;
            case (MotionEvent.ACTION_POINTER_UP) :
                return false;
            case (MotionEvent.ACTION_MOVE) :
           	    //if(BuildConfig.DEBUG) Log.d(TAG, "touch major = " + sizeMajor + ", minor = " + sizeMinor);
                return true;
            case (MotionEvent.ACTION_UP) :
            case (MotionEvent.ACTION_CANCEL) :
                mLongPressHandler.removeCallbacks(mLongPressRunnable);
   	            if(mStacked)
   	            {
   	                mStacked = false;
       	            if(BuildConfig.DEBUG) Log.d(TAG, "devices un-stacked");
   	            }
                return true;
            case (MotionEvent.ACTION_OUTSIDE) :
                return true;      
            default : 
                return true;
            }      
        }
    };

    private void exitCamera()
    {
	    int warning;
	    if(WifiDirectScanner.getInstance().isSendingOrReceiving())
	    {
            warning = R.string.warning_file_transfer_in_progress;
	    }
	    else
	    {
            warning = R.string.warning_exit_camera;
	    }

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getActivity(), R.style.AppCompatAlertDialogStyle);
        alertDialogBuilder.setTitle(R.string.warning);
        alertDialogBuilder.setMessage(warning);
        
        alertDialogBuilder.setPositiveButton(R.string.cont, new DialogInterface.OnClickListener() {
           @Override
           public void onClick(DialogInterface dialog, int which) 
           {
   	           dialog.dismiss();
	           launchMain();
           }
        });
        
        alertDialogBuilder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
           @Override
           public void onClick(DialogInterface dialog, int which) 
           {
               //do nothing
           }
        });
    
        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }

    private void launchMain()
    {
        final Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(MainConsts.EXTRA_MSG, true/*mIsMyMedia*/);

        /*
   	     * we want to go back to MainActivity instead of CamcorderActivity
   	     * if it was launched before us
   	     */
        startActivity(intent);

        /*
         * exit and release the audio context and disconnect p2p. we do it here instead of
         * onDestroy because if we switch to CamcorderActivity we don't want to release
         * audio context or disconnect p2p
         */
        if(mAimfireService != null)
        {
            mAimfireService.endDemo();
        }

        /*
         * exit out of camera, not really necessary because of the CLEAR_TOP
         */
   	    finish();
    }

    /**
     * not used - we fix display orientation. we could use it if, say, we have a
     * settings menu popped up and we want to allow it to change orientation
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) 
    {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onWindowFocusChanged (boolean hasFocus) 
    {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus)
        {
       	    forceFullScreen();
        }
    }

	public boolean onKeyDown(int keyCode, KeyEvent event) 
	{ 
		//if((keyCode == KeyEvent.KEYCODE_VOLUME_UP) || 
	   //(keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) ||
	   //(keyCode == KeyEvent.KEYCODE_CAMERA))
		if(keyCode == KeyEvent.KEYCODE_CAMERA)
	    {
			clickCapture(null);
			return true;
	    }
		else if(keyCode == KeyEvent.KEYCODE_MENU)
		{
			/*
			 * TODO: handle hardware menu button
			 */
            return true;
		}
        return super.onKeyDown(keyCode, event); 
    }

    private void checkPreferences() 
    {
        SharedPreferences settings =
            getSharedPreferences(getString(R.string.settings_file), Context.MODE_PRIVATE);

        if (settings != null) 
        {
            mCreatorName = settings.getString(MainConsts.DRIVE_PERSON_NAME, null);
            mCreatorPhotoUrl = settings.getString(MainConsts.DRIVE_PERSON_PHOTO_URL, null);
        }
    }

    private void forceFullScreen()
    {
        if (Build.VERSION.SDK_INT < 16) 
        {
            /*
             * if the Android version is lower than Jellybean, use this call to hide
             * the status bar.
             */
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                                 WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        else
        {
       	    View decorView = getWindow().getDecorView();
       	    /*
       	     * hide the status bar.
       	     */
       	    int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN;
       	    decorView.setSystemUiVisibility(uiOptions);
       	    /*
       	     * remember that we should never show the action bar if the
       	     * status bar is hidden, so hide that too if necessary.
       	     */
       	    ActionBar actionBar = getActionBar();
       	    if(actionBar != null)
       	    {
       	        actionBar.hide();
       	    }
        }
    }

    private void adjustAspectRatio(final float ratio)
    {
        Runnable adjustAspectRunnable = new Runnable() {
   	        public void run()
   	        {
                if(mPreviewLayout != null)
                {
       	            if(BuildConfig.DEBUG) Log.d(TAG, "adjustAspectRatio: set to " + ratio);
                    mPreviewLayout.setAspectRatio(ratio);
                }
                else
                {
       	            if(BuildConfig.DEBUG) Log.e(TAG, "unable to set aspect ratio for preview window!");
                }
       	    }
        };
        runOnUiThread(adjustAspectRunnable);
    }

    private void adjustUIControls(int rotation)
    {
		RelativeLayout.LayoutParams layoutParams =
		    (RelativeLayout.LayoutParams)mCaptureButton.getLayoutParams();
		layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, 0);
		layoutParams.addRule(RelativeLayout.CENTER_VERTICAL, RelativeLayout.TRUE);
		mCaptureButton.setLayoutParams(layoutParams);
		mCaptureButton.setRotation(rotation);

        layoutParams = (RelativeLayout.LayoutParams)mPvButton.getLayoutParams();
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, 0);
        layoutParams.addRule(RelativeLayout.ABOVE, 0);
        layoutParams.addRule(RelativeLayout.BELOW, R.id.capture_button);
        mPvButton.setLayoutParams(layoutParams);
        mPvButton.setRotation(rotation);

        /*
		layoutParams = (RelativeLayout.LayoutParams)mFbButton.getLayoutParams();
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, 0);
        layoutParams.addRule(RelativeLayout.ABOVE, R.id.capture_button);
        layoutParams.addRule(RelativeLayout.BELOW, 0);
		mFbButton.setLayoutParams(layoutParams);
		mFbButton.setRotation(rotation);
		*/

		layoutParams = (RelativeLayout.LayoutParams)mExitButton.getLayoutParams();
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT, 0);
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE);
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 0);
		mExitButton.setLayoutParams(layoutParams);
		mExitButton.setRotation(rotation);
	

        layoutParams = (RelativeLayout.LayoutParams)mView3DButton.getLayoutParams();
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, 0);
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0);
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
        mView3DButton.setLayoutParams(layoutParams);
        mView3DButton.setRotation(rotation);

        layoutParams = (RelativeLayout.LayoutParams)mModeButton.getLayoutParams();
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 0);
        layoutParams.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE);
        layoutParams.addRule(RelativeLayout.LEFT_OF, 0);
        layoutParams.addRule(RelativeLayout.RIGHT_OF, 0);
        mModeButton.setLayoutParams(layoutParams);
        mModeButton.setRotation(rotation);

        layoutParams = (RelativeLayout.LayoutParams)mLevelButton.getLayoutParams();
		layoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
		layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 0);
		layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, 0);
		mLevelButton.setLayoutParams(layoutParams);
		mLevelButton.setRotation(rotation);
		
		CustomToast.setRotation(rotation);
    }

    /**
     * shows deviation from level.
     * @param deviation - in degrees, should be within +/- 45
     */
    private void showDeviationFromLevel(int deviation)
    {
		if(Math.abs(deviation) < 3)
		    mLevelButton.setBackgroundResource(R.drawable.round_button_green);
		else
		    mLevelButton.setBackgroundResource(R.drawable.round_button_not_level);

		mLevelButton.setText(Integer.toString(deviation) + "\u00b0");
    }

    public Activity getActivity()
    {
    	    return this;
    }

    /**
     * get camera object instance created by CameraClient
     * @return camera object
     */
    public CameraClient getCameraClient()
    {
    	    return mCameraClient;
    }

    public int getDeviceDefaultOrientation()
    {
        WindowManager windowManager = 
      		(WindowManager) getSystemService(Context.WINDOW_SERVICE);

        Configuration config = getResources().getConfiguration();

        int rotation = windowManager.getDefaultDisplay().getRotation();

        if ( ((rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) &&
                config.orientation == Configuration.ORIENTATION_LANDSCAPE)
            || ((rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) &&    
                config.orientation == Configuration.ORIENTATION_PORTRAIT)) 
        {
            return Configuration.ORIENTATION_LANDSCAPE;
        } 
        else 
        { 
            return Configuration.ORIENTATION_PORTRAIT;
        }
    }

    public int getDeviceLandscapeOrientation()
    {
   	    int degrees = 0;
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        switch (rotation) 
        {
            case Surface.ROTATION_0: degrees = 0; break;
            case Surface.ROTATION_90: degrees = 90; break;
            case Surface.ROTATION_180: degrees = 180; break; // this is not possible
            case Surface.ROTATION_270: degrees = 270; break;
        }
        // reverse the sign to get clockwise rotation
        return (360-degrees)%360;
    }

    /** 
     * we had camera permission, and use-feature in manifest, but we don't require it, so 
     * check to make sure it exists
     */
    private boolean checkCameraHardware(Context context) 
    {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA))
        {
            // this device has a camera
            return true;
        } 
        else 
        {
            // no camera on this device
            return false;
        }
    }
    
    /**
     * onClick handler for "view3D" button.
     */
    OnClickListener oclView3D = new OnClickListener() {
        @Override
        public void onClick(View view) 
        {
   	        if(mCurrThumbLink==null)
   	        {
    	        if(BuildConfig.DEBUG) Log.d(TAG, "clickView3D: mCurrThumbLink is null.");
    	        return;
   	        }
    
   	        /*
   	         * note here we don't use FLAG_ACTIVITY_CLEAR_TOP because doing so
   	         * will kill camera
   	         */
            //Intent intent = new Intent(getActivity(), GalleryActivity.class);
            Intent intent = new Intent(getActivity(), PhotoActivity.class);
            intent.setData(Uri.fromFile(new File(mCurrThumbLink)));
            intent.putExtra(MainConsts.EXTRA_MSG, true/*isMyMedia*/);
            startActivity(intent);
        }
    };

    /**
     * onClick handler for "exit" button.
     */
    OnClickListener oclExit = new OnClickListener() {
        @Override
        public void onClick(View view) 
        {
    	        exitCamera();
        }
    };

    public void loadCurrThumbnail()
    {
        String sbsPath = null;
        Bitmap currThumb = null;

        ArrayList<String> list = MediaScanner.getNonEmptyPhotoList(MainConsts.MEDIA_3D_SAVE_DIR);
        
        /*
         * we search for the latest sbs file that has a thumbnail
         */
        for(int i=0; i<list.size(); i++)
        {
       	    String path = list.get(i);
            String thumbPath = MainConsts.MEDIA_3D_THUMB_PATH + (new File(path)).getName();

   	        /*
   	         * if no thumbnail exists for this sbs file, it means the sbs
   	         * file is a placeholder while we process the image pair
   	         */
            if((new File(thumbPath)).exists())
            {
                currThumb = BitmapFactory.decodeFile(thumbPath);
                sbsPath = path;
                
                /*testing*/
                extractCreatorInfo(thumbPath);
                /*testing end*/
                break;
            }
        }

        if(currThumb != null)
        {
            mCurrThumbLink = sbsPath;
            mView3DButton.setImageBitmap(currThumb);
        }
        else
        {
            mCurrThumbLink = null;
            mView3DButton.setImageResource(R.drawable.ic_local_florist_black_24dp);
        }
    }

    private void extractCreatorInfo(String path)
    {
        ExifInterface exif;
		try {
		    exif = new ExifInterface(path);
		    /*
		     * TAG_ARTIST and TAG_USER_COMMENT only added in API 24
		     */
            //String creatorName = exif.getAttribute(ExifInterface.TAG_ARTIST);
            //String creatorPhotoUrl = exif.setAttribute(ExifInterface.TAG_USER_COMMENT);
            String creatorName = exif.getAttribute("Artist");
            String creatorPhotoUrl = exif.getAttribute("UserComment");
            if(BuildConfig.DEBUG) Log.e(TAG, "extractCreatorInfo: " + 
                "creatorName=" + creatorName + ", creatorPhotoUrl=" + creatorPhotoUrl);
	    } catch (IOException e) {
            if(BuildConfig.DEBUG) Log.e(TAG, "extractCreatorInfo: " + e.getMessage());
	    }
    }

    public void processStereoPair(final String filename)
    {
        if(BuildConfig.DEBUG) Log.d(TAG, "processStereoPair: file received from remote device, name = " 
            + filename);

        final int index;
	    String [] tmp1 = filename.split("_");
	    if(tmp1.length > 4)
	    {
	        String [] tmp2 = tmp1[4].split("\\.");
	        index = Integer.parseInt(tmp2[0]);
	    }
	    else
	    {
    	    if(BuildConfig.DEBUG) Log.e(TAG, "processStereoPair: received file name is invalid");
    	    return;
	    }

   	    /*
   	     * we now received the better picture from remote device. our jpeg
   	     * encoding may not be done by the time we get here, so wait on a
   	     * separate thread for it if needed
   	     */
	    Runnable processFileTask = new ProcessFileTask(index, filename);
	    new Thread(processFileTask).start();

        /*
         * enable view button
         */
        mView3DButton.setEnabled(true);
    }

    public class ProcessFileTask implements Runnable 
    {
   	    private CaptureInfo captureInfo;
   	    private int index = 0;
   	    private String remoteFilename = null;

   	    public ProcessFileTask(int ind, String filename)
   	    {
    	    index = ind;
       	    captureInfo = mCaptureInfoSelf.get(index);
    	    remoteFilename = filename;
   	    }

        public void run() 
        {
       	    while(captureInfo.isEncoding())
       	    {
   	            try {
			       Thread.sleep(100);
		        } catch (InterruptedException e) {
	                e.printStackTrace();
		        }
       	    }
    	    /*
    	     * ready to rock
    	     */
    	    if(BuildConfig.DEBUG) Log.d(TAG, "ProcessFileTask: CaptureInfo for index " +
                index + " now available");

       	    String path = captureInfo.getCapturePath();
            File localFile = new File(path);
       	    String localFilename = localFile.getName();

            /*
             * send to photo processor service for processing. we always give 
             * the native function scale multiplier for left side. if this 
             * device is the right camera, then we need to convert.
             */
            float scale = CameraUtils.getScale();
   	        if(BuildConfig.DEBUG) Log.d(TAG, "getScale: mIsLeft = " + (mIsLeft?"true":"false") +
    		    ", scale multiplier = " + scale);

            Intent serviceIntent = new Intent(getActivity(), PhotoProcessor.class);
            serviceIntent.putExtra("lname", localFilename);
            serviceIntent.putExtra("rname", remoteFilename);
            serviceIntent.putExtra("creator", mCreatorName);
            serviceIntent.putExtra("photo", mCreatorPhotoUrl);
            serviceIntent.putExtra(MainConsts.EXTRA_SCALE, mIsLeft?scale:1.0f/scale);
			serviceIntent.putExtra(MainConsts.EXTRA_FACING, mCameraClient.getCameraFacing());
            getActivity().startService(serviceIntent);

            /*
             * create an empty, placeholder file, so GalleryActivity or ThumbsFragment can 
             * show a progress bar while this file is generated by PhotoProcessor
             */
            try {
				String procPath = MediaScanner.getProcessedSbsPath(localFilename);
				MediaScanner.addItemMediaList(procPath);
				(new File(procPath)).createNewFile();
			} catch (IOException e) {
   	            if(BuildConfig.DEBUG) Log.e(TAG, "ProcessFileTask: error creating placeholder file");
			}

            mCaptureInfoSelf.remove(index);
            mCaptureInfoRemote.remove(index);
        }
    }

    /**
     * onClick handler for "switch photo/video" button.
     */
    OnClickListener oclPV = new OnClickListener() {
        @Override
        public void onClick(View view) 
        {
   	        if(!mP2pConnected)
   	        {
   	            if(BuildConfig.DEBUG) Log.e(TAG, "switchPhotoVideo: error, P2P not connected");
    	        return;
   	        }
    
   	        if(mRemoteCameraPaused)
   	        {
	            CustomToast.show(getActivity(), 
        		    getActivity().getString(R.string.error_cannot_switch_photo_video),
        		    Toast.LENGTH_LONG);
	            return;
   	        }
    
   	        /*
   	         * inform the remote device that we are switching to video mode
   	         */
            if(mAimfireService != null)
            {
   	            mAimfireService.sendStringToPeer(true,
    		        MainConsts.CMD_DEMO_CAMERA_ACTION_SWITCH_PHOTO_VIDEO + ":" + CAMERA_MODE_VIDEO);
            }
    	    
   	        /*
   	         * switching ourselves
   	         */
   	        switchPhotoVideo(CAMERA_MODE_VIDEO);
        }
    };

    public void switchPhotoVideo(int newMode)
    {
   	    if(newMode == CAMERA_MODE_IMAGE)
   	    {
    	    /*
    	     * something wrong here - we are already in photo mode. ignore
    	     */
    	    return;
  	    }
    	
   	    /*
   	     * we stop the camera here (which means release camera and destroy
   	     * preview surface), because we want the camera to be released
   	     * right away, instead of be done in onStop. The problem with the
   	     * latter is that when Camcorder activity is launched and is
   	     * opening camera, onStop of this activity may not have been called
   	     */
   	    stopCamera();

   	    /*
   	     * switch mode
   	     */
        Intent intent = new Intent(this, CamcorderActivity.class);
        intent.putExtra(MainConsts.EXTRA_ISLEFT, mIsLeft);
        intent.putExtra(MainConsts.EXTRA_SYNCTIME, mSyncTimeUs);
        intent.putExtra(MainConsts.EXTRA_MSG, true/*"switching"*/);
        startActivity(intent);
        
        mSwitchPhotoVideo = true;
    }

    private void stopCamera()
    {
        if(mOrientationEventListener != null)
     	    mOrientationEventListener.disable();
    	    
   	    /*
   	     * close current camera in use. quit its handler thread.
   	     */
   	    if(mCameraClient != null)
   	    {
   	        mCameraClient.deinit();
   	    }

   	    /*
   	     * mPreview surface will be destroyed automatically. but apparently
   	     * if we don't de-register the SurfaceHolder callback, we are going
   	     * to get two callbacks when we instantiate a new CameraPreview when
   	     * we restart the activity
   	     */
   	    if(mPreview != null)
   	    {
   	        mPreview.deinit();
   	    }
    }

    /**
     * onClick handler for "switch front/back" button. note that if this
     * device has only one camera, no switch will happen, but the remote
     * device will switch (if it has two cameras). similarly if this
     * device has two but remote has only one, then only this device will
     * switch but not the remote. this is intended behavior
     */
    OnClickListener oclFB = new OnClickListener() {
        @Override
        public void onClick(View view) 
        {
   	        if(!mP2pConnected)
   	        {
   	            if(BuildConfig.DEBUG) Log.e(TAG, "switchFrontBack: error, P2P not connected");
    	        return;
   	        }
    
   	        if(mRemoteCameraPaused)
   	        {
	            CustomToast.show(getActivity(), 
        		    getActivity().getString(R.string.error_cannot_switch_front_back),
        		    Toast.LENGTH_LONG);
	            return;
   	        }
    
            int newFacing;
            int currFacing = mCameraClient.getCameraFacing();
            if (currFacing == Camera.CameraInfo.CAMERA_FACING_FRONT) 
            {
                newFacing = Camera.CameraInfo.CAMERA_FACING_BACK; 
    
                mFbButton.setImageResource(R.drawable.ic_camera_front_black_24dp);
            }
            else
            {
                newFacing = Camera.CameraInfo.CAMERA_FACING_FRONT; 
    
                mFbButton.setImageResource(R.drawable.ic_camera_rear_black_24dp);

            }
                
    	        /*
    	         * inform the remote device that we are switching to a different facing
    	         */
            if(mAimfireService != null)
            {
   	            mAimfireService.sendStringToPeer(true,
    		        MainConsts.CMD_DEMO_CAMERA_ACTION_SWITCH_FRONT_BACK + ":" + newFacing);
            }
    	    
   	        /*
   	         * switching ourselves
   	         */
   	        switchFrontBack(newFacing);
    	        
   	        /*
   	         * if this is the first time front camera is used, show a warning to user
   	         */
            if(newFacing == Camera.CameraInfo.CAMERA_FACING_FRONT)
            {
                SharedPreferences settings = 
           		    getSharedPreferences(getString(R.string.settings_file), Context.MODE_PRIVATE);
    
       	        if (settings.getBoolean("front_camera_first_time", true))
                {
    	            /*
    	             * TODO: change AlertDialog to a dialog fragment, such that we can
    	             * control the orientation. right now this AlertDialog is always
    	             * landscape because the activity is fixed to landscape.
    	             */
                    AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getActivity(), R.style.AppCompatAlertDialogStyle);
                    alertDialogBuilder.setTitle(R.string.warning);
                    alertDialogBuilder.setMessage(R.string.warning_front_camera);
            
                    alertDialogBuilder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                       @Override
                       public void onClick(DialogInterface arg0, int arg1) 
                       {
               	           //do nothing
                       }
                    });
            
                    AlertDialog alertDialog = alertDialogBuilder.create();
                    alertDialog.show();
                    
   	                // record the fact that the app has been started at least once
   	                settings.edit().putBoolean("front_camera_first_time", false).commit();
                }
            }
        }
    };

    public void switchFrontBack(int newFacing)
    {
        int currFacing = mCameraClient.getCameraFacing();
   	    if(newFacing == currFacing)
   	    {
    	    /*
    	     * something wrong here - we are already facing the correct way.
    	     * ignore
    	     */
    	    return;
   	    }
    	
   	    /*
   	     * switch facing
   	     */
   	    mCameraClient.switchFrontBack(newFacing);
    }

    /**
     * onClick handler for "record" button.
     */
    OnClickListener oclCapture = new OnClickListener() {
        @Override
        public void onClick(View view) 
        {
        	    clickCapture(null);
        }
    };

    /**
     * onClick handler for "capture" button.
     */
    public void clickCapture(View unused) 
    {
   	    if(!mP2pConnected)
   	    {
 	        if(BuildConfig.DEBUG) Log.e(TAG, "clickCapture: error, P2P not connected");
    	    return;
   	    }

   	    if(mRemoteCameraPaused)
   	    {
	        CustomToast.show(getActivity(), 
        		getActivity().getString(R.string.error_cannot_capture_photo),
        		Toast.LENGTH_LONG);
	        return;
   	    }

    	/*
    	 * user initiated capturing. we calculate the time elapsed
    	 * between current time and mSyncTimeUs, then add an extra
    	 * delay which accounts for the P2P latency in sending the
    	 * command to the remote device
    	 */
   	    long captureStartUs = SystemClock.elapsedRealtimeNanos()/1000 + P2P_LATENCY_US;
   	    long delayFromSyncUs = captureStartUs - mSyncTimeUs;

   	    /*
   	     * tell camera to prepare capture: lock AE, WB. it also
   	     * enables preview callback, within which frames will be
   	     * captured at the right moment
   	     */
        if(!mCameraClient.prepareCapture(captureStartUs))
        {
            if(BuildConfig.DEBUG) Log.d(TAG, "clickCapture: previous capture still in progress. " +
          		"Can't start a new one.");
            return;
        }

        /*
         * tell remote device to start recording
         */
        if(mAimfireService != null)
        {
   	        mAimfireService.sendStringToPeer(true,
                    MainConsts.CMD_DEMO_CAMERA_ACTION_START + ":" +
                    Long.toString(delayFromSyncUs) + ":" + Integer.toString(mImgInd));
        }

        mFirebaseAnalytics.logEvent(MainConsts.FIREBASE_CUSTOM_EVENT_SYNC_PHOTO_CAPTURE_START, null);
    }

    /**
     * for now only send the capture time to the cloud
     */
    public void sendCapTime(int diff1, int diff2)
    {
   	    if(!mP2pConnected)
   	    {
   	        if(BuildConfig.DEBUG) Log.e(TAG, "sendCapTime: error, P2P not connected");
    	    return;
   	    }

   	    /*
   	     * it's important to process cap time before calling
   	     * AimfireService to send it to the remote device. this
   	     * is because the sendStringToPeer takes a while to
   	     * finish, and receiveCapTime could be interleaved.
   	     */
    	processCapTime(mImgInd);

   	    if(BuildConfig.DEBUG) Log.d(TAG, "sendCapTime: picture index = " + mImgInd +
    		", diff1=" + diff1 + ", diff2=" + diff2);

        if(mAimfireService != null)
        {
            mAimfireService.sendStringToPeer(true,
       		    MainConsts.CMD_DEMO_CAMERA_REPORT_CAP_TIME + ":" +
                Integer.toString(mImgInd) + ":" +
                Integer.toString(diff1) + ":" + 
                Integer.toString(diff2));
        }
    }

    private void receiveCapTime(int ind, int diff1, int diff2)
    {
   	    if(BuildConfig.DEBUG) Log.d(TAG, "receiveCapTime: picture index = " + ind +
    		", diff1=" + diff1 + ", diff2=" + diff2);

   	    mCaptureInfoRemote.put(ind, new CaptureInfo(new int[]{diff1, diff2}));
   	    processCapTime(ind);
    }

    /**
     * process the capture time received from the other device. 
     */
    private void processCapTime(final int index)
    {
   	    int minDiff = Integer.MAX_VALUE;
        int minIndSelf = 0;
        int minIndRemote = 0;

        CaptureInfo ciSelf = mCaptureInfoSelf.get(index);
        CaptureInfo ciRemote = mCaptureInfoRemote.get(index);

        if((ciSelf == null) || (ciRemote == null))
        {
       	    /*
       	     * either remote or self capture time not available yet.
       	     * just return and wait
       	     */
       	    if(BuildConfig.DEBUG) Log.d(TAG, "processCapTime: CaptureInfo for index " + index +
   	    		" does not exist yet");
       	    return;
        }

        int[] captureTimeSelf = ciSelf.getCaptureTime();
        int[] captureTimeRemote = ciRemote.getCaptureTime();

        if((captureTimeSelf == null) || (captureTimeRemote == null))
        {
       	    if(BuildConfig.DEBUG) Log.e(TAG, "processCapTime: captureTimeSelf or captureTimeRemote " +
   	    		"for index " + index + " is null");
      	    return;
        }

        if((captureTimeRemote[0] == Integer.MAX_VALUE) ||
           (captureTimeRemote[1] == Integer.MAX_VALUE))
        {
       	    /*
       	     * something wrong with capture either with the remote
       	     * device. we will not process. remove CaptureInfo. this
       	     * should free the JpegEncoder object which contains
       	     * copies of frame buffers.
             */
       	    if(BuildConfig.DEBUG) Log.e(TAG, "processCapTime: captureTimeRemote " +
   	    		"for index " + index + " is invalid, remove.");
       	    mCaptureInfoSelf.remove(index);
       	    mCaptureInfoRemote.remove(index);

       	    return;
        }

        for(int i=0; i<2; i++)
        {
       	    for(int j=0; j<2; j++)
       	    {
   	    	    int diff = Math.abs(captureTimeSelf[i] - captureTimeRemote[j]);
   	    	    if(diff < minDiff)
   	    	    {
    	    	    minIndSelf = i;
    	    	    minIndRemote = j;

    	    	    minDiff = diff;
   	    	    }
      	    }
        }

        /*
         * now set the index chosen, which will start encoding the chosen
         * frame into jpeg
         */
        ciSelf.setChosenIndex(minIndSelf);

        if(minDiff > 50)
        {
            CustomToast.show(getActivity(), 
      		    getActivity().getString(R.string.error_capture_time_diff_too_large),
	            Toast.LENGTH_LONG);
        }

   	    if(BuildConfig.DEBUG) Log.d(TAG, "processCapTime: picture index " + index + ", minimum timing " +
    		"difference is " + minDiff + "ms" +
    		", self frame index = " + minIndSelf +
    		", remote frame index = " + minIndRemote);
        
   	    /*
   	     * we now send the better picture to remote device. the jpeg encoding
   	     * may not be done by the time we get here, so wait for it if needed
   	     */
   	    Runnable sendFileTask = new SendFileTask(index);
        (new Thread(sendFileTask)).start();
    }

    public class SendFileTask implements Runnable 
    {
   	    CaptureInfo captureInfo;

   	    public SendFileTask(int ind)
    	    {
    	       captureInfo = mCaptureInfoSelf.get(ind);
    	    }

        public void run() 
        {
            while(captureInfo.isEncoding())
            {
       	        try {
				   Thread.sleep(100);
			    } catch (InterruptedException e) {
		            e.printStackTrace();
			    }
            }

            mAimfireService.sendFileToPeer(captureInfo.getCapturePath());
        }
    }

    public void showJpeg(String filePath)
    {
   	    /*
   	     * remove the preview, because it freezes at the last frame camera client
   	     * feeds it, which appears to be one frame later than the 2nd frame we
   	     * captured
   	     */
   	    mPreviewLayout.removeView(mPreview);
    	    
   	    /*
   	     * mark previous bitmap for GC (shouldn't be necessary, just to be safe)
   	     */
        if((mCapturedFrameBitmap != null) && (!mCapturedFrameBitmap.isRecycled()))
            mCapturedFrameBitmap.recycle();

        if(mCapturedFrameIV == null)
        {
            mCapturedFrameIV = new ImageView(this);
            mPreviewLayout.addView(mCapturedFrameIV);
        }

        while(true)
        {
            mCapturedFrameBitmap = BitmapFactory.decodeFile(filePath);
            if(mCapturedFrameBitmap == null)
            {
           	    try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
            }
            else
            {
                mCapturedFrameIV.setImageBitmap(mCapturedFrameBitmap);
                break;
            }
        }

        if(BuildConfig.DEBUG) Log.d(TAG, "showing " + filePath);
    }

    /** 
     * An inner class that owns the camera surface view
     */
    private class CameraPreview extends SurfaceView implements SurfaceHolder.Callback 
    {
        private SurfaceHolder mHolder;

        public CameraPreview(Context context) 
        {
       	    super(context);

            /*
             * install a SurfaceHolder.Callback so we get notified when the
             * underlying surface is created and destroyed.
             */
            mHolder = getHolder();
            mHolder.addCallback(this);
            //mHolder.setFixedSize(PHOTO_DIMENSION[0], PHOTO_DIMENSION[1]);
            //mHolder.setSizeFromLayout(); //should be the default

            /*
             * deprecated setting, but required on Android versions prior to 3.0
             */
            //mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }

        public void deinit() 
        {
            mHolder.removeCallback(this);
        }

        public void surfaceCreated(SurfaceHolder holder) 
        {
            if(BuildConfig.DEBUG) Log.d(TAG, "surfaceCreated");

            /*
             *  the Surface has been created, now tell the camera where to draw 
             *  the preview.
             */
            try {
                if(BuildConfig.DEBUG) Log.d(TAG, "wait for camera client object to be ready");
           	    while(true)
           	    {
       	            if(mCameraClient.isReady())
       	            {
                        if(BuildConfig.DEBUG) Log.d(TAG, "camera client object ready, start preview");
                        mCameraClient.startPreview(holder);
                        break;
       	            }
       	            else
       	            {
   	            	    Thread.sleep(1);
       	            }
           	    }
            } catch (InterruptedException e) {
                if(BuildConfig.DEBUG) Log.d(TAG, "Thread interrupted: " + e.getMessage());
			}
        }

        public void surfaceDestroyed(SurfaceHolder holder) 
        {
            if(BuildConfig.DEBUG) Log.d(TAG, "surfaceDestroyed");
            
            /*
             * we will release the camera as part of CameraClient
             * deinit procedure in onStop
             */
            //mCameraClient.releaseCamera();
        }

        public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) 
        {
            if(BuildConfig.DEBUG) Log.d(TAG, "surfaceChanged: format=" + format + 
           		", width=" + w + ", height=" + h);

//          // If preview can change or rotate, take care of those events here.
//          // Make sure to stop the preview before resizing or reformatting it.
//
//          if (mHolder.getSurface() == null)
//          {
//            // preview surface does not exist
//            return;
//          }
//
//          // stop preview before making changes
//          try {
//              cameraObj.stopPreview();
//          } catch (Exception e){
//            // ignore: tried to stop a non-existent preview
//          }
//
//          // set preview size and make any resize, rotate or
//          // reformatting changes here
//
//          // start preview with new settings
//          try {
//              cameraObj.setPreviewDisplay(mHolder);
//              cameraObj.startPreview();
//
//          } catch (Exception e){
//              if(BuildConfig.DEBUG) Log.d(TAG, "Error starting camera preview: " + e.getMessage());
//          }
        }
    }
    
    /*
     * run camera client in its own thread to avoid any scheduling conflict with
     * UI thread
     */
    private class CameraClient
    {
   	    private Camera mCamera = null;
   	    private boolean mCameraReady = false;
   	    private HandlerThread mCameraHandlerThread = null;
        private Handler mCameraHandler = null;
   	    private int mCameraId = -1;
   	    private int mCameraFacing = -1;
   	    private int mCameraOrientation = -1; //fixed for camera
   	    private int mPhotoRotation = -1;
   	    private SurfaceHolder mSurfaceHolder = null;
        private Camera.Parameters mCameraParams;
        private Camera.Size mCameraPreviewSize;

        private long mLastTimeMs = 0;
        private int mNumOfFrameDelays = 0;
        private float mAvgFrameDelayMs = 0;

	    private long mCaptureStartUs = Long.MAX_VALUE;
        private boolean mFirstFrameCaptured = false;
        private boolean mSecondFrameCaptured = false;
        private int mFirstFrameDiffMs = 0;
        private int mSecondFrameDiffMs = 0;
        private byte[][] mFrameBuf = new byte[2][];
        private int mFrameBufInd = 0;

        private JpegEncoder mEncoder = null;

        public CameraClient(final int desiredFacing,
        		final int desiredWidth, final int desiredHeight)
        {
       	    /*
       	     * onPreviewFrame callback is invoked on the event thread open(int) was
       	     * called from. the thread needs to have its own looper, or otherwise
       	     * the callback will be invoked in the main thread. reference: "http://
       	     * stackoverflow.com/questions/19216893/android-camera-asynctask-with-
       	     * preview-callback/20693740#20693740"
       	     */
       	    if (mCameraHandlerThread == null)
       	    {
       	        mCameraHandlerThread = new HandlerThread("CameraHandlerThread");
       	        mCameraHandlerThread.start();
       	        mCameraHandler = new Handler(mCameraHandlerThread.getLooper());
       	    }
       	    mCameraHandler.post(new Runnable() {
        	        @Override
        	        public void run() 
        	        {
  	            openCamera(desiredFacing, desiredWidth, desiredHeight);
   	            }
        	    });
        }

        public void deinit()
        {
       	    /*
       	     * release camera that's currently in use
       	     */
       	    releaseCamera();
        	    
       	    /*
       	     * quit the handler thread after the release camera command
       	     * above is executed by the handler
       	     */
       	    mCameraHandlerThread.quitSafely();
        }

        public void switchFrontBack(final int newFacing)
        {
            /*
             * release whichever camera that's currently in use
             */
            releaseCamera();
            
            /*
             * open new camera with desired facing. note if this device has only
             * one camera, the call below would reopen the same camera without
             * complaining anything
             */
       	    mCameraHandler.post(new Runnable() {
        	        @Override
        	        public void run() 
        	        {
                openCamera(newFacing, PHOTO_DIMENSION[0], PHOTO_DIMENSION[1]);
       	        }
        	    });

            /*
             * we reuse the surface holder created before...
             */
            startPreview();
        }

        public boolean prepareCapture(long startUs)
        {
	        if(mCaptureStartUs != Long.MAX_VALUE)
	        {
        	    /*
        	     * another capture is in progress, can't start a new one
        	     */
        	    return false;
	        }
	        mCaptureStartUs = startUs;

       	    mCameraHandler.post(new Runnable() {
        	        @Override
        	        public void run() 
        	        {
    	    	            startCapture();
    	            }
        	    });
       	    return true;
        }

   	    public int getCameraFacing()
    	    {
    	    	    return mCameraFacing;
    	    }

   	    @SuppressWarnings("unused")
		public Camera.Parameters getCameraParametersInstance()
    	    {
    	    	    return mCameraParams;
    	    }

        /**
         * safely get an instance of the Camera object. initialize its parameters
         */
        public void openCamera(int desiredFacing, int desiredWidth, int desiredHeight)
        {
       	    if(BuildConfig.DEBUG) Log.d(TAG, "openCamera: facing=" + desiredFacing +
   	    		", " + desiredWidth + "X" + desiredHeight);

            final Camera.CameraInfo info = new Camera.CameraInfo();
            
            /*
             *  Try to find camera with desired facing
             */
            int numCameras = Camera.getNumberOfCameras();
            mCameraId = -1;
            for (int i = 0; i < numCameras; i++) 
            {
                Camera.getCameraInfo(i, info);
                if (info.facing == desiredFacing) 
                {
                    mCameraId = i;
                    break;
                }
            }
            if (mCameraId == -1) 
            {
                if(BuildConfig.DEBUG) Log.d(TAG, "No camera with desired facing found; opening default");
                mCameraId = 0;
            }

            try {
                mCamera = Camera.open(mCameraId);    
            }
            catch (RuntimeException e) {
                if(BuildConfig.DEBUG) Log.e(TAG, "cannot open camera!");
                return;
            }

            mCameraOrientation = info.orientation;
            mCameraFacing = info.facing;

            mCameraParams = mCamera.getParameters();

            /*
             * set preview size
             * TODO: experiment with more models and see if a certain size is good for all,
             * or perhaps this should be specific per device model
             */

            //YCrCb for preview
            mCameraParams.setPreviewFormat(ImageFormat.NV21);
            
            /*
             * if we can find a supported preview size that's the same as our
             * desired size, use it. otherwise, use preferred size native to 
             * the camera. this function will set mCameraParams with the size 
             * chosen
             * TODO: if desired size is not supported, and we use preferred
             * size by this device, we need to sync with the other camera
             */
            CameraUtils.choosePreviewSize(mCameraParams, desiredWidth, desiredHeight);
            mCameraPreviewSize = mCameraParams.getPreviewSize();

            /*
             * we are going to capture preview frame, not takePicture. so set this to 
             * true and hoping it boosts frame rate or make it more stable
             */
            mCameraParams.setRecordingHint(true);

            /*
             * disable all the automatic settings, in the hope that shutter lag will
             * be less variable
             */
            List<String> modes;

            modes = mCameraParams.getSupportedFocusModes();
            if(modes != null)
            {
                for(String mode : modes)
                {
           	        if(mode.contains(Camera.Parameters.FOCUS_MODE_INFINITY))
           	        {
                        mCameraParams.setFocusMode(Camera.Parameters.FOCUS_MODE_INFINITY);
                        break;
           	        }
                }
            }
    
            modes = mCameraParams.getSupportedFlashModes();
            if(modes != null)
            {
                for(String mode : modes)
                {
           	        if(mode.contains(Camera.Parameters.FLASH_MODE_OFF))
           	        {
                        mCameraParams.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                        break;
           	        }
                }
            }

/*
            modes = mCameraParams.getSupportedWhiteBalance();
            if(modes != null)
            {
                for(String mode : modes)
                {
           	        if(mode.contains(Camera.Parameters.WHITE_BALANCE_FLUORESCENT))
           	        {
                        mCameraParams.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_FLUORESCENT);
                        break;
           	        }
                }
            }

            modes = mCameraParams.getSupportedSceneModes();
            if(modes != null)
            {
                for(String mode : modes)
                {
           	        if(mode.contains(Camera.Parameters.SCENE_MODE_PORTRAIT))
           	        {
                        mCameraParams.setSceneMode(Camera.Parameters.SCENE_MODE_PORTRAIT);
                        break;
           	        }
                }
            }
*/

       	    // check that metering areas are supported
//          if (mCameraParams.getMaxNumMeteringAreas() > 0)
//          { 
//              List<Camera.Area> meteringAreas = new ArrayList<Camera.Area>();
//  
//              Rect areaRect1 = new Rect(-100, -100, 100, 100);    // specify an area in center of image
//              meteringAreas.add(new Camera.Area(areaRect1, 600)); // set weight to 60%
//              mCameraParams.setMeteringAreas(meteringAreas);
//          }
            
            // attempt to fix the frame rate. this is not a valid range as returned by 
            // getPreviewFpsRange. 
            //mCameraParams.setPreviewFpsRange(30000, 30000);

            /*
             * zoom can impact view angle. we should set it to 0 if it's not
             */
            if(mCameraParams.isZoomSupported())
            {
                int zoom = mCameraParams.getZoom();
                if(zoom != 0)
                {
                    if(BuildConfig.DEBUG) Log.i(TAG, "getViewAngle: camera zoom = " + zoom +
                  		", forcing to zero");
                    mCameraParams.setZoom(0);
                }
            }

            /*
             * commit camera parameters
             */
            mCamera.setParameters(mCameraParams);

            /*
             * pre-allocate buffer for the capture. avoid auto-allocating by the system,
             * as that will trigger GC and screw up our timing
             */
            int bitsPerPixel = ImageFormat.getBitsPerPixel(mCameraParams.getPreviewFormat());
            int bufSize = mCameraPreviewSize.width*mCameraPreviewSize.height*bitsPerPixel/8;
            
            /*
             * if we haven't allocated frame buffer, or existing buffers are
             * a different preview size, then allocate new ones. otherwise, 
             * skip and use existing
             */
            if((mFrameBuf[0] == null) || (mFrameBuf[1] == null) || 
           	   (mFrameBuf[0].length != bufSize) || (mFrameBuf[1].length != bufSize))
            {
                mFrameBuf[0] = new byte[bufSize];
                mFrameBuf[1] = new byte[bufSize];
            }
            if(BuildConfig.DEBUG) Log.d(TAG, "allocating 2 preview frame buffer with size=" + bufSize);

            // print some debugging information
//          if(BuildConfig.DEBUG) Log.d(TAG, "openCamera: autibanding mode=" + mCameraParams.getAntibanding());
//          if(BuildConfig.DEBUG) Log.d(TAG, "openCamera: colorEffect mode=" + mCameraParams.getColorEffect());
//          if(BuildConfig.DEBUG) Log.d(TAG, "openCamera: focal length=" + mCameraParams.getFocalLength());
//          if(BuildConfig.DEBUG) Log.d(TAG, "openCamera: min exposure comp=" + mCameraParams.getMinExposureCompensation());
//          if(BuildConfig.DEBUG) Log.d(TAG, "openCamera: max exposure comp=" + mCameraParams.getMaxExposureCompensation());
//
//          List<int[]> ranges = mCameraParams.getSupportedPreviewFpsRange();
//          int j=0;
//          for(int[] i : ranges)
//          {
//         	    if(BuildConfig.DEBUG) Log.d(TAG, "openCamera: supported preview FPS range " + j + "=(" +
//                  i[0] + ", " + i[1] + ")");
//         	    j++;
//          }
//          int[] currRange = new int[2];
//          mCameraParams.getPreviewFpsRange(currRange);
//     	    if(BuildConfig.DEBUG) Log.d(TAG, "openCamera: current preview FPS range " + "=(" +
//              currRange[0] + ", " + currRange[1] + ")");
//
//          List<Camera.Size> sizes = mCameraParams.getSupportedPreviewSizes();
//          for(Camera.Size i : sizes)
//          {
//         	    if(BuildConfig.DEBUG) Log.d(TAG, "openCamera: supported preview size (" +
//                  i.width + ", " + i.height + ")");
//          }
            
            if(mNaturalOrientation == Configuration.ORIENTATION_PORTRAIT)
            {
               if(((info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) && (mLandscapeOrientation == mCameraOrientation)) ||
                  ((info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) && (mLandscapeOrientation != mCameraOrientation)))
               {
                   mCamera.setDisplayOrientation(180);
                   mCameraOrientation = (mCameraOrientation+180)%360;
                   if(BuildConfig.DEBUG) Log.w(TAG, "openCamera: flip preview 180 degrees because camera " +
                   		"mounted anti-landscape");
               }
            }

            if(mOrientationEventListener == null)
            {
                mOrientationEventListener = new OrientationEventListener(getActivity(), 
            		    SensorManager.SENSOR_DELAY_NORMAL) 
                {
                    @Override
                    public void onOrientationChanged(int deviceOrientation) 
                    {
                        if (deviceOrientation == ORIENTATION_UNKNOWN) return;
                        
                        handleOrientationChanged(deviceOrientation);
                    }
                };

                if (mOrientationEventListener.canDetectOrientation())
                {
                    mOrientationEventListener.enable();
                }
            }
    
            Runnable forceOrientationCalcRunnable = new Runnable() {
       	        public void run()
       	        {
                    int deviceOrientation = mCurrDeviceOrientation;
                    mCurrDeviceOrientation = -1;
                    handleOrientationChanged(deviceOrientation);
       	        }
            };
            runOnUiThread(forceOrientationCalcRunnable);

            mCameraReady = true;
        }

        public void handleOrientationChanged(int deviceOrientation)
        {
            /*
             * device orientation can be anything between 0-359. we round this to 
             * one of 0, 90, 180, 270, and if it is different form before, we will
             * let GLSurfaceView renderer know
             */
            int roundedOrientation = ((deviceOrientation + 45) / 90 * 90)%360;
            
            /*
             * show a level guide to help user position device
             */
            if(deviceOrientation > 315)
        	        showDeviationFromLevel(deviceOrientation - roundedOrientation - 360);
            else
        	        showDeviationFromLevel(deviceOrientation - roundedOrientation);
       
            if(roundedOrientation != mCurrDeviceOrientation)
            {
                adjustUIControls((360 + mLandscapeOrientation - roundedOrientation)%360);

                mCurrDeviceOrientation = roundedOrientation;

                if(mNaturalOrientation == Configuration.ORIENTATION_PORTRAIT)
                {
                    if (mCameraFacing == Camera.CameraInfo.CAMERA_FACING_BACK) 
                    {
           		        mPhotoRotation = (mCameraOrientation + roundedOrientation)%360;
                    }
                    else
                    {
           		        mPhotoRotation = (360 + mCameraOrientation - roundedOrientation)%360;
                    }
                }
                else
                {
                    if (mCameraFacing == Camera.CameraInfo.CAMERA_FACING_BACK) 
                    {
           		        mPhotoRotation = roundedOrientation;
                    }
                    else
                    {
           		        mPhotoRotation = (360-roundedOrientation)%360;
                    }
                }
                if(BuildConfig.DEBUG) Log.d(TAG, "handleOrientationChange: new photo rotation = " + mPhotoRotation);
            }
        }

        public boolean isReady()
        {
        	    return mCameraReady;
        }

        public void startPreview(SurfaceHolder holder)
        {
       	    mSurfaceHolder = holder;
        	    
       	    startPreview();
        }

        private void startPreview()
        {
       	    if(BuildConfig.DEBUG) Log.d(TAG, "startPreview");
       	    mCameraHandler.post(new Runnable() {
       	        @Override
       	        public void run()
       	        {
       	            if(mSurfaceHolder != null)
       	            {
                       try {
                       	    if(mCamera != null)
                       	    {
			                mCamera.setPreviewDisplay(mSurfaceHolder);
                               mCamera.startPreview();
                       	    }
			            } catch (IOException e) {
				            e.printStackTrace();
			            }

                        adjustAspectRatio(
               		        (float)mCameraPreviewSize.width/(float)mCameraPreviewSize.height);
       	            }
       	        }
       	    });
        }

        @SuppressWarnings("unused")
		public Camera.Size getPreviewSize()
        {
        	    return mCameraPreviewSize;
        }

        public void releaseCamera()
        {
            if (mCamera != null) 
            {
                mCamera.stopPreview();
                mCamera.release();
                
                /*
                 * invalidate all camera instance variables, except for 
                 * mCameraHandler and mCameraSurfaceTexture, because we 
                 * can still use them...
                 */
                mCamera = null;
                mCameraReady = false;
                mCameraId = -1;
                mCameraFacing = -1;
                mCameraOrientation = -1; //fixed for camera
       
                CameraUtils.clearCamParams();

                if(BuildConfig.DEBUG) Log.d(TAG, "releaseCamera -- done");
            }
        }
        
        @SuppressWarnings("unused")
		public void hidePreview()
        {
            Runnable hidePreviewRunnable = new Runnable() {
       	        public void run()
       	        {
                    mPreview.setVisibility(View.INVISIBLE);
       	        }
            };
            runOnUiThread(hidePreviewRunnable);
        }

        @SuppressWarnings("unused")
		public void showPreview()
        {
            Runnable showPreviewRunnable = new Runnable() {
       	        public void run()
       	        {
                    mPreview.setVisibility(View.VISIBLE);
       	        }
            };
            runOnUiThread(showPreviewRunnable);
        }

        private void startCapture()
        {
            int captureWaitMs = (int)((mCaptureStartUs - SystemClock.elapsedRealtimeNanos()/1000)/1000);

            if(captureWaitMs < 0)
            {
                if(BuildConfig.DEBUG) Log.e(TAG, "prepareCapture: cannot wait, starting time already past, " +
               		"waitMs = " + captureWaitMs + "ms");
            }
            else
            {
                if(BuildConfig.DEBUG) Log.d(TAG, "prepareCapture: capture wait " + captureWaitMs + "ms");
            }

		    /*
		     * attempt to lock exposure and white balance in order to boost frame rate.
		     * it is found to have negative impact on exposure (for example, on GS6
		     * this makes the frames very dim). so disabled
    	     */
   	        //setWBAELock(true);
    	        
   	        /*
   	         * attach buffer
   	         */
            mCamera.addCallbackBuffer(mFrameBuf[0]);

   	        /*
   	         * start taking preview callbacks.
   	         */
            mCamera.setPreviewCallbackWithBuffer(mPreviewCB);

            return;
	    }

        private void endCapture()
        {
            mCaptureStartUs = Long.MAX_VALUE;
    
            mLastTimeMs = 0;
            mNumOfFrameDelays = 0;
            mAvgFrameDelayMs = 0;
    
            mFirstFrameCaptured = false;
            mSecondFrameCaptured = false;
            mFirstFrameDiffMs = 0;
            mSecondFrameDiffMs = 0;

            mImgInd++;

		    /*
		     * unlock exposure and white balance. disabled.
    	     */
   	        //setWBAELock(false);
    	        
   	        /*
   	         * stop taking preview callbacks. this will also clear the buffer
   	         * queue
   	         */
            mCamera.setPreviewCallbackWithBuffer(null);
        }

        /**
         * lock camera auto exposure and white balance prior to capture,
         * unlock them after capture
         */
        @SuppressWarnings("unused")
		public void setWBAELock(boolean enable) 
        {
	        /*
	         * debug:
	         */
            if(enable)
            {
	            /*
	             * set exposure compensation to the min. can this boost frame rate?
	             */
	            mCameraParams.setExposureCompensation(mCameraParams.getMinExposureCompensation());
            }
            else
            {
	            /*
	             * set exposure compensation to 0 - no adjustment
	             */
	            mCameraParams.setExposureCompensation(0);
            }
    
            /*
             *  check if lock/unlock white balance and auto exposure supported
             */
            boolean aeSupported = mCameraParams.isAutoExposureLockSupported();
            boolean wbSupported = mCameraParams.isAutoWhiteBalanceLockSupported();
        
            if(aeSupported)
            {
                mCameraParams.setAutoExposureLock(enable);
            }
        
            if(wbSupported)
            {
                mCameraParams.setAutoWhiteBalanceLock(enable);
            }
        
            mCamera.setParameters(mCameraParams);
            
            if(aeSupported && mCameraParams.getAutoExposureLock())
            {
                if(BuildConfig.DEBUG) Log.d(TAG, "Auto Exposure locked");
            }
            else
            {
                if(BuildConfig.DEBUG) Log.d(TAG, "Auto Exposure unlocked");
            }
    
            if(wbSupported && mCameraParams.getAutoWhiteBalanceLock())
            {
                if(BuildConfig.DEBUG) Log.d(TAG, "White Balance locked");
            }
            else
            {
                if(BuildConfig.DEBUG) Log.d(TAG, "White Balance unlocked");
            }
        }

        /*
         * cf. "http://stackoverflow.com/questions/2364892/how-to-play-native-
         * camera-sound-on-android", verified camera_click.ogg exists on GS4/6/7,
         * Huawei, Xiaomi, Oppo, etc
         */
        public void playShutterSound()
        {
            int maxVolume = AudioConfigure.getMaxAudioVolume();

            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            int volume = am.getStreamVolume( AudioManager.STREAM_MUSIC);

            /*
             * clamp shutter volume to half of max (more than that it could be annoying)
             */
            if(volume > maxVolume/2)
            {
           	    volume = maxVolume/2;
            }
            AudioConfigure.setAudioVolume(volume);

            if (mShutterSoundPlayer != null)
            {
                mShutterSoundPlayer.start();
            }
        }

        private PreviewCallback mPreviewCB = new PreviewCallback() 
        {
            @Override
            public void onPreviewFrame (byte[] data, Camera camera)
            {
    	        long currTimeUs = SystemClock.elapsedRealtimeNanos()/1000;
                long currTimeMs = currTimeUs/1000;

                if(mLastTimeMs != 0)
                {
                    mNumOfFrameDelays++;

                    int frameDelayMs = (int)(currTimeMs - mLastTimeMs);
                    mAvgFrameDelayMs = (mAvgFrameDelayMs * (float)(mNumOfFrameDelays-1) 
                   		+ (float)frameDelayMs)/(float)mNumOfFrameDelays;

                    if(BuildConfig.DEBUG) Log.d(TAG, "preview frame delay " + frameDelayMs + "ms" +
                        ", new avg = " + mAvgFrameDelayMs);
                	
                }
                mLastTimeMs = currTimeMs;

                /*
                 * we are going to capture two consecutive frames, encode them to
                 * jpeg, include request ref code in the file name, and append 
                 * capture time info to the file name:
                 * 
                 * mXX - for the frame that's immediately before mCaptureStartUs,
                 *       XX ms prior
                 *       
                 * pYY - for the frame that's immediately after mCaptureStartUs,
                 *       YY ms after
                 *       
                 * capture time will be exchanged by the cloud so we know which one 
                 * is the better one to use
                 */
    	        int diffMs = (int)((currTimeUs - mCaptureStartUs)/1000);
  	            if((diffMs < 0) && (diffMs >= -mAvgFrameDelayMs))
   	            {
    	            if(!mFirstFrameCaptured)
    	            {
  	        	        /*
   	        	         * this is the frame immediately prior to mCaptureStartUs
   	        	         */
                        if(BuildConfig.DEBUG) Log.d(TAG, "Preview callback: 1st frame diff=" + diffMs);

                        mFirstFrameCaptured = true;
                        mFirstFrameDiffMs = diffMs;

                        if(mIsLeft)
                        {
                            playShutterSound();
                        }
    	            }
    	            else
    	            {
   	            	    /*
   	            	     * warning - most likely due to jitter in frame delay
   	            	     */
                        if(BuildConfig.DEBUG) Log.d(TAG, "Preview callback: 2nd frame still ahead of capture time, " +
               		       "treat it as 1st frame" +
               		       "diff=" + diffMs);

           		        /*
           		         * treat it as 1st frame
           		         */
                        mFirstFrameDiffMs = diffMs;
    	            }
    	        }
    	        else if(diffMs >= 0)
    	        {
    	            if(mFirstFrameCaptured)
    	            {
  	        	        /*
  	        	         * we capture the 2nd frame.
   	        	         */
                        if(BuildConfig.DEBUG) Log.d(TAG, "Preview callback: 2nd frame diff=" + diffMs);

                        mSecondFrameCaptured = true;
                        mSecondFrameDiffMs = diffMs;
    	            }
    	            else
    	            {
   	            	    /*
   	            	     * warning - most likely due to jitter in frame delay
   	            	     */
                        if(BuildConfig.DEBUG) Log.w(TAG, "Preview callback: 1st frame already after capture time, " +
               		       "diff=" + diffMs);

                        mFirstFrameCaptured = true;
                        mFirstFrameDiffMs = diffMs;

                        if(mIsLeft)
                        {
                            playShutterSound();
                        }
    	            }
    	        }

           	    /*
           	     * start encoding both frames to jpeg. we feed only one buffer at a time.
           	     * up to this point, mFrameBuf[mFrameBufInd] is the same as "data" passed
           	     * to this call. and mFrameBufInd^1 is used in previous frame
           	     */
   	            if(mFirstFrameCaptured && mSecondFrameCaptured)
   	            {
                    /*
                     * encode the first frame 
                     */
                    mEncoder = new JpegEncoder(mPhotoRotation, 
                   		mFrameBuf[mFrameBufInd^1], mFrameBuf[mFrameBufInd],
                   		mFirstFrameDiffMs, mSecondFrameDiffMs,
                   		mCameraPreviewSize.width, mCameraPreviewSize.height,
                   		mImgInd, mIsLeft);

   	                mCaptureInfoSelf.put(mImgInd,
                		new CaptureInfo(new int[]{mFirstFrameDiffMs, mSecondFrameDiffMs},
       		                mEncoder));

                    sendCapTime(mFirstFrameDiffMs, mSecondFrameDiffMs);
                        
                    /*
                     * reset camera state and get ready for next capture
                     */
                    endCapture();

                    /*
                     * hide the preview temporarily, and give a "captured" 
                     * impression to user. preview will be restarted short time 
                     * later 
                     */
                    //mCamera.stopPreview();
                    //hidePreview();
   
                    /*
                     * stop the HandlerThread
                     */
                    //if(BuildConfig.DEBUG) Log.d(TAG, "PreviewCallback: done. quitting CameraHandlerThread");
                    //mCameraHandlerThread.getLooper().quit();
   	            }
   	            else
   	            {
                    mFrameBufInd ^= 1;
                    camera.addCallbackBuffer(mFrameBuf[mFrameBufInd]);
   	            }
            }
        };
        
        /*
         * not used, as using takePicture for full-sized capture involves too much 
         * jitter, making tight synchronization impossible. this can certainly be
         * improved with deeper access to native APIs and help from OEMs. 
         */
        @SuppressWarnings("unused")
		private ShutterCallback mShutterCB = new ShutterCallback() 
        {
    
            @Override
            public void onShutter() 
            {
                long diff = SystemClock.elapsedRealtimeNanos()/1000 - mCaptureStartUs;
                if(BuildConfig.DEBUG) Log.d(TAG, "ShutterCallback, shutter lag " + diff/1000 + "ms");
            }
        };
    
        /*
         * not used - using takePicture for full-sized capture involves too much jitter, 
         * making tight synchronization impossible
         */
        @SuppressWarnings("unused")
		private PictureCallback mPictureCB = new PictureCallback() 
        {
    
            @Override
            public void onPictureTaken(byte[] data, Camera camera) 
            {
                File pictureFile = getOutputMediaFile(FileColumns.MEDIA_TYPE_IMAGE);
                if (pictureFile == null)
                {
                    if(BuildConfig.DEBUG) Log.d(TAG, "Error creating media file, check storage permissions: "); 
                    return;
                }
    
                try {
                    FileOutputStream fos = new FileOutputStream(pictureFile);
                    fos.write(data);
                    fos.close();
                } catch (FileNotFoundException e) {
                    if(BuildConfig.DEBUG) Log.d(TAG, "File not found: " + e.getMessage());
                } catch (IOException e) {
                    if(BuildConfig.DEBUG) Log.d(TAG, "Error accessing file: " + e.getMessage());
                }
            }
        };
    

        /** 
         * Create a File for saving an image or video 
         */
        private File getOutputMediaFile(int type)
        {
            /*
             * create a media file name
             */
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            File mediaFile;
            if (type == MEDIA_TYPE_IMAGE)
            {
                mediaFile = new File(MainConsts.MEDIA_3D_RAW_PATH + 
                "IMG_"+ timeStamp + ".jpg");
            }
            else if(type == MEDIA_TYPE_VIDEO)
            {
                mediaFile = new File(MainConsts.MEDIA_3D_RAW_PATH + 
                "MPG_"+ timeStamp + ".mp4");
            }
            else
            {
                return null;
            }
    
            return mediaFile;
        }
    }
    
    /**
     * encodes YUV data to JPEG. runs on own thread so multiple frames can be encoded
     * simultaneously on separate cores (if available)
     */
    private class JpegEncoder extends Thread
    {
   	    private byte[] yuvData;
   	    private byte[] yuvData1st;
   	    private byte[] yuvData2nd;
   	    private int width, height;
   	    private String jpegFilePath = null;
   	    private String jpegFilePath1st = null;
   	    private String jpegFilePath2nd = null;
   	    private String jpegFilename = null;
   	    private String jpegFilename1st = null;
   	    private String jpegFilename2nd = null;
   	    private boolean isLeft = true;
   	    private boolean isRunning = true;
   	    private int rotation;

   	    public JpegEncoder(int r, byte[] frame1st, byte[] frame2nd,
    		int diff1st, int diff2nd,
    		int w, int h, int ind, boolean ii)
   	    {
    	    setName("JpegEncoderThread");
    	    	    
    	    rotation = r;
    	    width = w;
    	    height = h;
    	    isLeft = ii;

    	    jpegFilename1st = "IMG_" + ((isLeft)?"left_":"right_")
	           + AudioContext.getInstance().getHexRefCode()
	           + ((diff1st>0)?("_p" + Integer.toString(diff1st)):
	        	               ("_m" + Integer.toString(-diff1st)))
        	   + "_" + Integer.toString(ind)
        	   + ".jpg";

    	    jpegFilename2nd = "IMG_" + ((isLeft)?"left_":"right_")
	           + AudioContext.getInstance().getHexRefCode()
	           + ((diff2nd>0)?("_p" + Integer.toString(diff2nd)):
	        	               ("_m" + Integer.toString(-diff2nd)))
        	   + "_" + Integer.toString(ind)
        	   + ".jpg";

    	    jpegFilePath1st = MainConsts.MEDIA_3D_RAW_PATH + jpegFilename1st;
    	    jpegFilePath2nd = MainConsts.MEDIA_3D_RAW_PATH + jpegFilename2nd;

    	    yuvData1st = frame1st.clone();
    	    yuvData2nd = frame2nd.clone();
  	    }

   	    public void setFrameNum(int frameNum)
   	    {
    	    if(frameNum == 0)
    	    {
   	    	    yuvData = yuvData1st;
   	    	    jpegFilename = jpegFilename1st;
   	    	    jpegFilePath = jpegFilePath1st;
    	    }
    	    else
    	    {
   	    	    yuvData = yuvData2nd;
   	    	    jpegFilename = jpegFilename2nd;
   	    	    jpegFilePath = jpegFilePath2nd;
    	    }

   	    }

   	    public String getFilePath()
    	    {
    	    	    return jpegFilePath;
    	    }

   	    public boolean isEncoding()
    	    {
    	    	    return isRunning;
    	    }

   	    @Override
   	    public void run()
   	    {
    	    //if(BuildConfig.DEBUG) Log.d(TAG, "JpegEncoder.run() start");
    	    FileOutputStream fos;
            Bitmap bmp = null;
            Bitmap rotatedBitmap = null;

			try {
				fos = new FileOutputStream(jpegFilePath);

				/*
				 * first convert captured frame into RGB array from YUV NV21
				 * format. we had to use NV21 as it is supported by all camera
				 * and is the recommended format. but we need to convert 
				 * because we need to rotate. tried directly rotate NV21 image
				 * but for some reason didn't work
				 */
			    int[] argb = new int[width*height];

    	        //if(BuildConfig.DEBUG) Log.d(TAG, "JpegEncoder.run: YUV to RGB conversion start, " +
    	            //"width=" + width + ", height=" + height);
                YUV_NV21_TO_RGB(argb, yuvData, width, height);
    	        //if(BuildConfig.DEBUG) Log.d(TAG, "JpegEncoder.run: YUV to RGB conversion end");

                /*
                 * create bitmap object from RGB array
                 */
                bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                bmp.setPixels(argb, 0/* offset */, width /* stride */, 0, 0, width, height);

                /*
                 * rotate the bitmap, and save it as jpeg
                 */
                //if(BuildConfig.DEBUG) Log.d(TAG, "rotating image by " + rotation + " dgrees");
                final Matrix matrix = new Matrix();
                matrix.setRotate(rotation);
                rotatedBitmap = Bitmap.createBitmap(bmp, 0, 0, width, height, matrix, false);

    	        //if(BuildConfig.DEBUG) Log.d(TAG, "JpegEncoder.run: jpeg compress start");
                rotatedBitmap.compress(CompressFormat.JPEG, 100, fos);
    	        //if(BuildConfig.DEBUG) Log.d(TAG, "JpegEncoder.run: jpeg compress end");

				fos.close(); 
				if(BuildConfig.DEBUG) Log.d(TAG, "rotating image done");
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
        	    	        
            /*
             * mark previous bitmap for GC (shouldn't be necessary, just to be safe)
             */
			if(bmp != null)
			{
                bmp.recycle();
			}

			if(rotatedBitmap != null)
			{
                rotatedBitmap.recycle();
			}

			/*
			 * encoding is done
			 */
			isRunning = false;
    	    if(BuildConfig.DEBUG) Log.d(TAG, "JpegEncoder.run: encoding of " + jpegFilename + " done");
  	    }

        /*
         * original code from "http://stackoverflow.com/questions/12469730/
         * confusion-on-yuv-nv21-conversion-to-rgb"
         */
        public void YUV_NV21_TO_RGB(int[] argb, byte[] yuv, int width, int height) 
        {
            final int frameSize = width * height;

            final int ii = 0;
            final int ij = 0;
            final int di = +1;
            final int dj = +1;

            int a = 0;
            for (int i = 0, ci = ii; i < height; ++i, ci += di) 
            {
                for (int j = 0, cj = ij; j < width; ++j, cj += dj) {
                    int y = (0xff & ((int) yuv[ci * width + cj]));
                    int v = (0xff & ((int) yuv[frameSize + (ci >> 1) * width + (cj & ~1) + 0]));
                    int u = (0xff & ((int) yuv[frameSize + (ci >> 1) * width + (cj & ~1) + 1]));
                    y = y < 16 ? 16 : y;

                    int r = (int) (1.164f * (y - 16) + 1.596f * (v - 128));
                    int g = (int) (1.164f * (y - 16) - 0.813f * (v - 128) - 0.391f * (u - 128));
                    int b = (int) (1.164f * (y - 16) + 2.018f * (u - 128));

                    r = r < 0 ? 0 : (r > 255 ? 255 : r);
                    g = g < 0 ? 0 : (g > 255 ? 255 : g);
                    b = b < 0 ? 0 : (b > 255 ? 255 : b);

                    argb[a++] = 0xff000000 | (r << 16) | (g << 8) | b;
                }
            }
        }

        /*
         * original code from "http://stackoverflow.com/questions/23107057/
         * rotate-yuv420-nv21-image-in-android". doesn't seem to be working
         */
        @SuppressWarnings("unused")
		public void rotateNV21(byte[] input, byte[] output)
        {
            boolean swap = (rotation == 90 || rotation == 270);
            boolean yflip = (rotation == 90 || rotation == 180);
            boolean xflip = (rotation == 270 || rotation == 180);
            for (int x = 0; x < width; x++) 
            {
                for (int y = 0; y < height; y++) 
                {
                    int xo = x, yo = y;
                    int w = width, h = height;
                    int xi = xo, yi = yo;
                    if (swap) 
                    {
                        xi = w * yo / h;
                        yi = h * xo / w;
                    }
                    if (yflip) 
                    {
                        yi = h - yi - 1;
                    }
                    if (xflip) 
                    {
                        xi = w - xi - 1;
                    }
                    output[w * yo + xo] = input[w * yi + xi];
                    int fs = w * h;
                    //int qs = (fs >> 2);
                    xi = (xi >> 1);
                    yi = (yi >> 1);
                    xo = (xo >> 1);
                    yo = (yo >> 1);
                    w = (w >> 1);
                    h = (h >> 1);
                    // adjust for interleave here
                    int ui = fs + (w * yi + xi) * 2;
                    int uo = fs + (w * yo + xo) * 2;
                    // and here
                    int vi = ui + 1;
                    int vo = uo + 1;
                    output[uo] = input[ui]; 
                    output[vo] = input[vi]; 
                }
            }
        }   
    }

    private class CaptureInfo
    {
   	    private int[] captureTime = null;
   	    private int chosenInd = -1;
   	    private JpegEncoder encodingThread = null;

        public CaptureInfo(int[] ct)
        {
        		captureTime = ct;
        }

        public CaptureInfo(int[] ct, JpegEncoder je)
        {
       		captureTime = ct;
       		encodingThread = je;
        }

        public int[] getCaptureTime()
        {
        	    return captureTime;
        }

        public String getCapturePath()
        {
       	    if(encodingThread != null)
       	    {
       	        return encodingThread.getFilePath();
       	    }
       	    else
       	    {
   	    	    if(BuildConfig.DEBUG) Log.e(TAG, "CaptureInfo: encodingThread is null");
   	    	    return null;
       	    }
        }

        public void setChosenIndex(int ind)
        {
       	    if(chosenInd != -1)
       	    {
   	    	    /*
   	    	     * we could get here if the calls to processCapTime by
   	    	     * sendCapTime/receiveCapTime get interleaved.
   	    	     */
   	    	    if(BuildConfig.DEBUG) Log.w(TAG, "setChosenIndex: frame index already chosen, " +
    	    		"this shouldn't happen. ignore");
       	    }
       	    else
       	    {
   	    	    /*
   	    	     * start encoding the chosen frame
   	    	     */
   	    	    chosenInd = ind;
       	        encodingThread.setFrameNum(ind);
       	        encodingThread.start();
       	    }
        }

        public boolean isEncoding()
        {
       	    if(encodingThread != null)
       	    {
       	        return encodingThread.isEncoding();
       	    }
       	    else
       	    {
   	    	    return false;
       	    }
        }
    }
}