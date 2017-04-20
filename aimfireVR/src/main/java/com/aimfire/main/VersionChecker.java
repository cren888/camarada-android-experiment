package com.aimfire.main;

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
import java.util.concurrent.ExecutionException;

import com.aimfire.camarada.BuildConfig;
import com.aimfire.camarada.R;
import com.aimfire.gallery.DownloadFileTask;

import android.os.Process;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.util.Log;

/**
 * check version code
 */
public class VersionChecker extends IntentService 
{
	private static final String TAG = "VersionChecker";
	
	public VersionChecker() 
	{
		super("VC");
	} 

	@Override
	protected void onHandleIntent(Intent intent) 
	{
        if(BuildConfig.DEBUG) Log.d(TAG, "onHandleIntent");

		Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
		
		Resources res = getResources();
		String verTxt = res.getString(R.string.version_text_file_name);
		String url = "https://" + res.getString(R.string.app_domain) + "/" + verTxt;
		String path = MainConsts.MEDIA_3D_ROOT_PATH + verTxt;

		/*
		 * download version.txt
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
         * parse the version and notify user
         */
        if(isSuccess)
        {
            if(BuildConfig.DEBUG) Log.d(TAG, "onHandleIntent: download version success");
            parseVerTxt(path);
        }
        else
        {
            if(BuildConfig.DEBUG) Log.d(TAG, "onHandleIntent: unable to download version");
        }
    }
	
    private void parseVerTxt(String path)
    {
    	    int latestVerCode = -1;
    	    try {
    	        FileInputStream fis = new FileInputStream(path);
    	 
    	        //Construct BufferedReader from InputStreamReader
    	        BufferedReader br = new BufferedReader(new InputStreamReader(fis));
             
    	        String line = br.readLine();
    	        br.close();
    	        
    	        latestVerCode = Integer.parseInt(line);
    	        
    	        (new File(path)).delete();
		} catch (IOException e) {
            if(BuildConfig.DEBUG) Log.e(TAG, "parseVerTxt: couldn't get new version" + e.getMessage());
            return;
		}
    	    
        SharedPreferences settings =
            getSharedPreferences(getString(R.string.settings_file), Context.MODE_PRIVATE);

        if (settings != null) 
        {
    	        SharedPreferences.Editor editor = settings.edit();
    	        editor.putInt(MainConsts.LATEST_VERSION_CODE_KEY, latestVerCode);
    	        editor.commit();
        }
    }
}