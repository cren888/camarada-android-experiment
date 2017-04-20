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
import java.io.IOException;
import java.io.InputStream;

import com.aimfire.main.MainConsts;
import com.aimfire.camarada.BuildConfig;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.util.Log;

public class AnaglyphGen extends Thread
{
	private static final String TAG = "AnaglyphGen";
	private final Context context;
	private final int numOfPairs;
	
    public AnaglyphGen(Context ctx, int cnt)
    {
    	    context = ctx;
    	    numOfPairs = cnt;
    }

    public void run() 
    {
        for(int i=0; i<numOfPairs; i++)
        {
            Bitmap lbmp = openAsset(context, "pictures/" + Integer.toString(i) + "l.jpg");
            Bitmap rbmp = openAsset(context, "pictures/" + Integer.toString(i) + "r.jpg");
            
            // make lbmp mutable
            lbmp = lbmp.copy(lbmp.getConfig(), true);
    
            String anaglyphFilename = MainConsts.MEDIA_3D_EXPORT_PATH + "Anaglyph_" + Integer.toString(i) + ".PNG";
            File file = new File(anaglyphFilename);
            if(!file.exists())
            {
                genAnaglyph(anaglyphFilename, lbmp, rbmp);
            }
        }
    }
    
    public Bitmap openAsset(final Context context, final String filePath)
    {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;   // No pre-scaling

        AssetManager assetManager = context.getAssets();

        InputStream istr;
        try {
            istr = assetManager.open(filePath);
            return BitmapFactory.decodeStream(istr);
        } catch (IOException e) {
            // handle exception
        	    return null;
        }
    }

	/**
	 * convert a pair of bitmaps into an anaglyph. anaglyph is written to the leftBmp 
	 * (which is mutable passed in) and compressed into a PNG
	 * @param outputFilename
	 * @param leftBmp
	 * @param rightBmp
	 */
	public void genAnaglyph (String outputFilename, Bitmap leftBmp, Bitmap rightBmp)
	{
        int width = leftBmp.getWidth();
        int height = leftBmp.getHeight();
        int rwidth = rightBmp.getWidth();
        int rheight = rightBmp.getHeight();
        
        if((width!=rwidth) || (height!=rheight))
        {
        	    if(BuildConfig.DEBUG) Log.e(TAG, "left/right image dimensions don't match! stop converting.");
        }
        int size = width*height;
        
        int[] lpixels = new int[size];
        int[] rpixels = new int[size];
        leftBmp.getPixels(lpixels, 0, width, 0, 0, width, height);
        rightBmp.getPixels(rpixels, 0, width, 0, 0, width, height);
    
        for (int i = 0; i < size; i++) 
        {
        	    try{
        	    	    //left: cyan; right: red
                //lpixels[i] = Color.argb(Color.alpha(lpixels[i]), Color.red(rpixels[i]), Color.green(lpixels[i]), Color.blue(lpixels[i]));

        	    	    //left: red; right: cyan
                lpixels[i] = Color.argb(Color.alpha(lpixels[i]), Color.red(lpixels[i]), Color.green(rpixels[i]), Color.blue(rpixels[i]));
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    
        leftBmp.setPixels(lpixels, 0, width, 0, 0, width, height);
        
        FileOutputStream fos;
        try {
            fos = new FileOutputStream(outputFilename);
            leftBmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();

            // Recycle the bitmaps
            if(leftBmp != null)
                leftBmp.recycle();
            if(rightBmp != null)
                rightBmp.recycle();
        } catch (IOException e) {
            e.printStackTrace();
        }
	}
}
