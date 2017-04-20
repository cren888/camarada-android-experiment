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
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.ExecutionException;

import com.aimfire.camarada.BuildConfig;
import com.aimfire.camarada.R;
import com.aimfire.main.MainConsts;
import com.aimfire.gallery.DownloadFileTask;
import com.aimfire.gallery.MediaScanner;
import com.google.firebase.crash.FirebaseCrash;

import android.os.Process;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

/**
 * downloads samples - if sample is a movie, download its preview frame, if it is
 * a photo, download the photo itself
 */
public class SamplesDownloader extends IntentService 
{
	private static final String TAG = "SamplesDownloader";
	
	public SamplesDownloader() 
	{
		super("SP");
	} 

	@Override
	protected void onHandleIntent(Intent intent) 
	{
        if(BuildConfig.DEBUG) Log.d(TAG, "onHandleIntent");

		Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
		
		Resources res = getResources();
		String httpDir = res.getString(R.string.samples_link_dir_name);

		/*
		 * to avoid hitting file access limits in google drive, we duplicated samples
		 * to 10 accounts. the links are stored in samples[1-10].lnk
		 */
		Random rand = new Random();
		int  n = rand.nextInt(10) + 1;
		if(BuildConfig.DEBUG) Log.d(TAG, "onHandleIntent: sample link file #" + n);

		String samplesLinkFilename = res.getString(R.string.samples_link_file_name)
				+ Integer.toString(n)
				+ "." + MainConsts.LINK_EXTENSION;

		String url = "https://" + res.getString(R.string.app_domain) + "/" + httpDir + "/" + 
		    samplesLinkFilename;

		String saveLinkFilename = res.getString(R.string.samples_link_file_name)
				+ "." + MainConsts.LINK_EXTENSION;
		String path = MainConsts.MEDIA_3D_ROOT_PATH + saveLinkFilename;

		/*
		 * download the link file
		 */
        DownloadFileTask dft = new DownloadFileTask(this, url, path);
        dft.execute();
        
        boolean isSuccess = false;

        /*
         * wait for the asynctask to be done
         */
        try {
			isSuccess = dft.get();
		} catch (InterruptedException e) {
			return;
		} catch (ExecutionException e) {
			return;
		}
        
        /*
         * parse the link file and decide which ones (if any) need to be downloaded
         */
        if(isSuccess)
        {
            if(BuildConfig.DEBUG) Log.d(TAG, "onHandleIntent: download link file success");
            parseLinkFile(path);
        }
        else
        {
            if(BuildConfig.DEBUG) Log.d(TAG, "onHandleIntent: unable to download link file");
        }
    }
	
    private void parseLinkFile(String linkPath)
    {
   	    ArrayList<String> samplesResId = new ArrayList<String>();
   	    ArrayList<String> samplesName = new ArrayList<String>();

   	    try {
   	        FileInputStream fis = new FileInputStream(linkPath);
    	 
   	        //Construct BufferedReader from InputStreamReader
   	        BufferedReader br = new BufferedReader(new InputStreamReader(fis));
             
   	        String line = null;
			while ((line = br.readLine()) != null) 
			{
				if(line.startsWith("#"))
				{
					/*
					 * comment line, skip
					 */
					continue;
				}

                String [] tokens = line.split(" ");
                if(tokens.length != 2)
                {
                    if(BuildConfig.DEBUG) Log.e(TAG, "parseLinkFile: cannot parse this line = " + line);
                    continue;
                }
                else
                {
               	    samplesResId.add(tokens[0]);
               	    samplesName.add(tokens[1]);
                }
			}
   	        br.close();
		} catch (IOException e) {
            if(BuildConfig.DEBUG) Log.e(TAG, "parseLinkFile: couldn't read samples.lnk " + e.getMessage());
			FirebaseCrash.report(e);
		}
    	    
   	    boolean needDownload = false;

   	    for(int i=0; i<samplesResId.size(); i++)
   	    {
       	    String resId = samplesResId.get(i);
       	    String name = samplesName.get(i);
       	    String path = null;

   	   	    /*
   	   	     * SBS...jpg - this is a 3D SBS image
       	     * MPG...jpeg - this is a preview frame of cvr movie
      	     */
   		    if(MediaScanner.isPreview(name))
   		    {
   	    	    path = MediaScanner.getPreviewPathFromOrigName(name);
   		        if((new File(path).exists()))
   		        {
   	        	    /*
   	        	     * we already have this sample
   	        	     */
   	        	    continue;
   		        }

   		        /*
   	             * create an empty, placeholder movie file, so GalleryActivity/ThumbsFragment
   	             * knows its existence and show a progress bar while this file is downloaded
   	             */
   	            String cvrPath = MediaScanner.getSharedMediaPathFromOrigName(name);

   		        if(!(new File(cvrPath).exists()))
   		        {
   	                try {
       				    MediaScanner.addItemMediaList(cvrPath);
   		                (new File(cvrPath)).createNewFile();
   	                } catch (IOException e) {
   		                Toast.makeText(this, R.string.error_accessing_storage, Toast.LENGTH_LONG).show();
                        FirebaseCrash.report(e);
   		                continue;
   			        }
   		        }
   		        else
   		        {
   		            /*
   		             * we don't have a preview file, but have a .cvr file, strange
   		             */
   	                if(BuildConfig.DEBUG) Log.e(TAG, "parseLinkFile: cvr file already exists " +
                   		"in Shared Media! shouldn't happen");
                    FirebaseCrash.report(new Exception("parseLinkFile: cvr file already exists " +
                            "in Shared Media! shouldn't happen"));
   		        }
   		    }
   		    else if(MediaScanner.isPhoto(name))
   		    {
   	            path = MediaScanner.getSharedMediaPathFromOrigName(name);
   		        if((new File(path)).exists())
   		        {
   	        	    /*
   	        	     * we already have this sample
   	        	     */
   	        	    continue;
   		        }
   		        else
   		        {
   	                try {
       				    MediaScanner.addItemMediaList(path);
   		                (new File(path)).createNewFile();
   	                } catch (IOException e) {
   		                Toast.makeText(this, R.string.error_accessing_storage, Toast.LENGTH_LONG).show();
                        FirebaseCrash.report(e);
   		                continue;
   			        }
   		        }
   		    }
   		    else
   		    {
   	    	    /*
   	    	     * we are downloading preview and photo only. somehow a cvr
   	    	     * or some other file got into the link file. ignore
   	    	     */
                if(BuildConfig.DEBUG) Log.e(TAG, "downloadSamples: file name = " + name +
               		"shouldn't be here");
	    	    continue;
   		    }

   		    /*
   		     * we have at least one sample that needs to be downloaded
   		     */
   		    needDownload = true;

   	        /*
   	         * save drive file record.
   	         */
       	    saveDriveFileRecord(name, resId);
   	    }

   	    if(needDownload)
   	    {
   	        /*
   	         * notify Thumbs fragment or Gallery activity to update their list
   	         */
            Intent messageIntent = new Intent(MainConsts.FILE_DOWNLOADER_MESSAGE);
            messageIntent.putExtra(MainConsts.EXTRA_WHAT, MainConsts.MSG_FILE_DOWNLOADER_SAMPLES_START);
            LocalBroadcastManager.getInstance(this).sendBroadcast(messageIntent);
   	    }
    }
    
	private void saveDriveFileRecord(String filename, String resId) 
    {
		Context context = getApplicationContext();
		
        SharedPreferences driveFileRecord =
            context.getSharedPreferences(context.getString(R.string.drive_file_record), Context.MODE_PRIVATE);

        if (driveFileRecord != null) 
        {
            SharedPreferences.Editor editor = driveFileRecord.edit();
            editor.putString(filename, resId);
            editor.commit();
        }
    }
}