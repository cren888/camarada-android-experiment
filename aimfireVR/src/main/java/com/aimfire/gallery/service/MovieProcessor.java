package com.aimfire.gallery.service;

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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import com.aimfire.main.MainConsts;
import com.aimfire.utilities.ZipUtil;
import com.aimfire.v.p;
import com.aimfire.camarada.BuildConfig;
import com.aimfire.gallery.MediaScanner;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crash.FirebaseCrash;

import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.os.Process;
import android.os.SystemClock;
import android.app.IntentService;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.hardware.Camera;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

@SuppressWarnings("deprecation")
public class MovieProcessor extends IntentService 
{
	private static final String TAG = "MovieProcessor";
	
	/*
	 * extracting first keyframe around FIRST_KEYFRAME_TIME_US
	 * extracting last keyframe around LAST_KEYFRAME_TIME_US
	 */
	private static final long FIRST_KEYFRAME_TIME_US = 0l;
	//private static final long LAST_KEYFRAME_TIME_US = (MainConsts.VIDEO_LENGTH_SECONDS-MainConsts.VIDEO_IFRAME_INTERVAL)*1000000;

    private static final int ERROR_EXTRACT_SYNC_FRAME_ERROR = 0;
    private static final int ERROR_EXTRACT_SYNC_FRAME_EXCEPTION = 1;
    private static final int ERROR_EXTRACT_SIMILARITY_MATRIX = 2;

    /*
     * firebase analytics
     */
    private FirebaseAnalytics mFirebaseAnalytics;

    public MovieProcessor()
	{
		super("MP");
	} 

	@Override
	protected void onHandleIntent(Intent intent) 
	{
        /*
         * Obtain the FirebaseAnalytics instance.
         */
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

        boolean []result = new boolean[]{false, false};
        String previewPath = null;
        String thumbPath = null;
	    String configPath = null;
	    String convertFilePath = null;

        String exportL = MainConsts.MEDIA_3D_RAW_PATH + "L.png";
        String exportR = MainConsts.MEDIA_3D_RAW_PATH + "R.png";

		Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

        Bundle extras = intent.getExtras();
        if (extras == null) 
        {
            if(BuildConfig.DEBUG) Log.e(TAG, "onHandleIntent: error, wrong parameter");
            FirebaseCrash.report(new Exception("onHandleIntent: error, wrong parameter"));
   	        return;
        }

        String filePathL = extras.getString("lname");
        String filePathR = extras.getString("rname");
        String cvrNameNoExt = MediaScanner.getProcessedCvrName((new File(filePathL)).getName());

        if(BuildConfig.DEBUG) Log.d(TAG, "onHandleIntent:left file=" + filePathL +  
        		", right file=" + filePathR);

        String creatorName = extras.getString("creator");
        String creatorPhotoUrl = extras.getString("photo");

        float scale = extras.getFloat(MainConsts.EXTRA_SCALE);

        /*
         * if left/right videos were taken using front facing camera,
         * they need to be swapped when generating sbs 
         */
        int facing = extras.getInt(MainConsts.EXTRA_FACING);
        boolean isFrontCamera = (facing == Camera.CameraInfo.CAMERA_FACING_FRONT) ? true:false;

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();

        Bitmap bitmapL = null; 
        Bitmap bitmapR = null;

        long frameTime = FIRST_KEYFRAME_TIME_US;
        if(BuildConfig.DEBUG) Log.d(TAG, "extract frame from left at " + frameTime/1000 + "ms");

        try {
            long startUs = SystemClock.elapsedRealtimeNanos()/1000;

            FileInputStream inputStreamL = new FileInputStream(filePathL);
            retriever.setDataSource(inputStreamL.getFD());
            inputStreamL.close();

            bitmapL = retriever.getFrameAtTime(frameTime, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);

            FileInputStream inputStreamR = new FileInputStream(filePathR);
            retriever.setDataSource(inputStreamR.getFD());
            inputStreamR.close();

            bitmapR = retriever.getFrameAtTime(frameTime, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);

            retriever.release();

            long stopUs = SystemClock.elapsedRealtimeNanos()/1000;
            if(BuildConfig.DEBUG) Log.d(TAG, "retrieving preview frames took " + (stopUs-startUs)/1000 + "ms");

            if((bitmapL != null) && (bitmapR != null))
            {
                saveFrame(bitmapL, exportL);
                saveFrame(bitmapR, exportR);
            }
            else
            {
   	            reportError(MediaScanner.getProcessedCvrPath((new File(filePathL)).getName()), ERROR_EXTRACT_SYNC_FRAME_ERROR);
       	        return;
            }

            previewPath = MainConsts.MEDIA_3D_THUMB_PATH + cvrNameNoExt + ".jpeg";

            result = p.getInstance().f1(exportL, exportR, previewPath, scale, isFrontCamera);
        } catch (Exception ex) {
            retriever.release();
   	        reportError(MediaScanner.getProcessedCvrPath((new File(filePathL)).getName()), ERROR_EXTRACT_SYNC_FRAME_EXCEPTION);
   	        return;
        }

        if(!result[0])
        {
   	        reportError(MediaScanner.getProcessedCvrPath((new File(filePathL)).getName()), ERROR_EXTRACT_SIMILARITY_MATRIX);
        	    
       	    File leftFrom = (new File(filePathL));
       	    File rightFrom = (new File(filePathR));
       	    File leftExportFrom = (new File(exportL));
       	    File rightExportFrom = (new File(exportR));

            if(!BuildConfig.DEBUG) 
            {
           	    leftFrom.delete();
           	    rightFrom.delete();

           	    leftExportFrom.delete();
           	    rightExportFrom.delete();
            }
            else
            {
       	        File leftTo = new File(MainConsts.MEDIA_3D_DEBUG_PATH + leftFrom.getName());
       	        File rightTo = new File(MainConsts.MEDIA_3D_DEBUG_PATH + rightFrom.getName());

           	    leftFrom.renameTo(leftTo);
           	    rightFrom.renameTo(rightTo);

       	        File leftExportTo = new File(MainConsts.MEDIA_3D_DEBUG_PATH + leftExportFrom.getName());
       	        File rightExportTo = new File(MainConsts.MEDIA_3D_DEBUG_PATH + rightExportFrom.getName());

           	    leftExportFrom.renameTo(leftExportTo);
           	    rightExportFrom.renameTo(rightExportTo);
            }
        }
        else
        {
       	    double [] similarityMat = p.getInstance().g();

            String configData = similarityMat[0] + " " + similarityMat[1] + " " + similarityMat[2] + " " +
                                similarityMat[3] + " " + similarityMat[4] + " " + similarityMat[5];
        
       	    if(result[1])
       	    {
   		        convertFilePath = filePathR;
       	    }
       	    else
       	    {
   		        convertFilePath = filePathL;
       	    }

	        configPath = createConfigFile(convertFilePath, configData);

       	    /*
       	     * save the thumbnail
       	     */
       	    if(bitmapL != null)
       	    {
                thumbPath = MainConsts.MEDIA_3D_THUMB_PATH + cvrNameNoExt + ".jpg";
       	        saveThumbnail(bitmapL, thumbPath);

       	        MediaScanner.insertExifInfo(thumbPath, "name=" + creatorName +
       	        		"photourl=" + creatorPhotoUrl);
       	    }

   		    createZipFile(filePathL, filePathR, configPath, thumbPath, previewPath);

   		    /*
   		     * let CamcorderActivity know we are done.
   		     */
   	        reportResult(MediaScanner.getProcessedCvrPath((new File(filePathL)).getName()));
        }
        
        /*
         * paranoia
         */
        if(bitmapL != null)
        {
		    bitmapL.recycle();
        }
        if(bitmapR != null)
        {
		    bitmapR.recycle();
        }
        
        (new File(exportL)).delete();
        (new File(exportR)).delete();
    }
	
	private void saveThumbnail(Bitmap bitmap, String path)
	{
	    FileOutputStream fos;

        Bitmap thumbnail = ThumbnailUtils.extractThumbnail(bitmap, 
	            MainConsts.THUMBNAIL_SIZE, MainConsts.THUMBNAIL_SIZE);
        try { 
		    fos = new FileOutputStream(path); 
		    thumbnail.compress(CompressFormat.JPEG, 100, fos);
            fos.close();
        } catch (Exception e) {
            FirebaseCrash.report(e);
        }
	}

	private void saveFrame(Bitmap bitmap, String path)
	{
		FileOutputStream out = null;
		try {
		    out = new FileOutputStream(path);
		    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); 
		} catch (Exception e) {
            FirebaseCrash.report(e);
		} finally {
		    try {
		        if (out != null) {
		            out.close();
		        }
		    } catch (IOException e) {
                FirebaseCrash.report(e);
		    }
		}
	}

	/**
	 * filePath1 is the file corrected. filePath2 is the other file
	 */
	private void reportResult(String path)
	{
        mFirebaseAnalytics.logEvent(MainConsts.FIREBASE_CUSTOM_EVENT_SYNC_MOVIE_CAPTURE_COMPLETE, null);

        Intent messageIntent = new Intent(MainConsts.MOVIE_PROCESSOR_MESSAGE);
        messageIntent.putExtra(MainConsts.EXTRA_WHAT, MainConsts.MSG_MOVIE_PROCESSOR_RESULT);
        messageIntent.putExtra(MainConsts.EXTRA_PATH, path);
        messageIntent.putExtra(MainConsts.EXTRA_MSG, true/*isMyMedia*/);
        LocalBroadcastManager.getInstance(this).sendBroadcast(messageIntent);
	}

	private void reportError(String path, int errorCode)
	{
        Bundle params = new Bundle();
        params.putString("captureErrorCode", Integer.toString(errorCode));
        mFirebaseAnalytics.logEvent(MainConsts.FIREBASE_CUSTOM_EVENT_SYNC_MOVIE_CAPTURE_ERROR, params);

        Intent messageIntent = new Intent(MainConsts.MOVIE_PROCESSOR_MESSAGE);
        messageIntent.putExtra(MainConsts.EXTRA_WHAT, MainConsts.MSG_MOVIE_PROCESSOR_ERROR);
        messageIntent.putExtra(MainConsts.EXTRA_PATH, path);
        messageIntent.putExtra(MainConsts.EXTRA_MSG, true/*isMyMedia*/);
        LocalBroadcastManager.getInstance(this).sendBroadcast(messageIntent);
        
		/*
		 * delete the placeholder file
		 */
        MediaScanner.removeItemMediaList(path);
		(new File(path)).delete();
	}
	
    private String createConfigFile(String filePath, String configData)
    {
   		/*
   		 * now we write the result into a configuration file, to be used
   		 * by movie player later on
   		 */
   		File file = new File(filePath);
   		String filename = file.getName();
        String [] tmp = filename.split("\\.");
        String configFilename = tmp[0] + ".config";
        String configPath = MainConsts.MEDIA_3D_RAW_PATH + configFilename;
        File configFile = new File(MainConsts.MEDIA_3D_RAW_PATH, configFilename);

		try {
			FileOutputStream stream = new FileOutputStream(configFile);
            stream.write(configData.getBytes());
            stream.close();
        } catch (IOException e) {
            if(BuildConfig.DEBUG) Log.e(TAG, "createConfigFile: error writing config" + e);
            FirebaseCrash.report(e);
		}
		
		return configPath;
    }
    

    /**
     * create a zip file left/right mp4 files and config file
     */
    private void createZipFile(String filePathL, String filePathR, 
    		String configPath, String thumbPath, String previewPath)
    {
        /*
         * zip the two mp4 files, config file, and thumbnails
         */
        final String input[] = new String[]{filePathL, filePathR, 
        		configPath, thumbPath, previewPath};
        
   	    final String output = MediaScanner.getProcessedCvrPath((new File(filePathL)).getName());
	            
   	    try {
			ZipUtil.zip(input, output, MainConsts.MEDIA_3D_ROOT_PATH);
		} catch (IOException e) {
            if(BuildConfig.DEBUG) Log.e(TAG, "createZipFile: error creating zip " + e);
            FirebaseCrash.report(e);
		}
    }
}