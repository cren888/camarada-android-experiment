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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import com.aimfire.main.MainConsts;
import com.aimfire.gallery.MediaScanner;
import com.aimfire.camarada.BuildConfig;
import com.aimfire.v.p;
import com.google.firebase.analytics.FirebaseAnalytics;

import android.media.ThumbnailUtils;
import android.os.Process;
import android.app.IntentService;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Rect;
import android.graphics.Bitmap.CompressFormat;
import android.hardware.Camera;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

@SuppressWarnings("deprecation")
public class PhotoProcessor extends IntentService 
{
	private static final String TAG = "PhotoProcessor";

    /*
     * firebase analytics
     */
    private FirebaseAnalytics mFirebaseAnalytics;

    public PhotoProcessor()
	{
		super("PP");
	} 

	@Override
	protected void onHandleIntent(Intent intent) 
	{
		Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

        /*
         * Obtain the FirebaseAnalytics instance.
         */
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

        Bundle extras = intent.getExtras();
        if (extras == null) 
        {
            if(BuildConfig.DEBUG) Log.e(TAG, "onHandleIntent: error, wrong parameter");
    	        return;
        }

        String filename1 = extras.getString("lname");
        String filename2 = extras.getString("rname");

        String creatorName = extras.getString("creator");
        String creatorPhotoUrl = extras.getString("photo");

        float scale = extras.getFloat(MainConsts.EXTRA_SCALE);

        int facing = extras.getInt(MainConsts.EXTRA_FACING);
        boolean isFrontCamera = (facing == Camera.CameraInfo.CAMERA_FACING_FRONT) ? true:false;

        String[] stereoPath = MediaScanner.getImgPairPaths(filename1, filename2);

        if(stereoPath == null)
        {
            /*
             * something seriously wrong here - can't find matching image
             */
        	    if(BuildConfig.DEBUG) Log.e(TAG, "onHandleEvent: cannot locate stereo image pair");
        	    reportError(MediaScanner.getProcessedSbsPath(filename1));
            return;
        }

        if(BuildConfig.DEBUG) Log.d(TAG, "onHandleIntent:stereoPath[0]=" + stereoPath[0] +  
                                 ",stereoPath[1]=" + stereoPath[1] +  
                                 ",stereoPath[2]=" + stereoPath[2] +  
                                 ",stereoPath[3]=" + stereoPath[3] +  
                                 ",stereoPath[4]=" + stereoPath[4]);

        /*
         * now do auto alignment and store images as full width sbs jpgs. 
         * original left/right images will be removed unless save flag
         * is set to true (for debugging)
         */
        boolean []success = new boolean[]{false, false};
        try{
            success = p.getInstance().b(stereoPath[0], stereoPath[1], stereoPath[2], scale, isFrontCamera);
        }
        	catch (RuntimeException e) {
	            e.printStackTrace();
        }

        if(!success[0])
        {
        	    reportError(stereoPath[2]);
        }
        else
        {
        	    saveThumbnail(stereoPath[2], 
        	    		MainConsts.MEDIA_3D_THUMB_PATH + (new File(stereoPath[2])).getName());

        	    MediaScanner.insertExifInfo(stereoPath[2], "name=" + creatorName + 
        	    		"photourl=" + creatorPhotoUrl);

        	    reportResult(stereoPath[2], success[1]);
        }

        File leftFrom = new File(stereoPath[0]);
        File rightFrom = new File(stereoPath[1]);
    
        if(!BuildConfig.DEBUG) 
        {
        	    leftFrom.delete();
        	    rightFrom.delete();
        }
        else
        {
    	        File leftTo = new File(stereoPath[3]);
    	        File rightTo = new File(stereoPath[4]);

    	        leftFrom.renameTo(leftTo);
    	        rightFrom.renameTo(rightTo);
        }
    }
	
	private void saveThumbnail(String sbsPath, String thumbPath)
	{
        try {
            BitmapRegionDecoder decoder = null;
            Bitmap bmp = null;

            decoder = BitmapRegionDecoder.newInstance(sbsPath, false);
            bmp = decoder.decodeRegion(
            		new Rect(0, 0, decoder.getWidth()/2, decoder.getHeight()), null);

            Bitmap thumb = ThumbnailUtils.extractThumbnail(bmp, 
    		        MainConsts.THUMBNAIL_SIZE, MainConsts.THUMBNAIL_SIZE);

            FileOutputStream fos;
            fos = new FileOutputStream(thumbPath);
            thumb.compress(CompressFormat.JPEG, 50, fos);
            fos.close();
		
            if(bmp != null)
            {
                bmp.recycle();
            }

            if(thumb != null)
            {
                thumb.recycle();
            }
	    } catch (FileNotFoundException e) {
		    e.printStackTrace();
	    } catch (IOException e) {
		    e.printStackTrace();
	    } catch (Exception e) {
		    e.printStackTrace();
	    }
	}

	private void reportResult(String path, boolean isComfy)
	{
        mFirebaseAnalytics.logEvent(MainConsts.FIREBASE_CUSTOM_EVENT_SYNC_PHOTO_CAPTURE_COMPLETE, null);

        Intent messageIntent = new Intent(MainConsts.PHOTO_PROCESSOR_MESSAGE);
        messageIntent.putExtra(MainConsts.EXTRA_WHAT, MainConsts.MSG_PHOTO_PROCESSOR_RESULT);
        messageIntent.putExtra(MainConsts.EXTRA_PATH, path);
        messageIntent.putExtra(MainConsts.EXTRA_MSG, true/*isMyMedia*/);
        messageIntent.putExtra(MainConsts.EXTRA_COMFY, isComfy);
        LocalBroadcastManager.getInstance(this).sendBroadcast(messageIntent);
	}

	private void reportError(String path)
	{
        mFirebaseAnalytics.logEvent(MainConsts.FIREBASE_CUSTOM_EVENT_SYNC_PHOTO_CAPTURE_ERROR, null);

        Intent messageIntent = new Intent(MainConsts.PHOTO_PROCESSOR_MESSAGE);
        messageIntent.putExtra(MainConsts.EXTRA_WHAT, MainConsts.MSG_PHOTO_PROCESSOR_ERROR);
        messageIntent.putExtra(MainConsts.EXTRA_PATH, path);
        messageIntent.putExtra(MainConsts.EXTRA_MSG, true/*isMyMedia*/);
        LocalBroadcastManager.getInstance(this).sendBroadcast(messageIntent);
        
		/*
		 * delete the placeholder file
		 */
        MediaScanner.removeItemMediaList(path);
		(new File(path)).delete();
	}
}