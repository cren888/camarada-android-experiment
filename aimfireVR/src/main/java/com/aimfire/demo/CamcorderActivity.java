/*
 * CamcorderActivity: code modified based on Grafika example
 * 
 * Copyright 2013 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.aimfire.demo;

import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.opengl.EGL14;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.MotionEventCompat;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.view.View.OnTouchListener;
import android.view.animation.LinearInterpolator;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.ActionBar;
import android.app.Activity;
import android.support.v7.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.SensorManager;

import com.aimfire.camarada.BuildConfig;
import com.aimfire.camarada.R;
import com.aimfire.audio.AudioConfigure;
import com.aimfire.constants.ActivityCode;
import com.aimfire.main.MainConsts;
import com.aimfire.layout.AspectFrameLayout;
import com.aimfire.gallery.cardboard.MovieActivity;
import com.aimfire.gallery.MediaScanner;
import com.aimfire.gallery.service.MovieProcessor;
import com.aimfire.grafika.CameraUtils;
import com.aimfire.grafika.TextureMovieEncoder;
import com.aimfire.grafika.gles.FullFrameRect;
import com.aimfire.grafika.gles.Texture2dProgram;
import com.aimfire.service.AimfireService;
import com.aimfire.service.AimfireServiceConn;
import com.aimfire.utilities.CircularProgressDrawable;
import com.aimfire.utilities.CustomToast;
import com.aimfire.utilities.ZipUtil;
import com.aimfire.wifidirect.WifiDirectScanner;
import com.aimfire.hintcase.*;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crash.FirebaseCrash;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import uk.co.samuelwall.materialtaptargetprompt.MaterialTapTargetPrompt;

@SuppressWarnings("deprecation")
public class CamcorderActivity extends Activity
        implements SurfaceTexture.OnFrameAvailableListener, OnItemSelectedListener 
{
    private static final String TAG = "CamcorderActivity";
    private static final boolean VERBOSE = false;

    private static final int CAMERA_MODE_IMAGE = 0;
    private static final int CAMERA_MODE_VIDEO = 1;

   	private static final String [] CAMERA_RECORDING_START_SOUND = new String[]{
   		"system/media/audio/ui/Cam_Start.ogg",
        "/system/media/audio/ui/VideoRecord.ogg"
   	};

   	private static final String [] CAMERA_RECORDING_STOP_SOUND = new String[]{
   		"system/media/audio/ui/Cam_Stop.ogg",
        "/system/media/audio/ui/VideoStop.ogg", //Samsung
        "/system/media/audio/ui/VideoRecordEnd.ogg", //Huawei
        "/system/media/audio/ui/VideoRecord.ogg" //Oppo, Xiaomi
   	};

    /*
     * max p2p latency sending command to remote device. we want
     * to set a reasonable value for it. too conservative (i.e.
     * too big) means shutter delay; too aggressive (too short)
     * means sometimes commands arrive after the action time.
     * for video (as opposed to image) we can afford to set this
     * to a high/safe number
     */
    private static final long P2P_LATENCY_US = 200*1000;

    //private static final int VIDEO_QUALITY = MainConsts.VIDEO_QUALITY_SD_HQ;
    //private static final int VIDEO_QUALITY = MainConsts.VIDEO_QUALITY_720P;
    //private static final int VIDEO_QUALITY = MainConsts.VIDEO_QUALITY_1080P;

    private static final int CONNECT_ANIM_CYCLES = 3;
    private static final int CONNECT_ANIM_CYCLE_LENGTH_SECONDS = 20;

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
     * camera instance variables. facing - we launch back facing by default 
     */
    private Camera mCamera = null;
    private CameraHandler mCameraHandler = null;
    private SurfaceTexture mCameraSurfaceTexture = null;
    private int mCameraId = -1;
    private int mCameraFacing = -1;
    private int mCameraOrientation = -1; //fixed for camera
    private int mSupportedVideoQualities;
    private int mQualityPref;
    private String mPosPref;
    private Camera.Parameters mCameraParams;

    /*
     * preview and recording
     */
    private GLSurfaceView mGLView;
    private CameraSurfaceRenderer mRenderer;
    private boolean mRecordingEnabled = false;      
    private TextureMovieEncoder mMovieEncoder = new TextureMovieEncoder(this);

    /*
     * path prefix for sync and solo modes
     */
    private String mMpegPrefixSync;
    private String mMpegPrefixSolo;

    private String mMpegFilepath;
    private int mMpegIndex = -1;

    /*
     * synchronization variables
     */
	private long mSyncTimeUs = Long.MAX_VALUE;

    private boolean mIsInitiator;
    private boolean mIsLeft;
    private boolean mPvSwitching = false;

	/*
	 * handling device orientation changes
	 */
    private int mNaturalOrientation; //fixed for device
    private int mLandscapeOrientation; //fixed for device
    private int mCurrDeviceOrientation = -1;
    private OrientationEventListener mOrientationEventListener = null;

    /*
     * need to get service instance to send recorded media files and release 
     * audio resource when done
     */
    private AimfireServiceConn mAimfireServiceConn;
    private AimfireService mAimfireService;

    /*
     * pending movie processing tasks
     */
    private ArrayList<ProcessFileTask> mPendingTasks = new ArrayList<ProcessFileTask>();

    /*
     * state of the connection
     */
    private boolean mInSync = false;//whether we are in sync
    private boolean mP2pAttempted = false;//whether connection with another device has been attempted
    private boolean mP2pConnected = false;//whether p2p connection is established (may not be in sync yet)

    /*
     * name of the cvr file current thumb corresponds to
     */
	private String mCurrThumbLink = null;

	/*
	 * state of remote camera
	 */
	private boolean mRemoteCameraPaused = false;
	private boolean mLocalCameraPaused = false;

    /*
     * flag indicates switching to Camera
     */
    private boolean mSwitchPhotoVideo = false;


    /*
     * local and remote camera parameters
     */
    private boolean mCamParamsSent = false;

    /*
     * attempt to detect whether two devices are stacked. set but not used
     */
    private boolean mStacked = false;
    private final Handler mLongPressHandler = new Handler();

    /*
     * shutter sound player
     */
    private MediaPlayer mCamStartSoundPlayer = null;
    private MediaPlayer mCamStopSoundPlayer = null;

    /*
     * UI elements
     */
    private View mParentView;
    private ImageButton mView3DButton;
    private ImageButton mExitButton;
	private ImageButton mPvButton;
	private ImageButton mFbButton;
    private Button mLevelButton;
	private LinearLayout mTimeCounter;

    private FrameLayout mShutterLayout;
    private ImageButton mCaptureButton;
    private ImageView mProgView;
    private CircularProgressDrawable mProgDrawable;
    private Animator mCurrAnimator;

    private ImageView mScanProgView;
    private CircularProgressDrawable mScanProgDrawable;
    private ImageButton mScanModeButton;
    private Animator mScanCurrAnimator;
    private int mAnimCyclesCnt;

    /**
     * BroadcastReceiver for incoming AimfireService messages.
     */
    private BroadcastReceiver mAimfireServiceMsgReceiver = new BroadcastReceiver() 
    {
        @Override
        public void onReceive(Context context, Intent intent) 
        {
       	    long startUs, stopUs;
            boolean isSuccess;
            Bundle params = new Bundle();
            String msg = null;
            int messageCode = intent.getIntExtra(MainConsts.EXTRA_WHAT, -1);

            switch(messageCode)
            {
            case MainConsts.MSG_AIMFIRE_SERVICE_STATUS:
			    /*
 			     * if status message
 			     */
                msg = intent.getStringExtra(MainConsts.EXTRA_STATUS);
                if(msg != null)
                {
                    if(BuildConfig.DEBUG) Log.d(TAG, msg + "\n");
                }
                break;
            case MainConsts.MSG_AIMFIRE_SERVICE_AUDIO_EVENT_SYNC_START:
 			    /*
		         * start of audio request. if we had a failure in audio test then this most likely
		         * isn't going to succeed.
		         */
                int audioTestResult = mAimfireService.getAudioTestResult();
                if(audioTestResult != MainConsts.AUDIO_TEST_RESULT_OK)
                {
                    params.putInt("audioTestError", audioTestResult);
                    mFirebaseAnalytics.logEvent(MainConsts.FIREBASE_CUSTOM_EVENT_AUDIO_TEST_ERROR, params);
                    if(BuildConfig.DEBUG) Log.d(TAG, "starting sync despite audio test failure");
                }

                mFirebaseAnalytics.logEvent(MainConsts.FIREBASE_CUSTOM_EVENT_SYNC_START, null);
                mIsInitiator = intent.getBooleanExtra(MainConsts.EXTRA_INITIATOR, false);
                handleSyncStart();
                break;
            case MainConsts.MSG_AIMFIRE_SERVICE_AUDIO_EVENT_SYNC_END:
 			    /*
		         * end of audio request
		         */
                isSuccess = intent.getBooleanExtra(MainConsts.EXTRA_RESULT, true);

                if(isSuccess)
                {
                    mFirebaseAnalytics.logEvent(MainConsts.FIREBASE_CUSTOM_EVENT_SYNC_END, null);
                }
                else
                {
                    mFirebaseAnalytics.logEvent(MainConsts.FIREBASE_CUSTOM_EVENT_SYNC_ERROR, null);
                }

                handleSyncEnd(isSuccess);
                break;
            case MainConsts.MSG_AIMFIRE_SERVICE_AUDIO_EVENT_SYNC_MEAS:
 			    /*
		         * whether we get a valid measurement on this device
		         */
                isSuccess = intent.getBooleanExtra(MainConsts.EXTRA_RESULT, true);

 			    /*
		         * if audio measurement result
		         */
                if(!isSuccess)
                {
                    mFirebaseAnalytics.logEvent(MainConsts.FIREBASE_CUSTOM_EVENT_SYNC_MEAS_ERROR, null);
                }
                break;
            case MainConsts.MSG_AIMFIRE_SERVICE_AUDIO_EVENT_ERROR:
 			    /*
 			     * ignore reference code errors that occur only in non-offline mode.
 			     */
                break;
            case MainConsts.MSG_AIMFIRE_SERVICE_P2P_CONNECTING:
                mP2pAttempted = true;

                mScanProgDrawable.setMessageText(getString(R.string.connecting));
                if(BuildConfig.DEBUG) Log.d(TAG, "P2P service peer found, connecting..." + "\n");
                params.putString("localDeviceName", intent.getStringExtra(MainConsts.EXTRA_NAME));
                params.putString("remotelDeviceName", intent.getStringExtra(MainConsts.EXTRA_NAME2));
                mFirebaseAnalytics.logEvent(MainConsts.FIREBASE_CUSTOM_EVENT_SYNC_CONNECTING, params);
                break;
            case MainConsts.MSG_AIMFIRE_SERVICE_P2P_CONNECTED:
                mP2pConnected = true;

                mScanProgDrawable.setMessageText(getString(R.string.syncing));
	    	    break;
 		    case MainConsts.MSG_AIMFIRE_SERVICE_P2P_DISCONNECTED:
                mP2pConnected = false;

                //exit
				launchMain();
	    	    break;
 		    case MainConsts.MSG_AIMFIRE_SERVICE_P2P_FAILURE:
	    	    /*
	    	     * most likely the underlying P2P link is broken,
	    	     */
                int code = intent.getIntExtra(MainConsts.EXTRA_CMD, -1);
                if(BuildConfig.DEBUG) Log.e(TAG, "onReceive: p2p failure, code=" + code);
                params.putInt("p2pErrorCode", code);
                mFirebaseAnalytics.logEvent(MainConsts.FIREBASE_CUSTOM_EVENT_P2P_ERROR, params);
	    	    break;
 		    case MainConsts.MSG_AIMFIRE_SERVICE_P2P_FILE_RECEIVED:
	    	    /*
	    	     * file received. remember the file name
	    	     */
                String filename = intent.getStringExtra(MainConsts.EXTRA_FILENAME);
                if(BuildConfig.DEBUG) Log.d(TAG, "onReceive: received file from remote device: "
                    + filename);
                
	    	    /*
	    	     * video received from remote device. process it
	    	     */
                queueMovieProcessor(false/*isLocal*/, filename);
                mFirebaseAnalytics.logEvent(MainConsts.FIREBASE_CUSTOM_EVENT_SYNC_MOVIE_CAPTURE_FILE_RECEIVED, null);
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
	    	         *
	    	         * if can happen that the remote device finished encoding of previous video
	    	         * and send us this command, BEFORE we finish encoding of previous video.
	    	         * for example, we tried to stop audio encoder right after the previous
	    	         * block of audio was delivered - in which case we need to wait for
	    	         * PERIOD_ENCODER_READ_MS to end the audio encoder. therefore, we need to
	    	         * check if our encoder is still encoding.
	    	         */
               	    if(!mRecordingEnabled && !mMovieEncoder.isRecording())
               	    {
                        if(BuildConfig.DEBUG) Log.d(TAG, "ACTION_START: " + Long.parseLong(tokens[1]));

                        startUs = mSyncTimeUs + Long.parseLong(tokens[1]);
                        setRecordingStart(startUs);
    
                        long timeToWaitUs = startUs - SystemClock.elapsedRealtimeNanos()/1000;
                        if(BuildConfig.DEBUG) Log.d(TAG, "CMD_DEMO_CAMERA_ACTION_START received, mSyncTimeUs=" + mSyncTimeUs +
                   		    ", startUs=" + startUs + ", timeToWaitUs=" + timeToWaitUs);
    
                        if(timeToWaitUs > P2P_LATENCY_US)
                        {
                   	        if(BuildConfig.DEBUG) Log.e(TAG, "CMD_DEMO_CAMERA_ACTION_START: start timing looks wrong!");
                            FirebaseCrash.report(new Exception("CamcorderActivity CMD_DEMO_CAMERA_ACTION_START: start timing looks wrong!"));
                        }
               	    }
               	    else
               	    {
                   	    if(BuildConfig.DEBUG) Log.e(TAG, "CMD_DEMO_CAMERA_ACTION_START: previous recording not finsihed on this device!");
                        FirebaseCrash.report(new Exception("CamcorderActivity CMD_DEMO_CAMERA_ACTION_START: previous recording not finished on this device!"));
               	    }
	    	        break;
                case MainConsts.CMD_DEMO_CAMERA_ACTION_END:
	    	        /*
	    	         * action stop received. stop it right away
	    	         */
              	    if(mRecordingEnabled)
               	    {
                        stopUs = SystemClock.elapsedRealtimeNanos()/1000;
                        setRecordingStop(stopUs);
               	    }
               	    else
               	    {
                   	    if(BuildConfig.DEBUG) Log.e(TAG, "CMD_DEMO_CAMERA_ACTION_END: recording is not enabled on this device!");
                        FirebaseCrash.report(new Exception("CamcorderActivity CMD_DEMO_CAMERA_ACTION_END: recording is not enabled on this device!"));
               	    }
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
                case MainConsts.CMD_DEMO_CAMERA_REPORT_PARAMETERS:
	    	        /*
	    	         * camera parameters from the other device
	    	         */
                    String posPref = tokens[1];
 	                int qualityPref = Integer.parseInt(tokens[2]);
                    int maxQuality = Integer.parseInt(tokens[3]);
                    int maxWidth = Integer.parseInt(tokens[4]);
                    int maxHeight = Integer.parseInt(tokens[5]);
 	                float viewAngleX = Float.parseFloat(tokens[6]);
 	                float viewAngleY = Float.parseFloat(tokens[7]);
 	                float focusLen = Float.parseFloat(tokens[8]);
 	                syncCamParams(posPref, qualityPref, maxQuality, maxWidth, maxHeight,
                		viewAngleX, viewAngleY, focusLen);
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
	    	         * demo is put to background on the remote device. stop our side
	    	         * of the recording right away (if it is in progress)
	    	         */
	        	    if(mRecordingEnabled)
	        	    {
                        stopUs = SystemClock.elapsedRealtimeNanos()/1000;
                        setRecordingStop(stopUs);
	        	    }

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
    private BroadcastReceiver mMovieProcessorMsgReceiver = new BroadcastReceiver() 
    {
        @Override
        public void onReceive(Context context, Intent intent) 
        {
       	    String filePath;
       	    //String filePath2;
            Bundle bundle = new Bundle();

            int messageCode = intent.getIntExtra(MainConsts.EXTRA_WHAT, -1);
            switch(messageCode)
            {
            case MainConsts.MSG_MOVIE_PROCESSOR_RESULT:
                filePath = intent.getStringExtra(MainConsts.EXTRA_PATH);
           		if(BuildConfig.DEBUG) Log.d(TAG, "onReceive: " + filePath + " processing done");
           	    break;
            case MainConsts.MSG_MOVIE_PROCESSOR_ERROR:
                filePath = intent.getStringExtra(MainConsts.EXTRA_PATH);
           		if(BuildConfig.DEBUG) Log.d(TAG, "onReceive: " + filePath + " error processing movie pair");

                if(!mLocalCameraPaused)
                {
                    CustomToast.show(getActivity(), 
                        getString(R.string.error_video_alignment), 
       		            Toast.LENGTH_LONG);
                }
                break;
            default:
           	    break;
            }
            
            /*
             * time to show the thumbnail
             */
            loadCurrThumbnail();

            /*
             * enable view3D button
             */
            mView3DButton.setEnabled(true);

            /*
             * remove progress bar over the view3D button
             */
            ProgressBar pb = (ProgressBar) findViewById(R.id.view3D_progress_bar);
            pb.setVisibility(View.GONE);
        }
    };

    /**
     * BroadcastReceiver for movie encoder completion
     */
    private BroadcastReceiver mMovieEncoderMsgReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            int messageCode = intent.getIntExtra(MainConsts.EXTRA_WHAT, -1);
            switch(messageCode)
            {
                case MainConsts.MSG_MOVIE_ENCODER_COMPLETE:
                    if(mInSync)
                    {
                        mFirebaseAnalytics.logEvent(MainConsts.FIREBASE_CUSTOM_EVENT_SYNC_MOVIE_CAPTURE_ENCODED, null);
                        /*
                         * if 3D capature - send file to remote device
                         */
                        (new Thread(SendFileTask)).start();
                    }
                    else
                    {
                        /*
                         * if 2D capature - we are done
                         */
                        generateThumbAndPreview(mMpegFilepath);

                        /*
                         * make MediaScanner aware of the new movie
                         */
                        MediaScanner.addItemMediaList(mMpegFilepath);

                        /*
                         * time to show the thumbnail
                         */
                        loadCurrThumbnail();

                        /*
                         * enable view3D button
                         */
                        mView3DButton.setEnabled(true);

                        /*
                         * remove progress bar over the view3D button
                         */
                        ProgressBar pb = (ProgressBar) findViewById(R.id.view3D_progress_bar);
                        pb.setVisibility(View.GONE);
                    }
                    break;
                case MainConsts.MSG_MOVIE_ENCODER_ERROR:
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
                    FirebaseCrash.report(e);
				    return;
			    }
    	    }

            /*
             * necessary to set app type in audio context here because we may be
             * switched from camera mode
             */
            mAimfireService.setAppType(ActivityCode.CAMCORDER.getValue());
	    }
    };

	@Override
    protected void onCreate(Bundle savedInstanceState) 
    {
        if(BuildConfig.DEBUG) Log.d(TAG, "create CamcorderActivity");

        loadPrefs();

        /*
         *  keep the screen on until we turn off the flag 
         */
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camcorder);

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
         * force CamcorderActivity in landscape because it is the natural 
         * orientation of the camera sensor
         */
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        /*
         * get the orientation for SCREEN_ORIENTATION_LANDSCAPE mode. this is the 
         * clockwise rotation of landscape mode from device natural orientation.
         * reverse landscape is 180 degrees different. call this *after* the
         * display orientation is fixed, because only then getDefaultDisplay().
         * getRotation() will consistently return the value we require.
         */
        mLandscapeOrientation = getDeviceLandscapeOrientation();

        /*
         * apply the adapter to the spinner - for filter selection.
         */
        /*
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
            R.array.cameraFilterNames, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(this);
        */

        mParentView = getActivity().getWindow().getDecorView();

        mCaptureButton = (ImageButton) findViewById(R.id.toggle_recording_button);
        mView3DButton = (ImageButton) findViewById(R.id.view3D_button);
        mExitButton = (ImageButton) findViewById(R.id.exit_button);
		mPvButton = (ImageButton) findViewById(R.id.switch_photo_video_button);
		mFbButton = (ImageButton) findViewById(R.id.switch_front_back_button);
		mLevelButton = (Button) findViewById(R.id.level_button);
	    mTimeCounter = (LinearLayout) findViewById(R.id.time_counter);
        mScanModeButton = (ImageButton) findViewById(R.id.mode_button);

        mCaptureButton.setOnClickListener(oclToggle);
        mView3DButton.setOnClickListener(oclView3D);
        mExitButton.setOnClickListener(oclExit);
        mPvButton.setOnClickListener(oclPV);
        mFbButton.setOnClickListener(oclFB);
        mScanModeButton.setOnClickListener(oclSwitchMode);

        mShutterLayout = (FrameLayout) findViewById(R.id.shutter_layout);
        mProgView = (ImageView) findViewById(R.id.circular_progress_view);

        mProgDrawable = new com.aimfire.utilities.CircularProgressDrawable.Builder()
            .setRingWidth(10)
            .setRingColor(getResources().getColor(R.color.orange))
            .create();
    
        mProgView.setImageDrawable(mProgDrawable);

        mScanProgView = (ImageView) findViewById(R.id.scan_circular_progress_view);
        mScanProgView.setOnClickListener(oclScan);

        int [] centerGradient = new int []{
                getResources().getColor(R.color.start_button_start_color_pressed),
                getResources().getColor(R.color.start_button_end_color_pressed)};


        mScanProgDrawable = new com.aimfire.utilities.CircularProgressDrawable.Builder()
                .setRingWidth(10)
                .setInnerCircleScale(1.0f)
                .setOutlineColor(getResources().getColor(R.color.dark_grey))
                .setRingColor(getResources().getColor(R.color.white))
                .setArcColor(getResources().getColor(android.R.color.holo_blue_dark))
                .setCenterGradient(centerGradient)
                .setWifiBarColor(getResources().getColor(R.color.blue))
                .setMessageSize((int) (10/*sp*/*getResources().getDisplayMetrics().density))
                .setMessageColor(getResources().getColor(R.color.white))
                .create();

        mScanProgView.setImageDrawable(mScanProgDrawable);

        /*
         * showing animation for searching remote device
         */
        startScanAnim();

   	    String startSound = null;
   	    String stopSound = null;
   	    for(String s : CAMERA_RECORDING_START_SOUND)
   	    {
    	    if((new File(s)).exists())
    	    {
  	    	    startSound = s;
   	    	    break;
    	    }
   	    }

	    if(startSound != null)
	    {
            mCamStartSoundPlayer = MediaPlayer.create(this, Uri.fromFile(new File(startSound)));
	    }

   	    for(String s : CAMERA_RECORDING_STOP_SOUND)
   	    {
    	    if((new File(s)).exists())
    	    {
  	    	    stopSound = s;
  	    	    break;
    	    }
   	    }

	    if(stopSound != null)
	    {
            mCamStopSoundPlayer = MediaPlayer.create(this, Uri.fromFile(new File(stopSound)));
	    }

        /*
         * file name prefix for solo mode. rest of the file name (date and time stamp) are
         * added when recording starts.
         */
        mMpegPrefixSolo = MainConsts.MEDIA_3D_SAVE_PATH + "MPG_solo_";

        /*
         * place UI controls at their initial, default orientation
         */
        adjustUIControls(0);

        /*
         * load the thumbnail of the newest movie to the view3D button
         */
        loadCurrThumbnail();

        /*
         * attempt to open camera with desired dimension. the dimension may be
         * changed if camera doesn't support it, in which case the "preferred" 
         * (by the camera) dimension will be used
         */
        boolean success = openCamera(Camera.CameraInfo.CAMERA_FACING_BACK, mQualityPref);

        if(!success)
        {
            Toast.makeText(this, R.string.error_opening_camera, Toast.LENGTH_LONG).show();

       	    finish();
       	    return;
        }
        
        /*
         * define a handler that receives camera-control messages from other threads.  
         * all calls to Camera must be made on the same thread.  note we create this 
         * before the renderer thread, so we know the fully-constructed object will 
         * be visible.
         */
        mCameraHandler = new CameraHandler(this);

        /*
         * configure the GLSurfaceView.  this will start the Renderer thread, with an
         * appropriate EGL context.
         */
        mGLView = (GLSurfaceView) findViewById(R.id.cameraPreview_surfaceView);
        mGLView.setEGLContextClientVersion(2);     // select GLES 2.0
        mRenderer = new CameraSurfaceRenderer(mCameraHandler, mMovieEncoder);
        mGLView.setRenderer(mRenderer);
        mGLView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        mGLView.setOnTouchListener(otl);

        /*
         * bind to Aimfire service
         */
        mAimfireServiceConn = new AimfireServiceConn(this);
        
        /*
         * binding doesn't happen until later. wait for it to happen in another 
         * thread and connect to p2p peer if necessary
         */
        (new Thread(mAimfireServiceInitTask)).start();

        /*
         * register for AimfireService message broadcast
         */
        LocalBroadcastManager.getInstance(this).registerReceiver(mAimfireServiceMsgReceiver,
            new IntentFilter(MainConsts.AIMFIRE_SERVICE_MESSAGE));
        
	    /*
	     * register for intents sent by the media processor service
	     */
        LocalBroadcastManager.getInstance(this).registerReceiver(mMovieProcessorMsgReceiver,
            new IntentFilter(MainConsts.MOVIE_PROCESSOR_MESSAGE));

	    /*
	     * register for intents sent by the media processor service
	     */
        LocalBroadcastManager.getInstance(this).registerReceiver(mMovieEncoderMsgReceiver,
                new IntentFilter(MainConsts.MOVIE_ENCODER_MESSAGE));
    }

    @Override
    protected void onNewIntent(Intent intent)
    {
        if (BuildConfig.DEBUG) Log.d(TAG, "onNewIntent");

        /*
         * we will now set up the synchronization between two cameras
         */
        Bundle extras = intent.getExtras();
        if (extras == null)
        {
            if (BuildConfig.DEBUG) Log.e(TAG, "onNewIntent: wrong parameter");
            FirebaseCrash.report(new Exception("CamcorderActivity onNewIntent: wrong parameter"));
            finish();
            return;
        }

        mSyncTimeUs = extras.getLong(MainConsts.EXTRA_SYNCTIME, -1);

        /*
         * we could get here in two ways: 1) AimfireService completed sync with remote device.
         * 2) a switch from photo to video mode. if it is the latter, EXTRA_MSG is true
         */
        mPvSwitching = extras.getBoolean(MainConsts.EXTRA_MSG, false);

        /*
         * originally we set the initiator to be the left device. now we will set left/right
         * based on user preference
         */
        if(mPvSwitching)
        {
            mIsLeft = extras.getBoolean(MainConsts.EXTRA_ISLEFT);
        }

        /*
         * send parameters of our camera to remote.
         */
        sendCamParams();

//      CustomToast.show(getActivity(),
//          mIsLeft?getActivity().getString(R.string.left_camera_ready):
//                  getActivity().getString(R.string.right_camera_ready),
//          Toast.LENGTH_SHORT);
    }

    @Override
    protected void onRestart() 
    {
        super.onRestart();

        if(mP2pConnected)
        {
            if(!mAimfireService.isP2pConnected())
            {
                /*
                 * we were in 3d mode, but came back and found we are now disconnected. it must be
       	         * that during the time this activity is not in foreground, remote device has
       	         * disconnected, in which case we terminate.
       	         */
                finish();
                return;
            }
            else if(mAimfireService != null)
            {
                /*
                 * we remain connected, tell remote device we came back
                 */
                mAimfireService.restartDemo();
            }
        }

        /*
         *  keep the screen on until we turn off the flag 
         */
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if(BuildConfig.DEBUG) Log.d(TAG, "onRestart -- acquiring camera");
        mGLView.onResume();
        
        /*
         * restart camera, keep the last camera facing. reuse EGL context
         * just as if we are doing a switch
         */
        switchCamera(mCameraFacing);
        
        /*
         * place UI controls at their initial, default orientation
         */
        adjustUIControls(0);

        /*
         * load the latest thumbnail to the view3D button. we need
         * to do this because we (the CamcorderActivity) has been in
         * the background, and during this time, GalleryActivity may
         * have deleted the thumb and it's associated image that was
         * showing before
         */
        loadCurrThumbnail();

        if(mOrientationEventListener != null)
     	    mOrientationEventListener.enable();
    	    
        LocalBroadcastManager.getInstance(this).registerReceiver(mAimfireServiceMsgReceiver,
            new IntentFilter(MainConsts.AIMFIRE_SERVICE_MESSAGE));

        mLocalCameraPaused = false;
    }

    @Override
    protected void onStop() 
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
         * Camera mode, we have already stopped camera.
         */
        if(!mSwitchPhotoVideo)
        {
       	    stopCamera();
        	    
            if(mAimfireService != null)
            {
                mAimfireService.stopDemo();
            }
        }

        LocalBroadcastManager.getInstance(this).unregisterReceiver(mAimfireServiceMsgReceiver);

        mSwitchPhotoVideo = false;
        mLocalCameraPaused = true;
    }

    @Override
    protected void onDestroy() 
    {
        if(BuildConfig.DEBUG) Log.d(TAG, "onDestroy");

        /*
	     * screen can turn off now.
       	 */
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if(mCameraHandler != null)
        {
            mCameraHandler.invalidateHandler();     // paranoia
        }
        
   	    if(mAimfireServiceConn != null)
   	    {
   	        mAimfireServiceConn.unbind();
   	    }
    	    
   	    /*
   	     * de-register for intents sent by the media processor service
   	     */
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMovieProcessorMsgReceiver);

   	    /*
   	     * de-register for intents sent by the media encoder service
   	     */
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMovieEncoderMsgReceiver);

        /*
         * unpin - no effect if screen not pinned
         */
        if (Build.VERSION.SDK_INT >= 21)
        {
            try {
       	        stopLockTask();
            }
            catch (RuntimeException e) {
                if(BuildConfig.DEBUG) Log.e(TAG, "stopLockTask generated RuntimeException!");
            }
        }

        super.onDestroy();
    }

    // spinner selected
    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) 
    {
        Spinner spinner = (Spinner) parent;
        final int filterNum = spinner.getSelectedItemPosition();

        if(BuildConfig.DEBUG) Log.d(TAG, "onItemSelected: " + filterNum);
        mGLView.queueEvent(new Runnable() 
        {
            @Override public void run() 
            {
                /*
                 * notify the renderer that we want to change the encoder's state
                 */
                mRenderer.changeFilterMode(filterNum);
            }
        });
    }

    @Override public void onNothingSelected(AdapterView<?> parent) {}

    @Override
    public void onBackPressed()
    {
   	    exitCamera();
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
            
            /*
             * trying to detect thumb press vs. finger press is a little harder than 
             * anticipated, as the getSize and getTouchMajor/Minor methods return 
             * values that are not very clearly separating the two types. we could try 
             * to do this, but false positive/negative rates will be quite high.
             */
            //float xPosition = event.getRawX();
            //float yPosition = event.getRawY();
            //float sizeMajor = event.getTouchMajor();
            //float sizeMinor = event.getTouchMinor();

            /*
             * we detect here if devices are stacked. we also return false for 
             * ACTION_POINTER_DOWN/UP events, so the buttons (shutter, view3D, 
             * and exit) can process these events for detecting clicks.
             */
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

    private void loadPrefs()
    {
        SharedPreferences settings =
            getSharedPreferences(getString(R.string.settings_file), Context.MODE_PRIVATE);

        /*
         * here settings != null doesn't mean the file necessarily exist!
         */
        if (settings != null) 
        {
            mCreatorName = settings.getString(MainConsts.DRIVE_PERSON_NAME, null);
            mCreatorPhotoUrl = settings.getString(MainConsts.DRIVE_PERSON_PHOTO_URL, null);

            String videoRes = settings.getString(MainConsts.VIDEO_RESOLUTION_PREFS_KEY, MainConsts.VIDEO_RESOLUTION_720P);
            if(videoRes.equals(MainConsts.VIDEO_RESOLUTION_1080P))
            {
                mQualityPref = MainConsts.VIDEO_QUALITY_1080P;
            }
            else if(videoRes.equals(MainConsts.VIDEO_RESOLUTION_720P))
            {
                mQualityPref = MainConsts.VIDEO_QUALITY_720P;
            }
            else if(videoRes.equals(MainConsts.VIDEO_RESOLUTION_480P))
            {
                mQualityPref = MainConsts.VIDEO_QUALITY_SD_HQ;
            }

            mPosPref = settings.getString(MainConsts.CAMERA_POSITION_PREFS_KEY, MainConsts.CAMERA_POSITION_AUTO);
        }
    }

    private void updateQualityPref(int quality)
    {
        String resString = MainConsts.VIDEO_RESOLUTION_1080P;

        SharedPreferences settings =
                getSharedPreferences(getString(R.string.settings_file), Context.MODE_PRIVATE);

        /*
         * here settings != null doesn't mean the file necessarily exist!
         */
        if (settings != null)
        {
            if(quality == MainConsts.VIDEO_QUALITY_1080P)
            {
                resString = MainConsts.VIDEO_RESOLUTION_1080P;
            }
            else if(quality == MainConsts.VIDEO_QUALITY_720P)
            {
                resString = MainConsts.VIDEO_RESOLUTION_720P;
            }
            else if(quality == MainConsts.VIDEO_QUALITY_SD_HQ)
            {
                resString = MainConsts.VIDEO_RESOLUTION_480P;
            }

            SharedPreferences.Editor editor = settings.edit();
            editor.putString(MainConsts.VIDEO_RESOLUTION_PREFS_KEY, resString);
            editor.commit();
        }
    }

    private boolean checkShowHint(boolean is2d)
    {
        boolean show;
        String key;

        SharedPreferences settings =
            getSharedPreferences(getString(R.string.settings_file), Context.MODE_PRIVATE);

        /*
         * here settings != null doesn't mean the file necessarily exist!
         */
        if (settings != null) 
        {
            if(is2d)
            {
                key = MainConsts.SHOW_HINT_2D_PREFS_KEY;
            }
            else
            {
                key = MainConsts.SHOW_HINT_3D_PREFS_KEY;
            }

            show = settings.getBoolean(key, true);

            if(!show)
            {
                return false;
            }

            if(is2d || mIsLeft)
            {
       	        /*
       	         * disable hint for subsequent launches
       	         */
                SharedPreferences.Editor editor = settings.edit();
                editor.putBoolean(key, false);
                editor.commit();

                return true;
            }
        }
        return false;
    }

    private void startRecordingAnim(int durationSeconds)
    {
        if(MainConsts.VIDEO_LENGTH_SECONDS == -1)
        {
       	    return;
        }

        if(mCurrAnimator == null)
        {
            mCurrAnimator = prepareRecordingAnimator(durationSeconds);
        }
        mCurrAnimator.start();
    }

	private void stopRecordingAnim()
    {
        if(MainConsts.VIDEO_LENGTH_SECONDS == -1)
        {
       	    return;
        }

        if(mCurrAnimator != null)
        {
            mCurrAnimator.removeAllListeners();
            mCurrAnimator.cancel();
            mCurrAnimator = null;

            mProgDrawable.reset();
        }
    }

    private Animator prepareRecordingAnimator(int durationSeconds)
    {
        final AnimatorSet animatorSet = new AnimatorSet();

        mProgDrawable.setIndeterminate(false);
        mProgDrawable.setUseRotation(false);
        mProgDrawable.setUseArc(false);
        mProgDrawable.setUseAlpha(false);
        mProgDrawable.setUseWifiBar(false);

        Animator determinateAnimator = ObjectAnimator.ofFloat(mProgDrawable, CircularProgressDrawable.PROGRESS_PROPERTY, 0, 1);
        determinateAnimator.setDuration(durationSeconds*1000);

        animatorSet.playTogether(determinateAnimator);
        
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animator) 
            {
   	    	    // we only need to execute this once
                if(mInSync)
                {
                    syncStopRecording();
                }
                else
                {
                    soloStopRecording();
                }
            }
        });

        return animatorSet;
    }

    private void exitCamera()
    {
        if(!mInSync)
        {
            launchMain();
            return;
        }

   	    int warning;
   	    if((mPendingTasks.size() > 0) || (mMovieEncoder.isRecording()))
   	    {
            /*
             * we are either encoding (which means we haven't started file transfer yet but will),
             * or we have at least one pending task (which means we haven't received file from
             * remote device yet)
             */
            warning = R.string.warning_file_transfer_in_progress;
   	    }
   	    else
   	    {
            warning = R.string.warning_exit_camera;
   	    }

   	    /*
   	     * TODO: change AlertDialog to a dialog fragment, such that we can
   	     * control the orientation. right now this AlertDialog is always
   	     * landscape because the activity is fixed to landscape.
   	     */
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
        if (BuildConfig.DEBUG) Log.d(TAG, "launchMain");

        end3dAttempt(-1);

        /*
         * set result code - "ok" here means device pairing was attempted,
         * "canceled" means it was not
         */
        if(mP2pAttempted)
        {
            setResult(Activity.RESULT_OK);
        }
        else
        {
            setResult(Activity.RESULT_CANCELED);
        }

        /*
         * exit out of camcorder
         */
   	    finish();
    }

    @Override
    public void onWindowFocusChanged (boolean hasFocus) 
    {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus)
        {
       	    //forceFullScreen();

            showHint2D();
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

    public void showHint3D()
    {
        if(checkShowHint(false/*is2d*/))
        {
            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this, R.style.AppCompatAlertDialogStyle);
            alertDialogBuilder.setTitle(R.string.instruction);
            alertDialogBuilder.setView(R.layout.dialog_stacking);

            alertDialogBuilder.setPositiveButton(R.string.got_it, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface arg0, int arg1) {
                    //do nothing
                }
            });

            alertDialogBuilder.setNegativeButton(R.string.tutorial, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    mFirebaseAnalytics.logEvent(MainConsts.FIREBASE_CUSTOM_EVENT_ACTION_TUTORIAL, null);
                    Intent intent = new Intent(Intent.ACTION_VIEW,
                            Uri.parse(getString(R.string.app_youtube_tutorial)));
                    startActivity(intent);
                }
            });

            AlertDialog alertDialog = alertDialogBuilder.create();
            alertDialog.show();
        }

        int title;
        int text;
        if(mIsLeft)
        {
            title = R.string.leftCamHintTitle;
            text = R.string.leftCamHintText;
        }
        else
        {
            title = R.string.rightCamHintTitle;
            text = R.string.rightCamHintText;
        }

        final SimpleHintContentHolder camHintBlock = new SimpleHintContentHolder.Builder(getActivity())
            .setContentTitle(title)
            .setContentText(text)
            .setTitleStyle(R.style.HintTitleText)
            .setContentStyle(R.style.HintDescText)
            .build();

        new HintCase(mParentView)
            .setHintBlock(camHintBlock, new FadeInContentHolderAnimator(), new FadeOutContentHolderAnimator())
            .show();
    }

    MaterialTapTargetPrompt.OnHidePromptListener modeButtonListener = new MaterialTapTargetPrompt.OnHidePromptListener() {
        @Override
        public void onHidePrompt(MotionEvent event, boolean tappedTarget)
        {
            //Do something such as storing a value so that this prompt is never shown again
        }

        @Override
        public void onHidePromptComplete()
        {
            new MaterialTapTargetPrompt.Builder(CamcorderActivity.this)
                    .setTarget(mCaptureButton)
                    .setPrimaryText(R.string.captureHintTitle)
                    .setSecondaryText(R.string.captureHintText)
                    .show();
        }
    };

    public void showHint2D()
    {
        if(!checkShowHint(true/*is2d*/))
        {
            return;
        }

        new MaterialTapTargetPrompt.Builder(this)
                .setTarget(mScanProgView)
                .setPrimaryText(R.string.modeButtonHintTitle)
                .setSecondaryText(R.string.modeButtonHintText)
                .setOnHidePromptListener(modeButtonListener)
                .show();
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

	public boolean onKeyDown(int keyCode, KeyEvent event) 
	{ 
		//if((keyCode == KeyEvent.KEYCODE_VOLUME_UP) || 
		   //(keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) || 
		   //(keyCode == KeyEvent.KEYCODE_CAMERA))
		if(keyCode == KeyEvent.KEYCODE_CAMERA)
	    {
            clickToggleRecording(null);
			return true;
	    }
		else if(keyCode == KeyEvent.KEYCODE_MENU)
		{
            return true;
		}
        return super.onKeyDown(keyCode, event); 
    }

    public Activity getActivity()
    {
   	    return this;
    }


    private void startScanAnim()
    {
        mScanProgView.setVisibility(View.VISIBLE);
        mScanModeButton.setVisibility(View.INVISIBLE);

        mAnimCyclesCnt = 0;
        //mScanProgDrawable.setMessageText(getString(R.string.preparing_camera));

        mScanCurrAnimator = prepare3DAnimator();

        if(mScanCurrAnimator != null)
        {
            mScanCurrAnimator.start();
        }
    }

    private void restartScanAnim()
    {
        mAnimCyclesCnt++;
        if(mAnimCyclesCnt == CONNECT_ANIM_CYCLES)
        {
            if (BuildConfig.DEBUG) Log.d(TAG, "restartScanAnim: max anim cycle reached, endDemo");
            end3dAttempt(R.string.error_finding_camera);
            return;
        }

        if(mScanCurrAnimator != null)
        {
            mScanCurrAnimator.removeAllListeners();
            mScanCurrAnimator.cancel();
        }

        mScanCurrAnimator = prepare3DAnimator();
        mScanCurrAnimator.start();
    }

    private void stopScanAnim()
    {
        mScanProgView.setVisibility(View.INVISIBLE);
        mScanModeButton.setVisibility(View.VISIBLE);

        if(mInSync)
        {
            mScanModeButton.setBackgroundResource(R.drawable.round_button_orange);
            mScanModeButton.setImageResource(R.drawable.ic_3d);
        }
        else
        {
            mScanModeButton.setBackgroundResource(R.drawable.round_button_gray);
            mScanModeButton.setImageResource(R.drawable.ic_2d);
        }

        if(mScanCurrAnimator != null)
        {
            mScanCurrAnimator.removeAllListeners();
            mScanCurrAnimator.cancel();
        }
    }

    private void accelScanAnim()
    {
        //mScanProgDrawable.setMessageText(getString(R.string.preparing_camera));
        if(mScanCurrAnimator != null)
        {
            mScanCurrAnimator.removeAllListeners();
            mScanCurrAnimator.cancel();
        }

        mScanCurrAnimator = ObjectAnimator.ofFloat(mScanProgDrawable,
                CircularProgressDrawable.PROGRESS_PROPERTY,
                mScanProgDrawable.getProgress(), 1);
        mScanCurrAnimator.setDuration(2000);
        mScanCurrAnimator.start();
    }

    /**
     * Style 1 animation will simulate a determinate loading
     *
     * @return Animator
     */
    private Animator prepare3DAnimator()
    {
        final AnimatorSet animatorSet = new AnimatorSet();

        mScanProgDrawable.setIndeterminate(false);
        mScanProgDrawable.setUseRotation(false);
        mScanProgDrawable.setUseArc(false);
        mScanProgDrawable.setUseAlpha(false);
        mScanProgDrawable.setUseWifiBar(true);

        mScanProgDrawable.setMessageText(getString(R.string.scanning));
        mScanProgDrawable.setSuppText(getString(R.string.tap_to_stop));

        //if(!mScanProgDrawable.getMessageText().equals(getString(R.string.connecting)))
        //{
            //mScanProgDrawable.setMessageText(getString(R.string.scanning) + (mAnimCyclesCnt+1) + " of " + CONNECT_ANIM_CYCLES);
        //}

        Animator determinateAnimator = ObjectAnimator.ofFloat(mScanProgDrawable, CircularProgressDrawable.PROGRESS_PROPERTY, 0, 1);
        determinateAnimator.setDuration(CONNECT_ANIM_CYCLE_LENGTH_SECONDS*1000);

        /*
         * wifi bar highlight changes 3 times a second
         */
        Animator wifiBarAnimator = ObjectAnimator.ofInt(mScanProgDrawable,
                CircularProgressDrawable.WIFI_BAR_PROPERTY, 0, 2*CONNECT_ANIM_CYCLE_LENGTH_SECONDS);
        wifiBarAnimator.setDuration(CONNECT_ANIM_CYCLE_LENGTH_SECONDS*1000);
        wifiBarAnimator.setInterpolator(new LinearInterpolator());

        animatorSet.playTogether(wifiBarAnimator, determinateAnimator);

        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animator)
            {
                restartScanAnim();
            }
        });

        return animatorSet;
    }

    public void handleSyncStart()
    {
        if(BuildConfig.DEBUG) Log.d(TAG, "handleSyncStart\n");
        accelScanAnim();
    }

    public void handleSyncEnd(boolean isSuccess)
    {
        if(isSuccess)
        {
            if(BuildConfig.DEBUG) Log.d(TAG, "handleSyncEnd: sync success!\n");
            mInSync = true;
        }
        else
        {
            if(BuildConfig.DEBUG) Log.d(TAG, "handleSyncEnd: sync failed!\n");
            Toast.makeText(this, R.string.error_connecting, Toast.LENGTH_LONG).show();

            /*
             * AimfireService has ended its side of demo already, not need to call
             * mAimfireService.endDemo();
             */
            mInSync = false;
        }
        stopScanAnim();
    }

    /**
     * get the default/natural device orientation. this should be PORTRAIT
     * for phones and LANDSCAPE for tablets
     */
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
    /**
     * we fixed display to SCREEN_ORIENTATION_LANDSCAPE. but we don't know
     * how it is related to device's natural orientation. fortunately,
     * getDefaultDisplay().getRotation() tells us the information but with
     * a slight twist:
     * 
     * Documentation for Display.getRotation: 
     *     Returns the rotation of the screen from its "natural" orientation. The 
     *     returned value may be Surface.ROTATION_0 (no rotation), 
     *     Surface.ROTATION_90, Surface.ROTATION_180, or Surface.ROTATION_270. 
     *     For example, if a device has a naturally tall screen, and the user has 
     *     turned it on its side to go into a landscape orientation, the value 
     *     returned here may be either Surface.ROTATION_90 or Surface.ROTATION_270 
     *     depending on the direction it was turned. The angle is the rotation of 
     *     the drawn graphics on the screen, which is the opposite direction of the 
     *     physical rotation of the device. For example, if the device is rotated 90 
     *     degrees counter-clockwise, to compensate rendering will be rotated by 90 
     *     degrees clockwise and thus the returned value here will be 
     *     Surface.ROTATION_90.
     * 
     * if we fix the display orientation, getRotation is going to tell us what the
     * fixed orientation is, relative to device natural orientation, regardless of
     * what the device's current, actual orientation is. and based on above, we need
     * to reverse the result from getRotation to get clockwise rotation
     */
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

    public void setCameraPreviewOrientation()
    {
        if(BuildConfig.DEBUG) Log.d(TAG, "setCameraPreviewOrientation");
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(mCameraId, info);
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) 
        {
            case Surface.ROTATION_0: degrees = 0; break;
            case Surface.ROTATION_90: degrees = 90; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) 
        {
            // front-facing
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } 
        else 
        {  
            // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        mCamera.setDisplayOrientation(result);
    }

    public void setCameraRotation()
    {
        if(BuildConfig.DEBUG) Log.d(TAG, "setCameraRotation");
        OrientationEventListener listener = 
    		    new OrientationEventListener(this, SensorManager.SENSOR_DELAY_NORMAL) 
        {
            @Override
            public void onOrientationChanged(int orientation) 
            {
                if (orientation == ORIENTATION_UNKNOWN) return;
    
                Camera.CameraInfo info = new Camera.CameraInfo();
                Camera.getCameraInfo(mCameraId, info);
                orientation = (orientation + 45) / 90 * 90;
                int rotation = 0;
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) 
                {
                    rotation = (info.orientation - orientation + 360) % 360;
                } 
                else 
                {  // back-facing camera
                    rotation = (info.orientation + orientation) % 360;
                }
                Camera.Parameters parms = mCamera.getParameters();
                parms.setRotation(rotation);
                mCamera.setParameters(parms);
            }
        };

        if (listener.canDetectOrientation())
        {
            listener.enable();
        }
    }
    
    /**
     * Opens a camera, and attempts to establish preview mode at the specified width 
     * and height.
     * <p>
     * Sets mCameraPreviewWidth and mCameraPreviewHeight to the actual width/height 
     * of the preview.
     */
    private boolean openCamera(int desiredFacing, int videoQuality) 
    {
        if (mCamera != null) 
        {
            if(BuildConfig.DEBUG) Log.e(TAG, "openCamera: camera already initialized");
            FirebaseCrash.report(new Exception("CamcorderActivity openCamera: camera already initialized"));
            return false;
        }

        final Camera.CameraInfo info = new Camera.CameraInfo();
  
        /*
         *  Try to find camera with desired facing
         */
        int numCameras = Camera.getNumberOfCameras();
        if (numCameras == 0)
        {
            if(BuildConfig.DEBUG) Log.e(TAG, "openCamera: No camera found, exiting");
            FirebaseCrash.report(new Exception("openCamera: No camera found, exiting"));
            return false;
        }

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
            if(BuildConfig.DEBUG) Log.d(TAG, "openCamera: No camera with desired facing found; opening default");
            FirebaseCrash.report(new Exception("openCamera: No camera with desired facing found; opening default"));
            mCameraId = 0;
        }

        try {
            mCamera = Camera.open(mCameraId);    
        }
        catch (RuntimeException e) {
            if(BuildConfig.DEBUG) Log.e(TAG, "openCamera: cannot open camera!");
            FirebaseCrash.report(e);
            return false;
        }

        mCameraOrientation = info.orientation;
        mCameraFacing = info.facing;

        mCameraParams = mCamera.getParameters();

        CameraUtils.setCamParams(mCameraParams);

        /*
         * if we can find a supported video/preview size that's the same as our desired size,
         * use it. otherwise, use the best quality supported by the camera.
         */
        mSupportedVideoQualities = CameraUtils.getSupportedVideoQualities();
        if((mSupportedVideoQualities & (1<<mQualityPref)) == 0)
        {
            if(BuildConfig.DEBUG) Log.d(TAG, "openCamera: desired quality " + mQualityPref +
                    " not supported");

            mQualityPref = CameraUtils.getMaxVideoQuality();

            /*
             * since this device doesn't support whatever quality preference we had before,
             * we save the best quality that it does support
             */
            updateQualityPref(mQualityPref);
        }
        mCameraParams.setPreviewSize(MainConsts.VIDEO_DIMENSIONS[mQualityPref][0],
                MainConsts.VIDEO_DIMENSIONS[mQualityPref][1]);

        AspectFrameLayout afl = (AspectFrameLayout) findViewById(R.id.cameraPreview_frame);
        afl.setAspectRatio((float)MainConsts.VIDEO_DIMENSIONS[mQualityPref][0]/
        		(float)MainConsts.VIDEO_DIMENSIONS[mQualityPref][1]);

        /*
         * give the camera a hint that we're recording video. this can have a big
         * impact on frame rate.
         */
        mCameraParams.setRecordingHint(true);

        /*
         * disable all the automatic settings, in the hope that frame rate will
         * be less variable
         * 
         * TODO: if any of the default modes are not available then we need to 
         * sync it with the remote device
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
    
        /*
         * zoom can impact view angle. we should set it to 0 if it's not
         */
        if(mCameraParams.isZoomSupported())
        {
            int zoom = mCameraParams.getZoom();
            if(zoom != 0)
            {
                if(BuildConfig.DEBUG) Log.d(TAG, "getViewAngle: camera zoom = " + zoom +
               		", forcing to zero");
                mCameraParams.setZoom(0);
            }
        }

        /*
         *  leave the frame rate set to default
         */
        mCamera.setParameters(mCameraParams);

        /*
        int[] fpsRange = new int[2];
        mCameraParams.getPreviewFpsRange(fpsRange);
        String previewFacts = VIDEO_DIMENSIONS[mQualityPref][0] + "x" + VIDEO_DIMENSIONS[mQualityPref][1];
        if (fpsRange[0] == fpsRange[1]) {
            previewFacts += " @" + (fpsRange[0] / 1000.0) + "fps";
        } else {
            previewFacts += " @[" + (fpsRange[0] / 1000.0) +
                " - " + (fpsRange[1] / 1000.0) + "] fps";
        }
        TextView text = (TextView) findViewById(R.id.cameraParams_text);
        text.setText(previewFacts);
        */

        if(mNaturalOrientation == Configuration.ORIENTATION_PORTRAIT)
        {
           if(((info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) && (mLandscapeOrientation == mCameraOrientation)) ||
              ((info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) && (mLandscapeOrientation != mCameraOrientation)))
           {
               mCamera.setDisplayOrientation(180);
               mCameraOrientation = (mCameraOrientation+180)%360;
           }
        }

        if(mOrientationEventListener == null)
        {
            mOrientationEventListener = new OrientationEventListener(this, 
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
                final Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                	    public void run()
                	    {
                        int deviceOrientation = mCurrDeviceOrientation;
                        mCurrDeviceOrientation = -1;
                        handleOrientationChanged(deviceOrientation);
                	    }
                }, 100);
    	        }
        };
        runOnUiThread(forceOrientationCalcRunnable);

        return true;
    }

    public void handleOrientationChanged(int deviceOrientation) 
    {
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
            if(BuildConfig.DEBUG) Log.d(TAG, "device orientation change, new rotation = " + roundedOrientation);

            
            adjustUIControls((360 + mLandscapeOrientation - roundedOrientation)%360);

            mCurrDeviceOrientation = roundedOrientation;

      		int rotation;
            if(mNaturalOrientation == Configuration.ORIENTATION_PORTRAIT)
            {
                if (mCameraFacing == Camera.CameraInfo.CAMERA_FACING_BACK) 
                {
       		        rotation = 360 - (mCameraOrientation + roundedOrientation)%360;
                }
                else
                {
       		        rotation = 360 - (360 + mCameraOrientation - roundedOrientation)%360;
                }
            }
            else
            {
                if (mCameraFacing == Camera.CameraInfo.CAMERA_FACING_BACK) 
                {
       		        rotation = 360-roundedOrientation;
                }
                else
                {
       		        rotation = roundedOrientation;
                }
            }
                    
            /*
             * rotation could be 360 from above, convert it to 0.
             */
            rotation %= 360;

            /*
             * now tell renderer
             */
            final int fRotation = rotation;
            mGLView.queueEvent(new Runnable() 
            {
                @Override public void run() 
                {
                    mRenderer.setCameraPreviewSize(mQualityPref, fRotation);
                }
            });
        }
    }

    /*
    public int getRelativeLeft(View myView) {
        if (myView.getParent() == myView.getRootView())
            return myView.getLeft();
        else
            return myView.getLeft() + getRelativeLeft((View) myView.getParent());
    }

    public int getRelativeTop(View myView) {
        if (myView.getParent() == myView.getRootView())
            return myView.getTop();
        else
            return myView.getTop() + getRelativeTop((View) myView.getParent());
    }

    public static void moveViewToBack(View currentView) 
    {
        ViewGroup viewGroup = ((ViewGroup) currentView.getParent());
        int index = viewGroup.indexOfChild(currentView);
        for(int i = 0; i<index; i++) 
        {
            viewGroup.bringChildToFront(viewGroup.getChildAt(i));
        }
    }
    */

    private void adjustUIControls(int rotation)
    {
		RelativeLayout.LayoutParams layoutParams = 
		    (RelativeLayout.LayoutParams)mShutterLayout.getLayoutParams();
		layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
		layoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, 0);
		layoutParams.addRule(RelativeLayout.CENTER_VERTICAL, RelativeLayout.TRUE);
		mShutterLayout.setLayoutParams(layoutParams);
        mShutterLayout.setRotation(rotation);

        layoutParams = (RelativeLayout.LayoutParams)mPvButton.getLayoutParams();
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, 0);
        layoutParams.addRule(RelativeLayout.ABOVE, 0);
        layoutParams.addRule(RelativeLayout.BELOW, R.id.shutter_layout);
        mPvButton.setLayoutParams(layoutParams);
        mPvButton.setRotation(rotation);

        /*
		layoutParams = (RelativeLayout.LayoutParams)mFbButton.getLayoutParams();
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, 0);
        layoutParams.addRule(RelativeLayout.ABOVE, R.id.shutter_layout);
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
	
		View view3DPb = findViewById(R.id.view3D_progress_bar);
		layoutParams = (RelativeLayout.LayoutParams)view3DPb.getLayoutParams();
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, 0);
		layoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0);
		layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
		view3DPb.setLayoutParams(layoutParams);
		view3DPb.setRotation(rotation);

        layoutParams = (RelativeLayout.LayoutParams)mScanProgView.getLayoutParams();
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 0);
        layoutParams.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE);
        layoutParams.addRule(RelativeLayout.LEFT_OF, 0);
        layoutParams.addRule(RelativeLayout.RIGHT_OF, 0);
        mScanProgView.setLayoutParams(layoutParams);
        mScanProgView.setRotation(rotation);

        layoutParams = (RelativeLayout.LayoutParams)mScanModeButton.getLayoutParams();
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 0);
        layoutParams.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE);
        layoutParams.addRule(RelativeLayout.LEFT_OF, 0);
        layoutParams.addRule(RelativeLayout.RIGHT_OF, 0);
        mScanModeButton.setLayoutParams(layoutParams);
        mScanModeButton.setRotation(rotation);

		layoutParams = (RelativeLayout.LayoutParams)mLevelButton.getLayoutParams();
		layoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
		layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 0);
		layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, 0);
		mLevelButton.setLayoutParams(layoutParams);
		mLevelButton.setRotation(rotation);

	    layoutParams = (RelativeLayout.LayoutParams)mTimeCounter.getLayoutParams();
	    layoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
	    layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 0);
	    layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT, 0);
	    layoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, 0);
	    layoutParams.addRule(RelativeLayout.LEFT_OF, R.id.mode_button);
	    layoutParams.addRule(RelativeLayout.RIGHT_OF, 0);
	    layoutParams.addRule(RelativeLayout.ABOVE, 0);
	    layoutParams.addRule(RelativeLayout.BELOW, 0);
	    mTimeCounter.setLayoutParams(layoutParams);

		if((rotation == 0) || (rotation == 180))
		{
	        mTimeCounter.setTranslationY(0);
		}
		else
		{
	        mTimeCounter.setTranslationY(mTimeCounter.getWidth()/2);
		}
	    mTimeCounter.setRotation(rotation);
		
		CustomToast.setRotation(rotation);
    }

    /**
     * shows deviation from level.
     * @param deviation - in degrees, should be within +/- 45
     */
    private void showDeviationFromLevel(int deviation)
    {
		Button levelButton = (Button) findViewById(R.id.level_button);

		if(Math.abs(deviation) < 3)
		    levelButton.setBackgroundResource(R.drawable.round_button_green);
		else
		    levelButton.setBackgroundResource(R.drawable.round_button_not_level);

		levelButton.setText(Integer.toString(deviation) + "\u00b0");
    }

    /**
     * Stops camera preview, and releases the camera to the system.
     */
    private void releaseCamera() 
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
            mCameraId = -1;
            mCameraOrientation = -1; //fixed for camera
            mCurrDeviceOrientation = -1;
            
            /*
             * do not clear the facing - in case our activity restarted 
             * after being put to background, we want to restart the 
             * camera with same facing as before
             */
            //mCameraFacing = -1;

            mCamParamsSent = false;
            CameraUtils.clearCamParams();

            if(BuildConfig.DEBUG) Log.d(TAG, "releaseCamera -- done");
        } 
    }

    /**
     * report our camera parameters to the other device. 
     */
    private void sendCamParams()
    {
        if(BuildConfig.DEBUG) Log.d(TAG, "sendCamParams");

        if(!mP2pConnected)
        {
            if(BuildConfig.DEBUG) Log.d(TAG, "sendCamParams: P2P not connected");
   	        return;
        }

        if(mCamParamsSent)
        {
            if(BuildConfig.DEBUG) Log.d(TAG, "sendCamParams: camera parameter already sent");
       	    return;
        }

        int[] maxXYResolution = CameraUtils.getMaxXYResolution();
        float[] viewAnglesFocusLen = CameraUtils.getViewAngleFocusLen();

        if(mAimfireService != null)
        {
            mAimfireService.sendStringToPeer(true,
   		        MainConsts.CMD_DEMO_CAMERA_REPORT_PARAMETERS + ":" +
                        mPosPref + ":" +
                        Integer.toString(mQualityPref) + ":" +
                        Integer.toString(mSupportedVideoQualities) + ":" +
                        Integer.toString(maxXYResolution[0]) + ":" +
                        Integer.toString(maxXYResolution[1]) + ":" +
                        Float.toString(viewAnglesFocusLen[0]) + ":" +
                        Float.toString(viewAnglesFocusLen[1]) + ":" +
                        Float.toString(viewAnglesFocusLen[2]));
        }
        else
        {
            if(BuildConfig.DEBUG) Log.e(TAG, "sendCamParams: mAimfireService is null");
            FirebaseCrash.report(new Exception("CamcorderActivity sendCamParams: mAimfireService is null"));
        }

        mCamParamsSent = true;
    }

    /**
     * process the camera parameters received from the other device. 
     * we can estimate the scale factor based on these
     */
 	private void syncCamParams(String remotePosPref, int remoteQualityPref, int remoteQualities,
                               int maxWidth, int maxHeight,
                               float viewAngleX, float viewAngleY, float focusLen)
 	{
        if(BuildConfig.DEBUG) Log.d(TAG, "syncCamParams: remotePosPref=" + remotePosPref
                + ", remoteQualityPref=" + remoteQualityPref);

        if(mPosPref.equals(remotePosPref))
        {
            /*
             * if this and the remote has the same preference for position, then let the
             * initiator take left
             */
            mIsLeft = mIsInitiator;
        }
        else if(mPosPref.equals(MainConsts.CAMERA_POSITION_AUTO))
        {
            /*
             * if we are auto, we let remote decide
             */
            if(remotePosPref.equals(MainConsts.CAMERA_POSITION_LEFT))
            {
                mIsLeft = false;
            }
            else
            {
                mIsLeft = true;
            }
        }
        else
        {
            /*
             * at this point, either the remote is auto (in which case we decide), or the two
             * devices have chosen different position (i.e. no conflict)
             */
            if(mPosPref.equals(MainConsts.CAMERA_POSITION_LEFT))
            {
                mIsLeft = true;
            }
            else
            {
                mIsLeft = false;
            }
        }
        if(BuildConfig.DEBUG) Log.d(TAG, "syncCamParams: mIsInitiator=" + mIsInitiator
                + ", mIsLeft=" + mIsLeft);

        switchLeftRight();

        /*
         * the left camera will decide video quality, but it's subject to what the remote camera
         * can support
         */
        if(mIsLeft)
        {
            if((remoteQualities & (1<<mQualityPref)) == 0)
            {
                /*
                 * remote device doesn't support our preferred quality
                 */
                if((mSupportedVideoQualities & (1<<remoteQualityPref)) != 0)
                {
                    /*
                     * if we support remote preferred quality, then use that
                     */
                    mQualityPref = remoteQualityPref;
                }
                else
                {
                    /*
                     * if we don't support each other's preference, then choose the highest
                     * common quality
                     */
                    int common = mSupportedVideoQualities & remoteQualities;

                    if(common >= (1<<MainConsts.VIDEO_QUALITY_1080P))
                    {
                        mQualityPref = MainConsts.VIDEO_QUALITY_1080P;
                    }
                    else if(common >= (1<<MainConsts.VIDEO_QUALITY_720P))
                    {
                        mQualityPref = MainConsts.VIDEO_QUALITY_720P;
                    }
                    else
                    {
                        mQualityPref = MainConsts.VIDEO_QUALITY_SD_HQ;
                    }
                }
                changeVideoQuality();
            }
        }
        else
        {
            if((mSupportedVideoQualities & (1<<remoteQualityPref)) == 0)
            {
                if((remoteQualities & (1<<mQualityPref)) == 0)
                {
                    /*
                     * if we don't support each other's preference, then choose the highest
                     * common quality
                     */
                    int common = mSupportedVideoQualities & remoteQualities;

                    if(common >= (1<<MainConsts.VIDEO_QUALITY_1080P))
                    {
                        mQualityPref = MainConsts.VIDEO_QUALITY_1080P;
                    }
                    else if(common >= (1<<MainConsts.VIDEO_QUALITY_720P))
                    {
                        mQualityPref = MainConsts.VIDEO_QUALITY_720P;
                    }
                    else
                    {
                        mQualityPref = MainConsts.VIDEO_QUALITY_SD_HQ;
                    }
                    changeVideoQuality();
                }
            }
            else if (mQualityPref != remoteQualityPref)
            {
                mQualityPref = remoteQualityPref;
                changeVideoQuality();
            }
        }
        if(BuildConfig.DEBUG) Log.d(TAG, "syncCamParams: mQualityPref=" + mQualityPref);

        CameraUtils.setRemoteCamParams(maxWidth, maxHeight, 
 			viewAngleX, viewAngleY, focusLen);
 	}

    private void changeVideoQuality()
    {
        AspectFrameLayout afl = (AspectFrameLayout) findViewById(R.id.cameraPreview_frame);
        afl.setAspectRatio((float)MainConsts.VIDEO_DIMENSIONS[mQualityPref][0]/
                (float)MainConsts.VIDEO_DIMENSIONS[mQualityPref][1]);

        mCamera.stopPreview();
        mCameraParams.setPreviewSize(MainConsts.VIDEO_DIMENSIONS[mQualityPref][0],
                MainConsts.VIDEO_DIMENSIONS[mQualityPref][1]);

        mCamera.setParameters(mCameraParams);
        mCamera.startPreview();
    }

    /**
     * onClick handler for "view3D" button.
     */
    OnClickListener oclView3D = new OnClickListener() {
        @Override
        public void onClick(View view) 
        {
   	        if(mCurrThumbLink == null)
   	        {
    	        if(BuildConfig.DEBUG) Log.d(TAG, "clickView3D: mCurrThumbLink is null.");
   	    	        return;
   	        }

            /*
             * if we are in the middle of searching/connecting, end it
             */
            if(!mInSync)
            {
                end3dAttempt(-1);
            }

   	        /*
   	         * note here we don't use FLAG_ACTIVITY_CLEAR_TOP because doing so
   	         * will kill camcorder
   	         */
            //Intent intent = new Intent(getActivity(), GalleryActivity.class);
            Intent intent = new Intent(getActivity(), MovieActivity.class);
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


    /**
     * onClick handler for "switch photo/video" button.
     */
    OnClickListener oclPV = new OnClickListener() {
        @Override
        public void onClick(View view) 
        {
	        if(mInSync)
            {
                if (mRemoteCameraPaused)
                {
                    //Toast.makeText(getActivity(), "Remote Camera not active, cannot " +
                    //"switch photo/video mode", Toast.LENGTH_LONG).show();
                    CustomToast.show(getActivity(),
                            getActivity().getString(R.string.error_cannot_switch_photo_video),
                            Toast.LENGTH_LONG);
                    return;
                }

                if((mPendingTasks.size() > 0) || (mMovieEncoder.isRecording()))
                {
                    /*
                     * we are either encoding (which means we haven't started file transfer yet but will),
                     * or we have at least one pending task (which means we haven't received file from
                     * remote device yet)
                     */
                    CustomToast.show(getActivity(),
                            getActivity().getString(R.string.error_cannot_switch_photo_video2),
                            Toast.LENGTH_LONG);
                    return;
                }

	            /*
	             * inform the remote device that we are switching to photo mode
	             */
                if (mAimfireService == null)
                {
                    /*
                     * sanity check
                     */
                    return;
                }
                else
                {
                    mAimfireService.sendStringToPeer(true,
                            MainConsts.CMD_DEMO_CAMERA_ACTION_SWITCH_PHOTO_VIDEO + ":" + CAMERA_MODE_IMAGE);
                }
            }
	    
	        /*
	         * switching ourselves
	         */
	        switchPhotoVideo(CAMERA_MODE_IMAGE);
        }
    };

    public void switchPhotoVideo(int newMode)
    {
	    if(newMode == CAMERA_MODE_VIDEO)
	    {
    	    /*
    	     * something wrong here - we are already in video mode. ignore
    	     */
    	    return;
	    }

	    /*
	     * we stop the camera here (which means release camera and destroy
	     * preview surface), because we want the camera to be released 
	     * right away, instead of be done in onStop. The problem with the
	     * latter is that when Camera activity is launched and is 
	     * opening camera, onStop of this activity may not have been called
	     */
	    stopCamera();

   	    /*
   	     * switch mode ourselves
   	     */
        Intent intent = new Intent(this, CameraActivity.class);
        intent.putExtra(MainConsts.EXTRA_ISLEFT, mIsLeft);
        intent.putExtra(MainConsts.EXTRA_SYNCTIME, mSyncTimeUs);
        startActivity(intent);
        
        mSwitchPhotoVideo = true;
    }

    public void switchLeftRight()
    {
        if (!mIsLeft)
        {
            mShutterLayout.setVisibility(View.INVISIBLE);
            mPvButton.setVisibility(View.INVISIBLE);
            //mFbButton.setVisibility(View.INVISIBLE);
        }
        else
        {
            mShutterLayout.setVisibility(View.VISIBLE);
            mPvButton.setVisibility(View.VISIBLE);
            //mFbButton.setVisibility(View.VISIBLE);
        }

        if (!mPvSwitching)
        {
            showHint3D();

            if (!mIsLeft)
            {
                /*
                 * lock the screen of phone stacked bottom (right camera)
                 *
                 * on Lollipop and later, the hardware menu key is replaced by
                 * multitasking key. it's easily pressed when devices are stacked.
                 * there's no way to capture this key press in our app, so the
                 * alternative here is to use the pinning api to pin this app
                 */
                if (Build.VERSION.SDK_INT >= 21)
                {
                    startLockTask();
                }
            }
        }

        /*
         * file name prefix for 3d mode. rest of the file name (time diff and index) are
         * added when recording starts.
         */
        mMpegPrefixSync = MainConsts.MEDIA_3D_RAW_PATH
                + "MPG_" + ((mIsLeft) ? "left_" : "right_")
                + mAimfireService.getHexRefCode();
    }

    private void stopCamera()
    {
        if(mOrientationEventListener != null)
       	    mOrientationEventListener.disable();

        releaseCamera();

        /*
         * comment out code that notifies the renderer to release
         * SurfaceTexture, because we want to preserve the EGL context
         */
        //mGLView.queueEvent(new Runnable() 
        //{
            //@Override public void run() 
            //{
                //// Tell the renderer that it's about to be paused so it can clean up.
                //mRenderer.notifyPausing();
            //}
        //});

        /*
         * according to Android documentation, "If set to true, then the EGL 
         * context *may* be preserved when the GLSurfaceView is paused. 
         * Whether the EGL context is actually preserved or not depends upon 
         * whether the Android device that the program is running on can 
         * support an arbitrary number of EGL contexts or not. Devices that 
         * can only support a limited number of EGL contexts must release the 
         * EGL context in order to allow multiple applications to share the 
         * GPU". more details see "http://stackoverflow.com/questions/2112768/
         * prevent-onpause-from-trashing-opengl-context/11167948#11167948"
         */
        mGLView.setPreserveEGLContextOnPause(true);

        mGLView.onPause();
        
        /*
         * stop recording if we are put in background or on the way to get destroyed
         */
        if(mMovieEncoder.isRecording())
            mMovieEncoder.stopRecording();
    }
    
    /**
     * onClick handler for "switch front/back" button.
     */
    OnClickListener oclFB = new OnClickListener() {
        @Override
        public void onClick(View view) 
        {
            int newFacing = -1;

            if (mCameraFacing == Camera.CameraInfo.CAMERA_FACING_FRONT)
            {
                newFacing = Camera.CameraInfo.CAMERA_FACING_BACK;
            }
            else
            {
                newFacing = Camera.CameraInfo.CAMERA_FACING_FRONT;
            }

	        if(mInSync)
            {
                if (mRemoteCameraPaused)
                {
                    //Toast.makeText(getActivity(), "Remote Camera not active, cannot " +
                    //"switch front/back mode", Toast.LENGTH_LONG).show();
                    CustomToast.show(getActivity(),
                            getActivity().getString(R.string.error_cannot_switch_front_back),
                            Toast.LENGTH_LONG);
                    return;
                }

	            /*
	             * inform the remote device that we are switching to a different facing
	             */
                if (mAimfireService == null)
                {
                    /*
                     * sanity check
                     */
                    return;
                }
                else
                {
                    mAimfireService.sendStringToPeer(true,
                            MainConsts.CMD_DEMO_CAMERA_ACTION_SWITCH_FRONT_BACK + ":" + newFacing);
                }
            }
	    
	        /*
	         * switching ourselves
	         */
	        switchFrontBack(newFacing);
        }
    };
    
    public void switchFrontBack(int newFacing)
    {
	    if(newFacing == mCameraFacing)
	    {
    	    /*
    	     * something wrong here - we are already facing the correct way.
    	     * ignore
    	     */
    	    return;
	    }

        if(newFacing == Camera.CameraInfo.CAMERA_FACING_BACK)
        {
            mFbButton.setImageResource(R.drawable.ic_camera_front_black_24dp);
        }
        else
        {
            mFbButton.setImageResource(R.drawable.ic_camera_rear_black_24dp);
        }

        /*
         * release whichever camera that's currently in use
         */
        releaseCamera();
        
        switchCamera(newFacing);
    }

    public void switchCamera(int newFacing)
    {
        /*
         * open new camera with desired facing. note if this device has only
         * one camera, the call below would happily reopen the same camera 
         * without complaining 
         */
        boolean success = openCamera(newFacing, mQualityPref);
        if(!success)
        {
       	    finish();
       	    return;
        }

        /*
         * we reuse the surface texture created before...
         */
        try {
            mCamera.setPreviewTexture(mCameraSurfaceTexture);
        } catch (IOException e) {
            if (BuildConfig.DEBUG) Log.e(TAG, "switchCamera: " + e.getMessage());
            FirebaseCrash.report(e);
        }
        mCamera.startPreview();

        /*
         * user may have kept the device perfectly still during switching,
         * so our orientation listener may not be invoked. in this case,
         * we need to force an update (so that new camera's facing can be
         * taken into account in determining the correct rotation for 
         * recording)
         */
        //int deviceOrientation = mCurrDeviceOrientation;
        //mCurrDeviceOrientation = -1;
        //handleOrientationChanged(deviceOrientation);
    }

    /**
     * onClick handler for "record" button.
     */
    OnClickListener oclToggle = new OnClickListener() {
        @Override
        public void onClick(View view) 
        {
            if(!mInSync)
            {
                end3dAttempt(-1);
            }

       	    clickToggleRecording(view);
        }
    };

    /**
     * onClick handler for "scan" progress view.
     */
    OnClickListener oclScan = new OnClickListener() {
        @Override
        public void onClick(View view)
        {
            end3dAttempt(R.string.warning_search_interrupted);
        }
    };

    public void end3dAttempt(int msg)
    {
        if(BuildConfig.DEBUG) Log.d(TAG, "end3dAttempt");
        if(mAimfireService != null)
        {
            mAimfireService.endDemo();
        }
        stopScanAnim();

        if(msg != -1)
        {
            for (int i = 0; i < 2; i++)
            {
                Toast.makeText(getActivity(), msg, Toast.LENGTH_LONG).show();
            }
        }
    }

    public void clickToggleRecording(View unused)
    {
   	    if(!mRecordingEnabled)
   	    {
	        if(mMovieEncoder.isRecording())
	        {
        	    /*
        	     * we are still encoding last video. this should only happen
        	     * if user toggle recording button very quickly
        	     */
    	        return;
	        }

            if(mInSync)
            {
                syncStartRecording();
            }
            else
            {
                soloStartRecording();
            }
   	    }
   	    else
   	    {
            if(mInSync)
            {
                syncStopRecording();
            }
            else
            {
                soloStopRecording();
            }
   	    }
    }

    /**
     * start solo (2D) recording
     */
    private void soloStartRecording()
    {
        long startUs = SystemClock.elapsedRealtimeNanos()/1000;
        setRecordingStart(startUs);
    }

    /**
     * start our recording and send command to remote device to start
     */
    private void syncStartRecording()
    {
   	    /*
   	     * user wanted to start recording. we calculate the time
   	     * elapsed between current time and mSyncTimeUs, then add
   	     * an extra delay which accounts for the P2P latency in
   	     * sending the command to the remote device
   	     */
        long startUs = SystemClock.elapsedRealtimeNanos()/1000 + P2P_LATENCY_US;

        long delayFromSyncUs = startUs - mSyncTimeUs;
        if(BuildConfig.DEBUG) Log.d(TAG, "DELAY_FROM_SYNC: " + delayFromSyncUs);


        if(mRemoteCameraPaused)
        {
            //Toast.makeText(getActivity(), "Remote Camera not active, cannot " +
            //"capture video", Toast.LENGTH_LONG).show();
            CustomToast.show(getActivity(),
                    getActivity().getString(R.string.error_cannot_capture_video),
                    Toast.LENGTH_LONG);
            return;
        }

        /*
         * tell remote device to start recording
         */
        if(mAimfireService == null)
        {
            /*
             * sanity check
             */
            return;
        }
        else
        {
            mAimfireService.sendStringToPeer(true,
                    MainConsts.CMD_DEMO_CAMERA_ACTION_START + ":" + Long.toString(delayFromSyncUs));
        }

        setRecordingStart(startUs);
    }

    /**
     * stop solo (2D) recording
     */
    private void soloStopRecording()
    {
        long stopUs = SystemClock.elapsedRealtimeNanos()/1000;
        setRecordingStop(stopUs);
    }

    /**
     * stop our recording and send command to remote device to stop
     */
    private void syncStopRecording()
    {
   	    /*
   	     * stop our side of the recording. set it a little bit in
   	     * the future to account for the delay in sending the
   	     * command to the remote device. this will not be perfect -
   	     * the length of the two recording will always be slightly
   	     * different - but at least we tried.
   	     */
        long stopUs = SystemClock.elapsedRealtimeNanos()/1000 + P2P_LATENCY_US;
        setRecordingStop(stopUs);

        /*
         * audio/visual indication of stop is done in handleRecordingStop instead
         * of here because we don't want to record the stop shutter sound.
         */

        /*
         * tell the remote device to stop, too. we do not have to check
         * if the remote camera was put to background or not, because if 
         * it did, it must have sent us a message, and we must have
         * stopped already and wouldn't get here
         * 
         */
        if(mAimfireService != null)
        {
            mAimfireService.sendStringToPeer(true,
       		    Integer.toString(MainConsts.CMD_DEMO_CAMERA_ACTION_END));
        }
    }

    /**
     * onClick handler for "2D" mode button.
     */
    OnClickListener oclSwitchMode = new OnClickListener() {
        @Override
        public void onClick(View view)
        {
            if(!mInSync)
            {
                mAimfireService.initDemo(true, ActivityCode.CAMCORDER.getValue(), null);
                startScanAnim();
            }
        }
    };

    /*
     * cf. "http://stackoverflow.com/questions/2364892/how-to-play-native-
     * camera-sound-on-android", verified Cam_Start/Stop.ogg exists on GS4/6
     */
    public void playShutterSound(boolean isStartSound)
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

        if (isStartSound && mCamStartSoundPlayer != null)
        {
            mCamStartSoundPlayer.start();
        }
        else if (!isStartSound && mCamStopSoundPlayer != null)
        {
            mCamStopSoundPlayer.start();
        }
    }

    private void loadCurrThumbnail()
    {
        ArrayList<String> list = MediaScanner.getNonEmptyMovieList(MainConsts.MEDIA_3D_SAVE_DIR);
        if(list.size()==0)
        {
            mCurrThumbLink = null;
            mView3DButton.setImageResource(R.drawable.ic_local_florist_black_24dp);
       	    return;
        }

        String moviePath = list.get(0);
        mCurrThumbLink = moviePath;
        
        String thumbPath = MainConsts.MEDIA_3D_THUMB_PATH + 
       		MediaScanner.getMovieNameNoExt(moviePath) + ".jpg";

        if(BuildConfig.DEBUG) Log.d(TAG, "loadCurrThumbnail: path=" + thumbPath);
        
        if(!(new File(thumbPath).exists()) ||
            MediaScanner.isEmpty(thumbPath))
        {
            if(MediaScanner.is2dMovie(moviePath))
            {
                generateThumbAndPreview(moviePath);
            }
            else
            {
                /*
                 * unzip the cvr file if we don't have it already cached
                 */
                try {
                    ZipUtil.unzip(moviePath, MainConsts.MEDIA_3D_ROOT_PATH, false);
                } catch (IOException e) {
                    if (BuildConfig.DEBUG) Log.e(TAG, "onCreate: error unzipping " + e);
                    FirebaseCrash.report(e);
                }
            }
        }

        Bitmap currThumb = BitmapFactory.decodeFile(thumbPath);
        if(currThumb != null)
        {
            mView3DButton.setImageBitmap(currThumb);
        }
        else
        {
	        if(BuildConfig.DEBUG) Log.e(TAG, "loadCurrThumbnail: thumbnail is null! " +
        		"thumb path = " + thumbPath);
            FirebaseCrash.report(new Exception("CamcorderActivity loadCurrThumbnail: thumbnail is null"));
        }
    }

    private void generateThumbAndPreview(String filePath)
    {
        if (BuildConfig.DEBUG) Log.d(TAG, "generateThumbAndPreview");

        String movieNameNoExt = MediaScanner.getMovieNameNoExt(filePath);
        String previewPath = MainConsts.MEDIA_3D_THUMB_PATH + movieNameNoExt + ".jpeg";
        String thumbPath = MainConsts.MEDIA_3D_THUMB_PATH + movieNameNoExt + ".jpg";

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();

        Bitmap bitmap = null;

        try {
            FileInputStream inputStream = new FileInputStream(filePath);
            retriever.setDataSource(inputStream.getFD());
            inputStream.close();

            bitmap = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            retriever.release();

            if (bitmap != null)
            {
                FileOutputStream out = null;
                out = new FileOutputStream(previewPath);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out);
                out.close();

                Bitmap thumbnail = ThumbnailUtils.extractThumbnail(bitmap,
                        MainConsts.THUMBNAIL_SIZE, MainConsts.THUMBNAIL_SIZE);
                out = new FileOutputStream(thumbPath);
                thumbnail.compress(Bitmap.CompressFormat.JPEG, 100, out);
                out.close();
            }
        } catch (Exception e) {
            if(BuildConfig.DEBUG) Log.e(TAG, "generateThumbAndPreview: exception" + e.getMessage());
            retriever.release();
            FirebaseCrash.report(e);
        }
    }

    /**
     * recording has been initiated. disable control and view3D buttons
     */
    @SuppressWarnings("unused")
	private void setControlRecordingOn() 
    {
        /*
         * disable controls until capture is finished
         */
        mPvButton.setEnabled(false);
        //mFbButton.setEnabled(false);
        mView3DButton.setEnabled(false);

        if(MainConsts.VIDEO_LENGTH_SECONDS == -1)
        {
      	    /*
       	     * display time counter only if indeterminate recording
       	     */
            mTimeCounter.setVisibility(View.VISIBLE);
        }
    }

    /**
     * recording has been stopped. re-enable control and view3D buttons
     */
	@SuppressWarnings("unused")
	private void setControlRecordingOff() 
    {
        /*
         * capture is finished, re-enable buttons
         */
        mPvButton.setEnabled(true);
        //mFbButton.setEnabled(true);

        /*
         * view3D button not enabled until processing of new video is done
         */
        mView3DButton.setEnabled(false); 

        ProgressBar pb = (ProgressBar) findViewById(R.id.view3D_progress_bar);
        pb.setVisibility(View.VISIBLE);

        if(MainConsts.VIDEO_LENGTH_SECONDS == -1)
        {
       	    /*
       	     * display time counter only if indeterminate recording
       	     */
            mTimeCounter.setVisibility(View.INVISIBLE);
        }
    }

    private void handleRecordingTime(final int timeCounterSeconds) 
    {
   	    int hours = timeCounterSeconds/3600;
   	    String hourString = (hours >= 10)?Integer.toString(hours):("0"+Integer.toString(hours));
   	    int minutes = (timeCounterSeconds - hours*3600)/60;
   	    String minuteString = (minutes >= 10)?Integer.toString(minutes):("0"+Integer.toString(minutes));
   	    int seconds = timeCounterSeconds - hours*3600 - minutes*60;
   	    String secondString = (seconds >= 10)?Integer.toString(seconds):("0"+Integer.toString(seconds));
    	    	    
   	    String timeCounterString = hourString + ":" + minuteString + ":" + secondString;

	    TextView tv = (TextView)findViewById(R.id.counter);
	    tv.setText(timeCounterString);

	    Button button = (Button)findViewById(R.id.red_dot);
	    if(seconds%2 == 0)
	    {
	        button.setBackgroundResource(R.drawable.round_button_red);
	    }
	    else
	    {	
	        button.setBackground(null);
	    }
    }

    private void handleRecordingStart(final String filepath/*unused*/) 
    {
        if(!mInSync || mIsLeft)
        {
            /*
             * add audio/visual indication
             */
            playShutterSound(true);
            mCaptureButton.setImageResource(R.drawable.ic_stop_black_24dp);

            /*
             * starting the circular progress bar around shutter button
             */
            startRecordingAnim(MainConsts.VIDEO_LENGTH_SECONDS);
        }
    }

    private void handleRecordingStop(final String filepath) 
    {
   	    mMpegFilepath = filepath;

        if(!mInSync || mIsLeft)
        {
            /*
             * add audio/visual indication. shutter sound is delayed to avoid recording it
             */
            final Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    playShutterSound(false);
                }
            }, 500);
            mCaptureButton.setImageResource(R.drawable.ic_videocam_black_24dp);

            stopRecordingAnim();
        }
    }

    /*
     * send recorded video clip to the remote device
     */
    Runnable SendFileTask = new Runnable() {
        public void run() 
        {
            /*
             * when we started recording, we give it a temporary name in the 
             * format of mMpegPrefixSync + _ + index.mp4. this is so that
             * we can start a encoder thread before exact offset is determined.
             * now we rename the temporary file to mMpegPrefixSync + "_" +
             * offset + "_" + index.mp4.
             */
   	        String tempFilepath = mMpegPrefixSync + "_" + Integer.toString(mMpegIndex) + ".mp4";
   	        File from = new File(tempFilepath);
    	        
   	        File to = new File(mMpegFilepath);
   	        from.renameTo(to);

   	        /*
   	         * now send it to remote device
   	         */
            if(BuildConfig.DEBUG) Log.d(TAG, "handleRecordingStop: recording done, sending file " + 
                mMpegFilepath + " to remote device");
            mAimfireService.sendFileToPeer(mMpegFilepath);
            
            /*
             * add it to the queue to be processed by MovieProcessor
             */
            queueMovieProcessor(true/*isLocal*/, to.getName());
        }
    };

    /**
     * Connects the SurfaceTexture to the Camera preview output, and starts the preview.
     */
    private void handleSetSurfaceTexture(SurfaceTexture st) 
    {
   	    /*
   	     * when we restart camera after pausing, we tried to use preserved
   	     * EGL context by setPreserveEGLContextOnPause(true). however this
   	     * *may* fail on some devices that don't support multiple EGL
   	     * contexts (haven't come across any but according to documentation
   	     * this is possible). if that's the case, a new EGL context will be
   	     * created and we will get here. so to deal with it, we need first
   	     * stopPreview (as we tried startPreview on the old, potentially
   	     * invalid SurfaceTexture). when startPreview was not called (for
   	     * all the other reasons we get here), stopPreview has no negative
   	     * effect
   	     */
   	    mCamera.stopPreview();

   	    mCameraSurfaceTexture = st;
        st.setOnFrameAvailableListener(this);
        try {
            mCamera.setPreviewTexture(st);
            mCamera.startPreview();
        } catch (IOException e) {
            //throw new RuntimeException(e);
      	    if(BuildConfig.DEBUG) Log.e(TAG, "handleSetSurfaceTexture: cannot setPreviewTexture, surface destroyed");
            FirebaseCrash.report(e);
        }
    }

    /**
     * lock camera auto exposure and white balance prior to capture,
     * unlock them after capture
     */
    public void setWBAELock(boolean enable) 
    {
        Camera.Parameters mParams = mCamera.getParameters();

	    /*
	     * debug:
	     */
        if(enable)
        {
	        /*
	         * set exposure compensation to the min. can this boost frame rate?
	         */
	        mParams.setExposureCompensation(mParams.getMinExposureCompensation());
        }
        else
        {
	        /*
	         * set exposure compensation to 0 - no adjustment
	         */
	        mParams.setExposureCompensation(0);
        }

        /*
         *  check if lock/unlock white balance and auto exposure supported
         */
        boolean aeSupported = mParams.isAutoExposureLockSupported();
        boolean wbSupported = mParams.isAutoWhiteBalanceLockSupported();
    
        if(aeSupported)
        {
            mParams.setAutoExposureLock(enable);
        }
    
        if(wbSupported)
        {
            mParams.setAutoWhiteBalanceLock(enable);
        }
    
        mCamera.setParameters(mParams);
        
        if(aeSupported && mParams.getAutoExposureLock())
        {
            if(BuildConfig.DEBUG) Log.d(TAG, "Auto Exposure locked");
        }
        else
        {
            if(BuildConfig.DEBUG) Log.d(TAG, "Auto Exposure unlocked");
        }
    
        if(wbSupported && mParams.getAutoWhiteBalanceLock())
        {
            if(BuildConfig.DEBUG) Log.d(TAG, "White Balance locked");
        }
        else
        {
            if(BuildConfig.DEBUG) Log.d(TAG, "White Balance unlocked");
        }
    }

    public void setRecordingStart(final long startUs)
    {
        if(mInSync)
        {
            mFirebaseAnalytics.logEvent(MainConsts.FIREBASE_CUSTOM_EVENT_SYNC_MOVIE_CAPTURE_START, null);
        }
        else
        {
            mFirebaseAnalytics.logEvent(MainConsts.FIREBASE_CUSTOM_EVENT_SOLO_MOVIE_CAPTURE_START, null);
        }

        mRecordingEnabled = true;
    
   	    /*
   	     * even though recording hasn't really started, it is initiated,
   	     * disable control and view3D buttons
   	     */
        setControlRecordingOn();

        mGLView.queueEvent(new Runnable() 
        {
            @Override public void run() 
            {
                /*
                 * notify the renderer that we want to change the encoder's state
                 */
                mRenderer.setRecordingStartTime(startUs, mInSync, mInSync?mMpegPrefixSync:mMpegPrefixSolo, ++mMpegIndex);
            }
        });
    }

    public void setRecordingStop(final long stopUs) 
    {
        if(mInSync)
        {
            mFirebaseAnalytics.logEvent(MainConsts.FIREBASE_CUSTOM_EVENT_SYNC_MOVIE_CAPTURE_STOP, null);
        }
        else
        {
            mFirebaseAnalytics.logEvent(MainConsts.FIREBASE_CUSTOM_EVENT_SOLO_MOVIE_CAPTURE_STOP, null);
        }

        mRecordingEnabled = false;

        /*
         * re-enable control and view3D buttons
         */
        setControlRecordingOff();

        mGLView.queueEvent(new Runnable() 
        {
            @Override public void run() 
            {
                /*
                 * notify the renderer that we want to change the encoder's state
                 */
                mRenderer.setRecordingStopTime(stopUs);
            }
        });
    }

    @Override
    public void onFrameAvailable(SurfaceTexture st) 
    {
        if(BuildConfig.DEBUG) if (VERBOSE) Log.d(TAG, "onFrameAvailable");

        mGLView.requestRender();
    }

    static class CameraHandler extends Handler
    {
        public static final int MSG_SET_SURFACE_TEXTURE = 0;
        public static final int MSG_SET_RECORDING_START = 1;
        public static final int MSG_SET_RECORDING_STOP = 2;
        public static final int MSG_SET_RECORDING_TIME = 3;

        /*
         * Weak reference to the Activity; only access this from the UI thread.
         */
        private WeakReference<CamcorderActivity> mWeakActivity;

        public CameraHandler(CamcorderActivity activity) 
        {
            mWeakActivity = new WeakReference<CamcorderActivity>(activity);
        }

        /**
         * Drop the reference to the activity.  Useful as a paranoid measure to ensure that
         * attempts to access a stale Activity through a handler are caught.
         */
        public void invalidateHandler() 
        {
            mWeakActivity.clear();
        }

        @Override  // runs on UI thread
        public void handleMessage(Message inputMessage) 
        {
            int what = inputMessage.what;
            if(BuildConfig.DEBUG) Log.d(TAG, "CameraHandler [" + this + "]: what=" + what);

            CamcorderActivity activity = mWeakActivity.get();
            if (activity == null) 
            {
                if(BuildConfig.DEBUG) Log.w(TAG, "CameraHandler.handleMessage: activity is null");
                return;
            }

            switch (what) 
            {
                case MSG_SET_SURFACE_TEXTURE:
                    activity.handleSetSurfaceTexture((SurfaceTexture) inputMessage.obj);
                    break;
                case MSG_SET_RECORDING_START:
                    activity.handleRecordingStart((String) inputMessage.obj);
                    break;
                case MSG_SET_RECORDING_STOP:
                    activity.handleRecordingStop((String) inputMessage.obj);
                    break;
                case MSG_SET_RECORDING_TIME:
                    activity.handleRecordingTime((Integer) inputMessage.obj);
                    break;
                default:
                    throw new RuntimeException("unknown msg " + what);
            }
        }
    }
    
    private void queueMovieProcessor(boolean isLocal, String filename)
    {
   	    ProcessFileTask foundTask = null;

   	    /*
   	     * find out if there is a matching ProcessFileTask already in our queue
   	     */
   	    for(ProcessFileTask t: mPendingTasks)
   	    {
    	    if(t.isPair(isLocal, filename))
    	    {
                (new Thread(t)).start();
                foundTask = t;
                break;
    	    }
  	    }
    	    
   	    if(foundTask != null)
   	    {
            mPendingTasks.remove(foundTask);
            return;
   	    }
   	    else
   	    {
   	        /*
   	         * if not, create a new ProcessFileTask
   	         */
	        ProcessFileTask newTask = new ProcessFileTask(isLocal, filename);
	        mPendingTasks.add(newTask);
   	    }
    }

    private class ProcessFileTask implements Runnable
    {
   	    private String localFilename = null;
   	    private String remoteFilename = null;
   	    private int cameraFacing;

   	    public ProcessFileTask(boolean isLocal, String filename)
   	    {
    	    if(isLocal)
    	    {
  	    	    localFilename = filename;
    	    }
    	    else
    	    {
    	        remoteFilename = filename;
    	    }
    	    	    
    	    /*
    	     * remember the facing as it can change when we execute this task
    	     */
    	    cameraFacing = mCameraFacing;
  	    }

   	    public boolean isPair(boolean isLocal, String filename)
   	    {
    	    boolean retVal = false;
    	    if(isLocal && (remoteFilename != null))
  	    	{
   	    	    int indL = indexOf(filename);
   	    	    int indR = indexOf(remoteFilename);
  	    	    if((indL != -1) && (indL == indR))
   	    	    {
    	    	    localFilename = filename;
    	    	    retVal = true;
  	    	    }
  	    	}
    	    else if(!isLocal && (localFilename != null))
    	    {
   	    	    int indR = indexOf(filename);
  	    	    int indL = indexOf(localFilename);
  	    	    if((indL != -1) && (indL == indR))
   	    	    {
    	    	    remoteFilename = filename;
    	    	    retVal = true;
  	    	    }
    	    }
    	    return retVal;
  	    }

   	    private int indexOf(String filename)
   	    {
	        String [] tmp1 = filename.split("_");
	        if(tmp1.length > 4)
	        {
	            String [] tmp2 = tmp1[4].split("\\.");
    	        try {
	                return (Integer.parseInt(tmp2[0]));
			    } catch (NumberFormatException e) {
                    if(BuildConfig.DEBUG) Log.d(TAG, "ProcessFileTask: isPair number format exception");
                    FirebaseCrash.report(e);
				    return -1;
			    }
	        }
	        else
	        {
    	        if(BuildConfig.DEBUG) Log.e(TAG, "ProcessFileTask: isPair file name is invalid");
                FirebaseCrash.report(new Exception("CamcorderActivity ProcessFileTask: isPair file name is invalid"));
    	        return -1;
	        }
   	    }

        public void run() 
        {
    	    int timeOffsetMs = MediaScanner.getMpgTimeOffsetMs(localFilename, remoteFilename);

    	    if(BuildConfig.DEBUG) Log.d(TAG, "ProcessFileTask: local filename = " + localFilename +
  	    		", remote filename = " + remoteFilename + ", timeOffsetMs = " + timeOffsetMs);

            /*
             * send to video processor service for processing. we always give 
             * the native function scale multiplier for left side. if this 
             * device is the right camera, then we need to convert.
             */
            float scale = CameraUtils.getScale();
   	        if(BuildConfig.DEBUG) Log.d(TAG, "ProcessFileTask: getScale - mIsLeft = " + (mIsLeft?"true":"false") +
    		    ", scale multiplier = " + scale);

   	        String localFilepath = MainConsts.MEDIA_3D_RAW_PATH + localFilename;
   	        String remoteFilepath = MainConsts.MEDIA_3D_RAW_PATH + remoteFilename;
            Intent serviceIntent = new Intent(getActivity(), MovieProcessor.class);
            if(mIsLeft)
            {
                serviceIntent.putExtra("lname", localFilepath);
                serviceIntent.putExtra("rname", remoteFilepath);
            }
            else
            {
                serviceIntent.putExtra("lname", remoteFilepath);
                serviceIntent.putExtra("rname", localFilepath);
            }

            serviceIntent.putExtra("creator", mCreatorName);
            serviceIntent.putExtra("photo", mCreatorPhotoUrl);

            serviceIntent.putExtra(MainConsts.EXTRA_SCALE, mIsLeft?scale:1.0f/scale);
			serviceIntent.putExtra(MainConsts.EXTRA_FACING, cameraFacing);
			serviceIntent.putExtra(MainConsts.EXTRA_OFFSET, timeOffsetMs);
            getActivity().startService(serviceIntent);
            
            /*
             * create an empty, placeholder file, so GalleryActivity or ThumbsFragment can 
             * show a progress bar while this file is generated by MovieProcessor
             */
            try {
				String path = MediaScanner.getProcessedCvrPath(localFilename);
				MediaScanner.addItemMediaList(path);
				(new File(path)).createNewFile();
			} catch (IOException e) {
   	            if(BuildConfig.DEBUG) Log.e(TAG, "ProcessFileTask: error creating placeholder file");
                FirebaseCrash.report(e);
			}
        }
    }
}

class CameraSurfaceRenderer implements GLSurfaceView.Renderer
{
    private static final String TAG = "CameraSurfaceRenderer";
    private static final boolean VERBOSE = false;

    private static final int FILTER_NONE = 0;
    private static final int FILTER_BLACK_WHITE = 1;
    private static final int FILTER_BLUR = 2;
    private static final int FILTER_SHARPEN = 3;
    private static final int FILTER_EDGE_DETECT = 4;
    private static final int FILTER_EMBOSS = 5;

    private CamcorderActivity.CameraHandler mCameraHandler;
    private TextureMovieEncoder mEncoder;

    private FullFrameRect mFullScreen;

    private final float[] mSTMatrix = new float[16];
    private int mTextureId;

    private SurfaceTexture mSurfaceTexture;
    private boolean mEncoderThreadStarted; //whether encoder has been started (and waiting for frames)
    private boolean mIsRecording; //whether we are currently recording

	private long mRecordingStartUs = Long.MAX_VALUE;
	private long mRecordingStopUs = Long.MAX_VALUE;
    private long mLastTimeMs = 0;
    private int mNumOfFrameDelays = 0;
    private float mAvgFrameDelayMs = 0;

    private int mTimeCounterSeconds;

    private boolean mSyncCapture = false;

    private String mOutputFilepath = null;
    private String mPrefix = null;
    private int mIndex = 0;

    // width/height of the incoming camera preview frames
    private boolean mIncomingSizeUpdated;
    private int mIncomingWidth;
    private int mIncomingHeight;
    private int mIncomingBitrate;
    private int mIncomingRotation;

    private int mCurrentFilter;
    private int mNewFilter;


    public CameraSurfaceRenderer(CamcorderActivity.CameraHandler cameraHandler,
            TextureMovieEncoder movieEncoder) 
    {
        mCameraHandler = cameraHandler;
        mEncoder = movieEncoder;

        mTextureId = -1;

        mEncoderThreadStarted = false;
        mIsRecording = false;

        mIncomingSizeUpdated = false;
        mIncomingWidth = mIncomingHeight = -1;
        mIncomingBitrate = -1;
        mIncomingRotation = 0;

        /*
         *  We could preserve the old filter mode, but currently not bothering.
         */
        mCurrentFilter = -1;
        mNewFilter = FILTER_NONE;
    }

    public void notifyPausing()
    {
        if (mSurfaceTexture != null) 
        {
            if(BuildConfig.DEBUG) Log.d(TAG, "renderer pausing -- releasing SurfaceTexture");
            mSurfaceTexture.release();
            mSurfaceTexture = null;
        }
        if (mFullScreen != null) 
        {
            mFullScreen.release(false);     // assume the GLSurfaceView EGL context is about
            mFullScreen = null;             //  to be destroyed
        }
        mIncomingWidth = mIncomingHeight = -1;
    }

    public void setRecordingStartTime(long startUs, boolean inSync, String prefix, int index)
    {
   	    mRecordingStartUs = startUs;
        mSyncCapture = inSync;

   	    /*
   	     * things we need to construct a filename
   	     */
   	    mPrefix = prefix;
   	    mIndex = index;
    }

    public void setRecordingStopTime(long stopUs) 
    {
    	    mRecordingStopUs = stopUs;
    }

    private void resetRecordingState()
    {
        /*
         * mRecordingStartUs is the time we check (when each preview frame 
         * arrives) whether we should start recording. set it far into the 
         * future so we don't trigger it until they are property set (via
         * user pressing record on one device and via p2p messaging on the
         * other)
         */
        mRecordingStartUs = Long.MAX_VALUE;
        mRecordingStopUs = Long.MAX_VALUE;
        
        mLastTimeMs = 0;
        mNumOfFrameDelays = 0;
        mAvgFrameDelayMs = 0;

        mEncoderThreadStarted = false;
    }

    /**
     * Changes the filter that we're applying to the camera preview.
     */
    public void changeFilterMode(int filter) 
    {
        mNewFilter = filter;
    }

    /**
     * Updates the filter program.
     */
    public void updateFilter() 
    {
        Texture2dProgram.ProgramType programType;
        float[] kernel = null;
        float colorAdj = 0.0f;

        if(BuildConfig.DEBUG) Log.d(TAG, "Updating filter to " + mNewFilter);
        switch (mNewFilter) 
        {
            case FILTER_NONE:
                programType = Texture2dProgram.ProgramType.TEXTURE_EXT;
                break;
            case FILTER_BLACK_WHITE:
                /*
                 * (in a previous version the TEXTURE_EXT_BW variant was enabled by a 
                 * flag called ROSE_COLORED_GLASSES, because the shader set the red 
                 * channel to the B&W color and green/blue to zero.)
                 */
                programType = Texture2dProgram.ProgramType.TEXTURE_EXT_BW;
                break;
            case FILTER_BLUR:
                programType = Texture2dProgram.ProgramType.TEXTURE_EXT_FILT;
                kernel = new float[] {
                        1f/16f, 2f/16f, 1f/16f,
                        2f/16f, 4f/16f, 2f/16f,
                        1f/16f, 2f/16f, 1f/16f };
                break;
            case FILTER_SHARPEN:
                programType = Texture2dProgram.ProgramType.TEXTURE_EXT_FILT;
                kernel = new float[] {
                        0f, -1f, 0f,
                        -1f, 5f, -1f,
                        0f, -1f, 0f };
                break;
            case FILTER_EDGE_DETECT:
                programType = Texture2dProgram.ProgramType.TEXTURE_EXT_FILT;
                kernel = new float[] {
                        -1f, -1f, -1f,
                        -1f, 8f, -1f,
                        -1f, -1f, -1f };
                break;
            case FILTER_EMBOSS:
                programType = Texture2dProgram.ProgramType.TEXTURE_EXT_FILT;
                kernel = new float[] {
                        2f, 0f, 0f,
                        0f, -1f, 0f,
                        0f, 0f, -1f };
                colorAdj = 0.5f;
                break;
            default:
                throw new RuntimeException("Unknown filter mode " + mNewFilter);
        }

        /*
         * do we need a whole new program?  (We want to avoid doing this if we don't 
         * have too -- compiling a program could be expensive.)
         */
        if (programType != mFullScreen.getProgram().getProgramType()) 
        {
            mFullScreen.changeProgram(new Texture2dProgram(programType));

            /*
             * If we created a new program, we need to initialize the texture 
             * width/height.
             */
            mIncomingSizeUpdated = true;
        }

        /*
         * Update the filter kernel (if any).
         */
        if (kernel != null) 
        {
            mFullScreen.getProgram().setKernel(kernel, colorAdj);
        }

        mCurrentFilter = mNewFilter;
    }

    /**
     * Records the size of the incoming camera preview frames.
     * <p>
     * It's not clear whether this is guaranteed to execute before or after onSurfaceCreated(),
     * so we assume it could go either way.  (Fortunately they both run on the same thread,
     * so we at least know that they won't execute concurrently.)
     */
    public void setCameraPreviewSize(int quality, int rotation) 
    {
        if(BuildConfig.DEBUG) Log.d(TAG, "setCameraPreviewSize");
        mIncomingWidth = MainConsts.VIDEO_DIMENSIONS[quality][0];
        mIncomingHeight = MainConsts.VIDEO_DIMENSIONS[quality][1];
        mIncomingBitrate = MainConsts.VIDEO_DIMENSIONS[quality][2];
        mIncomingSizeUpdated = true;
        mIncomingRotation = rotation;
    }

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) 
    {
        if(BuildConfig.DEBUG) Log.d(TAG, "onSurfaceCreated");

        /*
         * Set up the texture blitter that will be used for on-screen display.  This
         * is *not* applied to the recording, because that uses a separate shader.
         */
        mFullScreen = new FullFrameRect(
                new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT));

        mTextureId = mFullScreen.createTextureObject();

        /*
         * create a SurfaceTexture, with an external texture, in this EGL context.  
         * we don't have a Looper in this thread -- GLSurfaceView doesn't create one 
         * -- so the frame available messages will arrive on the main thread.
         */
        mSurfaceTexture = new SurfaceTexture(mTextureId);

        /*
         * Tell the UI thread to enable the camera preview.
         */
        mCameraHandler.sendMessage(mCameraHandler.obtainMessage(
                CamcorderActivity.CameraHandler.MSG_SET_SURFACE_TEXTURE, mSurfaceTexture));
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) 
    {
        if(BuildConfig.DEBUG) Log.d(TAG, "onSurfaceChanged " + width + "x" + height);
    }

    @Override
    public void onDrawFrame(GL10 unused) 
    {
        if(BuildConfig.DEBUG) if (VERBOSE) Log.d(TAG, "onDrawFrame tex=" + mTextureId);

        /*
         * Latch the latest frame. If there isn't anything new, we'll just re-use 
         * whatever was there before.
         */
        mSurfaceTexture.updateTexImage();

        Long currTimeUs = SystemClock.elapsedRealtimeNanos()/1000;
        Long currTimeMs = currTimeUs/1000;

        if(!mIsRecording)
        {
            long captureWaitMs = (mRecordingStartUs - currTimeUs)/1000;
   
            /*
             * TODO: hard-coded value
             */
            if(captureWaitMs < 500)
            {
                /*
                 * if we are close to the start of capture time, we start estimating 
                 * frame rate, and use it to control when frame capture will begin
                 */
                if(mLastTimeMs != 0)
                {
                    mNumOfFrameDelays++;
                
                    int frameDelayMs = (int)(currTimeMs - mLastTimeMs);
                    mAvgFrameDelayMs = (mAvgFrameDelayMs * (float)(mNumOfFrameDelays-1) + 
           		        (float)frameDelayMs)/(float)mNumOfFrameDelays;
        
                    //if(BuildConfig.DEBUG) Log.d(TAG, "preview frame delay " + frameDelayMs + "ms" +
                               //", new avg = " + mAvgFrameDelayMs);
                }
                mLastTimeMs = currTimeMs;

                if(!mEncoderThreadStarted)
                {
                    File outputFile;
                    if(mSyncCapture)
                    {
                        /*
                         * for sync capture, set a temp path which will be renamed later on
                         */
                        String path = mPrefix + "_" + Integer.toString(mIndex) + ".mp4";
                        outputFile = new File(path);
                    }
                    else
                    {
                        /*
                         * for solo capture, set the correct path to use
                         */
                        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                        mOutputFilepath = mPrefix + timeStamp + ".mp4";
                        outputFile = new File(mOutputFilepath);
                    }

                    /*
                     * If we are getting close, start the encoder thread, so we are
                     * ready to record right away when time is right. the name
                     * "startRecording" below may be confusing - it means we start
                     * the encoding thread. but we won't actually feed it frames
                     * until time is right.
                     * 
                     * note there is only one instance of TextureMovieEncoder, but
                     * each time startRecording is invoked, a new encoding thread 
                     * is created. we want to call startRecording only once per
                     * recording.
                     */
                    mEncoder.startRecording(mTextureId, new TextureMovieEncoder.EncoderConfig(
                        outputFile, mIncomingWidth, mIncomingHeight, mIncomingRotation,
                        mIncomingBitrate, EGL14.eglGetCurrentContext()));

                    mEncoderThreadStarted = true;
                }
            }

            if(captureWaitMs < mAvgFrameDelayMs)
            {
                /*
                 * If the recording state is changing, take care of it here.  Ideally we 
                 * wouldn't be doing all this in onDrawFrame(), but the EGLContext sharing 
                 * with GLSurfaceView makes it hard to do elsewhere.
                 * 
                 * to synchronize the left/right video, we could tweak the encoder to
                 * adjust presentation time recorded in the stream, based on offset; 
                 * currently we do not do this, but rather record the offset in the
                 * file name of the video files, and adjust the timing at playback time
                 */
                if(mSyncCapture)
                {
                    mOutputFilepath = mPrefix + ((captureWaitMs > 0) ?
                            ("_m" + Long.toString(captureWaitMs)) :
                            ("_p" + Long.toString(-captureWaitMs))) +
                            "_" + Integer.toString(mIndex) + ".mp4";
                }

                if(BuildConfig.DEBUG) Log.d(TAG, "onDrawFrame: recording start, captureWaitMs=" + captureWaitMs +
       		       ", mAvgFrameDelay=" + mAvgFrameDelayMs + "ms");

                /*
                 * Tell the UI thread recording is starting. 
                 */
                mCameraHandler.sendMessage(mCameraHandler.obtainMessage(
                    CamcorderActivity.CameraHandler.MSG_SET_RECORDING_START, mOutputFilepath));

                mIsRecording = true;
            }
        }
        else if(currTimeUs >= mRecordingStopUs)
        {
            /*
             * stop recording
             */
            long captureLengthMs = (currTimeUs - mRecordingStartUs)/1000;
            if(BuildConfig.DEBUG) Log.d(TAG, "onDrawFrame: recording done, captureLengthMs=" + captureLengthMs);
                
            mEncoder.stopRecording();

            /*
             * Tell the UI thread recording is done. time to send file to 
             * remote device
             */
            mCameraHandler.sendMessage(mCameraHandler.obtainMessage(
                CamcorderActivity.CameraHandler.MSG_SET_RECORDING_STOP, mOutputFilepath));

            /*
             * reset recording flags and get ready for next capture
             */
            resetRecordingState();
            mIsRecording = false;
        }

        /*
         * tell the video encoder thread that a new frame is available.
         * this will be ignored if we're not actually recording.
         */
        if(mIsRecording)
        {
            mEncoder.frameAvailable(mSurfaceTexture);
        }

        if (mIncomingWidth <= 0 || mIncomingHeight <= 0) 
        {
            /*
             * Texture size isn't set yet.  This is only used for the filters, but 
             * to be safe we can just skip drawing while we wait for the various 
             * races to resolve. (this seems to happen if you toggle the screen off/on 
             * with power button.)
             */
            //if(BuildConfig.DEBUG) Log.d(TAG, "Drawing before incoming texture size set; skipping");
            return;
        }

        /*
         *  Update the filter, if necessary.
         */
        if (mCurrentFilter != mNewFilter) 
        {
            updateFilter();
        }

        if (mIncomingSizeUpdated) 
        {
            mFullScreen.getProgram().setTexSize(mIncomingWidth, mIncomingHeight);
            mIncomingSizeUpdated = false;
        }

        /*
         * Draw the video frame.
         */
        mSurfaceTexture.getTransformMatrix(mSTMatrix);
        mFullScreen.drawFrame(mTextureId, mSTMatrix);

        /*
         * update the time counter
         */
        if(mIsRecording)
        {
            updateTimeCounter(currTimeUs - mRecordingStartUs);
        }
    }

    /*
     * only update the view when we need to increment the counter
     */
    private void updateTimeCounter(Long durationUs) 
    {
   	    int durationSeconds = (int)(durationUs/1000000);
   	    if(durationSeconds != mTimeCounterSeconds)
   	    {
    	    mTimeCounterSeconds = durationSeconds;

            mCameraHandler.sendMessage(mCameraHandler.obtainMessage(
                CamcorderActivity.CameraHandler.MSG_SET_RECORDING_TIME, mTimeCounterSeconds));
   	    }
    }
}