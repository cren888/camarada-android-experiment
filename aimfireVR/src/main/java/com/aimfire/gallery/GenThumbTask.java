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

import com.aimfire.main.MainConsts;

import android.media.ThumbnailUtils;
import android.os.AsyncTask;
import android.graphics.Bitmap;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Rect;
import android.graphics.Bitmap.CompressFormat;

public class GenThumbTask extends AsyncTask<Void, Void, Boolean>
{
	//private static final String TAG = "GenThumbTask";
	private String mPreviewPath;
	
	public GenThumbTask(String path)
	{
		super();
		mPreviewPath = path;
	}
	
	
	@Override
    protected Boolean doInBackground(Void... _) 
	{
	    /*
	     * generate a thumbnail if we don't have it already
	     */
	    generateThumb();

	    /*
	     * extract chain info from exif header of the preview frame
	     */
		//String info = MediaScanner.getInstance().extractExifInfo(mPreviewPath);
        //if(BuildConfig.DEBUG) Log.d(TAG, "chain info=" + info);

		return true;
    }

    @Override
    protected void onPostExecute(Boolean result) 
    {
        super.onPostExecute(result);
    }
    
    private void generateThumb()
    {
        String thumbPath = MainConsts.MEDIA_3D_THUMB_PATH + 
        		(new File(mPreviewPath)).getName().replace(MainConsts.PREVIEW_EXTENSION, MainConsts.PHOTO_EXTENSION);
        
        if(MediaScanner.isEmpty(mPreviewPath) || (new File(thumbPath)).exists())
        {
       	    return;
        }

        String tmpThumbPath = thumbPath + ".tmp";
        BitmapRegionDecoder decoder = null;
        Bitmap bmp = null;
        Bitmap thumb = null;
        FileOutputStream fos;

        try {
            decoder = BitmapRegionDecoder.newInstance(mPreviewPath, false);
            if(MediaScanner.is2d(mPreviewPath))
            {
                bmp = decoder.decodeRegion(
                        new Rect(0, 0, decoder.getWidth(), decoder.getHeight()), null);
            }
            else
            {
                bmp = decoder.decodeRegion(
                        new Rect(0, 0, decoder.getWidth() / 2, decoder.getHeight()), null);
            }

            thumb = ThumbnailUtils.extractThumbnail(bmp, 
	            MainConsts.THUMBNAIL_SIZE, MainConsts.THUMBNAIL_SIZE);

            fos = new FileOutputStream(tmpThumbPath);
            thumb.compress(CompressFormat.JPEG, 50, fos);
            fos.close();
            
	        (new File(tmpThumbPath)).renameTo(new File(thumbPath));
        } catch (Exception e) {
            e.printStackTrace();
        }

        if(bmp != null)
        {
            bmp.recycle();
        }

        if(thumb != null)
        {
            thumb.recycle();
        }
    }
}
