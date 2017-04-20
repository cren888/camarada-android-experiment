package com.aimfire.gallery;

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
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import com.aimfire.camarada.BuildConfig;
import com.aimfire.main.MainConsts;
import com.aimfire.drive.service.DownloadCompletionService;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.util.Log;


/**
 * DownloadFileTask: this is an AsyncTask - different from and not to be confused with 
 * DownloadFileFragment. it downloads a file without the ability to follow any link
 * redirection.
 */
public class DownloadFileTask extends AsyncTask<Void, Integer, Boolean> 
{
	private static final String TAG = "DownloadFileTask";

	Context mContext = null;
	String mUrl = null;
	String mPath = null;
    String tmpPath = null;

	/**
	 * download file specified by url and store it to path. 
	 */
    public DownloadFileTask(Context context, String url, String path) 
    {
    	    mContext = context;
    	    mUrl = url;

    	    mPath = path;
        tmpPath = mPath + ".tmp";
    }

    @Override
    protected Boolean doInBackground(Void... _) 
    {
        InputStream input = null;
        OutputStream output = null;
        HttpURLConnection connection = null;

        try {
            URL url = new URL(mUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000); // 5 sec
            connection.setReadTimeout(10000); // 10 sec
            //connection.connect();

            // expect HTTP 200 OK, so we don't mistakenly save error report
            // instead of the file
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) 
            {
                if(BuildConfig.DEBUG) Log.e(TAG, "doInBackground: server returned HTTP " + connection.getResponseCode()
                    + " " + connection.getResponseMessage());
                return false;
            }

            // this will be useful to display download percentage
            // might be -1: server did not report the length
            int fileLength = connection.getContentLength();

            // download the file
            input = connection.getInputStream();
            output = new FileOutputStream(tmpPath);

            byte data[] = new byte[4096];
            long total = 0;
            int count;
            while ((count = input.read(data)) != -1) 
            {
                total += count;

                // publishing the progress....
                if (fileLength > 0) // only if total length is known
                    publishProgress((int) (total * 100 / fileLength));

                output.write(data, 0, count);
            }

            if (output != null)
            {
                output.close();
            }
            if (input != null)
            {
                input.close();
            }

            if (connection != null)
            {
                connection.disconnect();
            }

            (new File(tmpPath)).renameTo(new File(mPath));
        } catch (Exception e) {
            if(BuildConfig.DEBUG) Log.e(TAG, "doInBackground: download exception " + e.getMessage());

            if (connection != null)
            {
                connection.disconnect();
            }
            return false;
        } 
        
        return true;
    }
    
    @Override
    protected void onPreExecute() 
    {
        super.onPreExecute();
    }

    @Override
    protected void onProgressUpdate(Integer... progress) 
    {
        super.onProgressUpdate(progress);
    }

    @Override
    protected void onPostExecute(Boolean isSuccess) 
    {
        super.onPostExecute(isSuccess);
        
        /*
         * if we are downloading a link file or version file, the caller will wait 
         * for this to finish and process it directly. if this is not a link file, 
         * we will invoke the DownloadCompletionService to process.
         */
        if(MediaScanner.isPreview(mPath) || MediaScanner.isPhoto(mPath))
        {
	        /*
	         * start a service to extract the file.
	         */
            Intent serviceIntent = new Intent(mContext, DownloadCompletionService.class);
            serviceIntent.putExtra(MainConsts.EXTRA_STATUS, isSuccess);
            serviceIntent.putExtra(MainConsts.EXTRA_PATH, mPath);
            mContext.startService(serviceIntent);
        }
    }
}