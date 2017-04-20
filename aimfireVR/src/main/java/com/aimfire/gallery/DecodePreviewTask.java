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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

import com.aimfire.main.MainConsts;
import com.aimfire.v.p;
import com.aimfire.camarada.BuildConfig;

import android.media.ThumbnailUtils;
import android.os.AsyncTask;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Rect;
import android.graphics.Bitmap.CompressFormat;
import android.util.Log;
import android.widget.FrameLayout;
import android.widget.ImageView;

public class DecodePreviewTask extends AsyncTask<Void, Void, Bitmap>
{
	private static final String TAG = "DecodePreviewTask";
	
	private FrameLayout mFrameLayout;
	private String mPath;
    private DisplayMode mDisplayMode;
    private boolean mDisplayColor, mDisplaySwap;
    private int mScreenWidth, mScreenHeight;

	public DecodePreviewTask(FrameLayout fl, String path,
                             DisplayMode dm, boolean dc, boolean ds,
                             int sw, int sh)
	{
		super();
		mFrameLayout = fl;
		mPath = path;
		mDisplayMode = dm;
		mDisplayColor = dc;
		mDisplaySwap = ds;
		mScreenWidth = sw;
		mScreenHeight = sh;
	}
	
	
	@Override
    protected Bitmap doInBackground(Void... _) 
	{
		Bitmap bitmap = null;
	    String outputPath = null; 

	    /*
	     * generate a thumbnail if we don't have it already
	     */
	    generateThumb();

	    /*
         *  filename:
         *  
         *  SBS_<refCode>_<index>.jpg
         *  MPG_<refCode>_<index>.jpeg
         *  MPG_solo_date_time.jpeg
         *
         */
        if(MediaScanner.is2d(mPath))
        {
            outputPath = mPath;
        }
        else
        {
            String prefix = null;

            if (mPath.endsWith(MainConsts.PHOTO_EXTENSION))
            {
                prefix = "SBS";
            }
            else if (mPath.endsWith(MainConsts.PREVIEW_EXTENSION))
            {
                prefix = "MPG";
            }
            else
            {
                // Shouldn't be here
                return null;
            }

            String filename = (new File(mPath)).getName();

            switch (mDisplayMode) {
                case Anaglyph:
                    outputPath = MainConsts.MEDIA_3D_EXPORT_PATH + filename.replace(prefix, prefix + "A" + (mDisplayColor ? "1" : "0") + (mDisplaySwap ? "1" : "0")).replace(MainConsts.PREVIEW_EXTENSION, MainConsts.PHOTO_EXTENSION);
                    p.getInstance().e(mPath, outputPath, AnaglyphType.RedCyan.getValue(), mDisplayColor, mDisplaySwap);
                    break;
                case SbsFull:
                    if (mDisplaySwap || !mDisplayColor)
                    {
                        outputPath = MainConsts.MEDIA_3D_EXPORT_PATH + filename.replace(prefix, prefix + "F" + (mDisplayColor ? "1" : "0") + (mDisplaySwap ? "1" : "0")).replace(MainConsts.PREVIEW_EXTENSION, MainConsts.PHOTO_EXTENSION);
                        p.getInstance().d(mPath, outputPath, SbsType.FullWidth.getValue(), mDisplayColor, mDisplaySwap);
                    }
                    else
                    {
   	    	            /*
   	    	             * this is just our orginal file, nothing to do here
   	    	             */
                        outputPath = mPath;
                    }
                    break;
                case SbsHalf:
    	            /*
    	             * this is intended for 3D TV, and to my knowledge, we will never need to swap
    	             * left and right
    	             */
                    outputPath = MainConsts.MEDIA_3D_EXPORT_PATH + filename.replace(prefix, prefix + "H" + (mDisplayColor ? "1" : "0") + "0").replace(MainConsts.PREVIEW_EXTENSION, MainConsts.PHOTO_EXTENSION);
                    p.getInstance().d(mPath, outputPath, SbsType.HalfWidth.getValue(), mDisplayColor, false);
                    break;
                case Cardboard: //we should not get here!
                default:
                    break;
            }
        }

        if(mFrameLayout != null)
        {
            bitmap = decodeFile(outputPath);
        }
		return bitmap;
    }

    @Override
    protected void onPostExecute(Bitmap bitmap) 
    {
        super.onPostExecute(bitmap);
        
        if(mFrameLayout != null)
        {
   	        ((ImageView)mFrameLayout.getChildAt(0)).setImageBitmap(bitmap);
        }
    }
    
    private void generateThumb()
    {
        String thumbPath = MainConsts.MEDIA_3D_THUMB_PATH + (new File(mPath)).getName();
        
        if(MediaScanner.isEmpty(mPath) || (new File(thumbPath)).exists())
        {
       	    return;
        }

        String tmpThumbPath = thumbPath + ".tmp";
        BitmapRegionDecoder decoder = null;
        Bitmap bmp = null;
        Bitmap thumb = null;
        FileOutputStream fos;

        try {
            decoder = BitmapRegionDecoder.newInstance(mPath, false);
            if(MediaScanner.is2d(mPath))
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

    /**
     * decode bitmap to screen size. no wasted memory. c.f. "http://stackoverflow.
     * com/questions/6410364/how-to-scale-bitmap-to-screen-size"
     */
    private Bitmap decodeFile(String path)
    {
   	    int decodeWidth, decodeHeight;

        /*
         * decode image size without actually allocating memory
         */
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try {
			BitmapFactory.decodeStream(new FileInputStream(path), null, o);
		} catch (FileNotFoundException e) {
       	    if(BuildConfig.DEBUG) Log.e(TAG, "decodeFile: file " + path + " not found");
       	    return null;
		} catch (Exception e) {
       	    if(BuildConfig.DEBUG) Log.e(TAG, "decodeFile: file " + path + " other exception (empty file?)");
       	    return null;
		}              
        if(BuildConfig.DEBUG) Log.d(TAG, "decodeFile: bound width = " + o.outWidth + ", height = " + o.outHeight);

        /*
         * our image is outWidth/outHeight, and our screen is screenWidth/
         * screenHeight. calculate what our image's final size should be 
         * after decoding
         */
        float arScreen = (float)mScreenWidth/(float)mScreenHeight;
        float arImage = (float)o.outWidth/(float)o.outHeight;
        if(arImage > arScreen)
        {
       	    decodeWidth = mScreenWidth;
       	    decodeHeight = (int)((float)mScreenWidth/arImage);
        }
        else
        {
       	    decodeHeight = mScreenHeight;
       	    decodeWidth = (int)((float)mScreenHeight*arImage);
        }
        if(BuildConfig.DEBUG) Log.d(TAG, "decodeFile:  decode width = " + decodeWidth + ", height = " + decodeHeight);

        /*
         * decode with inSampleSize. android will automatically adjust
         * the supplied inSampleSize to the nearest power of 2. so we
         * could've just set inSampleSize to outWidth/decodeWidth. but
         * the nearest power of 2 could be either higher or lower, and
         * if it's higher, we will lose some picture quality. so we will
         * loop below to find the best power of 2 downsampling that will
         * result in a picture that's slightly bigger.
         */
        int width_tmp=o.outWidth, height_tmp=o.outHeight;
        int scale=1;
        while(true)
        {
            if(width_tmp/2<decodeWidth || height_tmp/2<decodeHeight)
                break;
            width_tmp/=2;
            height_tmp/=2;
            scale++;
        }

        BitmapFactory.Options o2 = new BitmapFactory.Options();
        //o2.inSampleSize=o.outWidth/decodeWidth;
        o2.inSampleSize = (int)Math.pow(2, scale);
        if(BuildConfig.DEBUG) Log.d(TAG, "decodeFile: inSampleSize = " + o2.inSampleSize);

        Bitmap sampledBitmap = null;
        Bitmap finalBitmap = null;
		try {
			sampledBitmap = BitmapFactory.decodeStream(new FileInputStream(path), null, o2);
            finalBitmap = Bitmap.createScaledBitmap(sampledBitmap, decodeWidth, decodeHeight, true);
		} catch (Exception e) {
            if(BuildConfig.DEBUG) Log.e(TAG, "decodeFile: exception = " + e.getMessage());
			return null;
		}

        if(sampledBitmap != null)
        {
            sampledBitmap.recycle();
        }
        return finalBitmap;
    }
}
