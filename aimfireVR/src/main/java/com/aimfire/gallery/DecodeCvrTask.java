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
import java.io.IOException;

import com.aimfire.main.MainConsts;
import com.aimfire.utilities.ZipUtil;
import com.aimfire.camarada.BuildConfig;

import android.os.AsyncTask;
import android.util.Log;

public class DecodeCvrTask extends AsyncTask<Void, Void, Boolean>
{
	private static final String TAG = "DecodeCvrTask";
	
	private String mPath;

	public DecodeCvrTask(String path)
	{
		super();
		mPath = path;
	}
	
	@Override
    protected Boolean doInBackground(Void... _) 
	{
        try {
            ZipUtil.unzip(mPath, MainConsts.MEDIA_3D_ROOT_PATH, false);
        } catch (IOException e) {
            if(BuildConfig.DEBUG) Log.e(TAG, "doInBackground: error unzipping " + e);
            return false;
        }
        return true;
    }

    @Override
    protected void onPostExecute(Boolean result) 
    {
        super.onPostExecute(result);
        if(BuildConfig.DEBUG) Log.d(TAG, "onPostExecute: unzipping result = " + result);
    }
}