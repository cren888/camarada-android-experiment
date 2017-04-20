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
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

import com.aimfire.main.MainConsts;
import com.aimfire.camarada.BuildConfig;
import com.aimfire.utilities.ZipUtil;

import android.content.Context;
import android.content.res.AssetManager;
import android.media.ExifInterface;
import android.util.Log;

public class MediaScanner 
{
	private static final String TAG = "MediaScanner";

    private static ArrayList<String> sMyMediaList = null;
    private static ArrayList<String> sSharedMediaList = null;
    //private static ArrayList<String> sMyPhotoList = null;
    //private static ArrayList<String> sMyVideoList = null;
    //private static ArrayList<String> sSharedPhotoList = null;
    //private static ArrayList<String> sSharedVideoList = null;

    private static ArrayList<String> initMediaList(File dir)
    {
        ArrayList<String> retVal = new ArrayList<String>();

        /* 
         * find all (processed) full SBS images and zipped movies
         * filename: SBS_...jpg or MPG_...cvr or MPG_solo...mp4
         */
        File files[] = dir.listFiles();
        
        /*
         * sort in chronological order. newest first
         */
        if(files == null)
        {
       	    // empty list
       	    return retVal;
        }
        else if(files.length > 1)
        {
            Arrays.sort(files, new Comparator<File>(){
                public int compare(File f1, File f2)
                {
                    return Long.valueOf(f2.lastModified()).compareTo(f1.lastModified());
                } });
        }

        for (int i=0; i < files.length; i++)
        {
       	    String name = files[i].getName();
       	    if(isMovie(name) || isPhoto(name))
       	    {
       	        retVal.add(files[i].getAbsolutePath());
       	    }
        }
        if(BuildConfig.DEBUG) Log.d(TAG, "getMediaList: " + retVal.size() + 
        		" SBS images and CVR movies found in " + dir.getPath());

        return retVal;
    }

    /**
     * get paths of sbs images and cvr movies
     */
    public static ArrayList<String> getMediaList(boolean isMyMedia)
    {
   	    if(isMyMedia)
   	    {
   	        if(sMyMediaList == null)
   	        {
    	        sMyMediaList = initMediaList(MainConsts.MEDIA_3D_SAVE_DIR);
   	        }
   	        return (new ArrayList<String>(sMyMediaList));
   	    }
   	    else
   	    {
   	        if(sSharedMediaList == null)
   	        {
       	        sSharedMediaList = initMediaList(MainConsts.MEDIA_3D_SHARED_DIR);
   	        }
   	        return (new ArrayList<String>(sSharedMediaList));
   	    }
    }

    /**
     * add an item to media list
     */
    public static void addItemMediaList(String path)
    {
   	    if(path.contains(MainConsts.MEDIA_3D_SAVE_PATH))
   	    {
   	        if(sMyMediaList == null)
   	        {
    	        sMyMediaList = initMediaList(MainConsts.MEDIA_3D_SAVE_DIR);
   	        }

    	    synchronized(sMyMediaList)
    	    {
    	        sMyMediaList.add(0, path);
    	    }
   	    }
   	    else if(path.contains(MainConsts.MEDIA_3D_SHARED_PATH))
   	    {
   	        if(sSharedMediaList == null)
   	        {
    	        sSharedMediaList = initMediaList(MainConsts.MEDIA_3D_SHARED_DIR);
   	        }

    	    synchronized(sSharedMediaList)
    	    {
    	        sSharedMediaList.add(0, path);
    	    }
   	    }
   	    else
   	    {
            if(BuildConfig.DEBUG) Log.e(TAG, "addItemMediaList: path = " + path + " invalid");
   	    }
    }

    /**
     * remove an item from media list
     */
    public static void removeItemMediaList(String path)
    {
   	    if(path.contains(MainConsts.MEDIA_3D_SAVE_PATH))
   	    {
    	    if(sMyMediaList == null)
    	    {
    	        sMyMediaList = initMediaList(MainConsts.MEDIA_3D_SAVE_DIR);
    	    }

    	    synchronized(sMyMediaList)
    	    {
    	        removeItemMediaList(sMyMediaList, path);
    	    }
   	    }
   	    else if(path.contains(MainConsts.MEDIA_3D_SHARED_PATH))
   	    {
    	    if(sSharedMediaList == null)
    	    {
    	        sSharedMediaList = initMediaList(MainConsts.MEDIA_3D_SHARED_DIR);
    	    }

    	    synchronized(sSharedMediaList)
    	    {
    	        removeItemMediaList(sSharedMediaList, path);
    	    }
   	    }
   	    else
   	    {
            if(BuildConfig.DEBUG) Log.e(TAG, "removeItemMediaList: path = " + path + " invalid");
            return;
   	    }
    }
    	    
    public static void removeItemMediaList(ArrayList<String> list, String path)
    {
   	    int index = -1;
   	    for(int i=0; i<list.size(); i++)
   	    {
    	    if(list.get(i).equals(path))
    	    {
   	    	    index = i;
   	    	    break;
    	    }
   	    }
    	    
   	    if(index != -1)
   	    {
    	    list.remove(index);
   	    }
   	    else
   	    {
            if(BuildConfig.DEBUG) Log.e(TAG, "removeItemMediaList: path = " + path + " not found");
            return;
   	    }
    }

    /**
     * get non-empty sbs image list
     */
    public static ArrayList<String> getNonEmptyPhotoList(File dir)
    {
        ArrayList<String> photoList = new ArrayList<String>();

        /* 
         * find all (processed) full SBS images
         * filename: SBS_...jpg
         */
        File files[] = dir.listFiles();
        
        /*
         * sort in chronological order. newest first
         */
        if(files == null)
        {
       	    // empty list
       	    return photoList;
        }
        else if(files.length > 1)
        {
            Arrays.sort(files, new Comparator<File>(){
                public int compare(File f1, File f2)
                {
                    return Long.valueOf(f2.lastModified()).compareTo(f1.lastModified());
                } });
        }

        for (int i=0; i < files.length; i++)
        {
       	    String name = files[i].getName();
       	    if(name.startsWith("SBS") && name.endsWith(".jpg") && (files[i].length() != 0))
       	    {
       	        photoList.add(files[i].getAbsolutePath());
       	    }
        }
        if(BuildConfig.DEBUG) Log.d(TAG, "getNonEmptyPhotoList: " + photoList.size() + " images found!");
    	    return photoList;
    }

    /**
     * get processed (non-empty) movie list
     */
    public static ArrayList<String> getNonEmptyMovieList(File dir)
    {
        ArrayList<String> movieList = new ArrayList<String>();

        /* 
         * find all (processed) cvr movies
         * filename: MPG...cvr
         */
        File files[] = dir.listFiles();
        
        if(files == null)
        {
       	    // empty list
       	    return movieList;
        }
        else if(files.length > 1)
        {
            Arrays.sort(files, new Comparator<File>(){
                public int compare(File f1, File f2)
                {
                    return Long.valueOf(f2.lastModified()).compareTo(f1.lastModified());
                } });
        }

        for (int i=0; i < files.length; i++)
        {
       	    if(isMovie(files[i].getName()) && (files[i].length() != 0))
       	    {
       	        movieList.add(files[i].getAbsolutePath());
       	    }
        }
        if(BuildConfig.DEBUG) Log.i(TAG, "getNonEmptyMovieList: " + movieList.size() + " movies found!");
    	    return movieList;
    }

    /**
     * based on filename of either left or right image, return the path of 
     * full sbs image/video
     */
    public static String getProcessedSbsPath(String filename)
    {
   	    /*
         *  filename: IMG_left/right_<refCode>_m/p<offset>_<index>.mp4/jpg
         */
   	    String [] tokens = filename.split("_");
   	    return (MainConsts.MEDIA_3D_SAVE_PATH +
   	    		"SBS_" + tokens[2] + "_" + tokens[4]);
    }
    
    /**
     * based on filename of either left or right movie, return the path of 
     * cvr file 
     */
    public static String getProcessedCvrPath(String filename)
    {
   	    return (MainConsts.MEDIA_3D_SAVE_PATH + getProcessedCvrName(filename)
   	    		+ "." + MainConsts.MOVIE_EXTENSION_3D);
    }

    /**
     * get cvr file name (without extension)
     */
    public static String getProcessedCvrName(String filename)
    {
   	    /*
         *  filename: MPG_left/right_<refCode>_m/p<offset>_<index>.mp4
         */
   	    String [] tokens1 = filename.split("_");
   	    String [] tokens2 = tokens1[4].split("\\.");

   	    return (tokens1[0] + "_" + tokens1[2] + "_" + tokens2[0]);
    }

    /**
     * get cvr name (without extension) from a cvr path
     */
    public static String getMovieNameNoExt(String filePath)
    {
   	    /*
         *  filepath: PATH/MPG_<refCode>_<index>.cvr
         */
   	    String [] tokens = (new File(filePath)).getName().split("\\.");

   	    return tokens[0];
    }

    /**
     * based on filename of either left or right image, find the matching
     * image, and return the paths of left, right, full sbs images, as 
     * well as paths where rectified left/right images should be saved (if
     * we choose to save them). return null if no matching image is found.
     */
    public static String[] getImgPairPaths(String filename1, String filename2)
    {
   	    String[] retVal = null;

   	    StereoPair sp = new StereoPair(filename1.split("_"), filename2.split("_"));

   	    retVal = new String[]{sp.getLeftPath(), sp.getRightPath(), sp.getFullSbsPath(),
   	    		sp.getDebugLeftPath(), sp.getDebugRightPath()};
   	    return retVal;
    }

    public static int getMpgTimeOffsetMs(String filename1, String filename2)
    {
   	    String [] f1 = filename1.split("_");
   	    String [] f2 = filename2.split("_");

        StereoPair sp = new StereoPair(f1, f2);
   	    return sp.getTimeOffsetMs();
    }

    public static ArrayList<String> getExportedFilenames(String mediaFilename)
    {
   	    ArrayList<String> retVal = new ArrayList<String>();
   	    String prefix = null;
    	    
   	    if(mediaFilename.startsWith("SBS"))
   	    {
    	    prefix = "SBS";
   	    }
   	    else if(mediaFilename.startsWith("MPG"))
   	    {
    	    prefix = "MPG";
   	    }
   	    else
   	    {
    	    return retVal;
   	    }

   	    retVal.add(mediaFilename.replace(prefix, prefix + "A00").replace(MainConsts.MOVIE_EXTENSION_3D, MainConsts.PHOTO_EXTENSION));
   	    retVal.add(mediaFilename.replace(prefix, prefix + "A01").replace(MainConsts.MOVIE_EXTENSION_3D, MainConsts.PHOTO_EXTENSION));
   	    retVal.add(mediaFilename.replace(prefix, prefix + "A10").replace(MainConsts.MOVIE_EXTENSION_3D, MainConsts.PHOTO_EXTENSION));
   	    retVal.add(mediaFilename.replace(prefix, prefix + "A11").replace(MainConsts.MOVIE_EXTENSION_3D, MainConsts.PHOTO_EXTENSION));
   	    retVal.add(mediaFilename.replace(prefix, prefix + "F00").replace(MainConsts.MOVIE_EXTENSION_3D, MainConsts.PHOTO_EXTENSION));
   	    retVal.add(mediaFilename.replace(prefix, prefix + "F01").replace(MainConsts.MOVIE_EXTENSION_3D, MainConsts.PHOTO_EXTENSION));
   	    retVal.add(mediaFilename.replace(prefix, prefix + "F10").replace(MainConsts.MOVIE_EXTENSION_3D, MainConsts.PHOTO_EXTENSION));
   	    retVal.add(mediaFilename.replace(prefix, prefix + "F11").replace(MainConsts.MOVIE_EXTENSION_3D, MainConsts.PHOTO_EXTENSION));
   	    retVal.add(mediaFilename.replace(prefix, prefix + "H00").replace(MainConsts.MOVIE_EXTENSION_3D, MainConsts.PHOTO_EXTENSION));
   	    retVal.add(mediaFilename.replace(prefix, prefix + "H01").replace(MainConsts.MOVIE_EXTENSION_3D, MainConsts.PHOTO_EXTENSION));
   	    retVal.add(mediaFilename.replace(prefix, prefix + "H10").replace(MainConsts.MOVIE_EXTENSION_3D, MainConsts.PHOTO_EXTENSION));
   	    retVal.add(mediaFilename.replace(prefix, prefix + "H11").replace(MainConsts.MOVIE_EXTENSION_3D, MainConsts.PHOTO_EXTENSION));

   	    return retVal;
    }

    public static String getMyMediaPathFromPreviewPath(String previewPath)
    {
   	    String previewName = (new File(previewPath)).getName();

        if(is2d(previewPath))
            return MainConsts.MEDIA_3D_SAVE_PATH + previewName.replace(MainConsts.PREVIEW_EXTENSION, MainConsts.MOVIE_EXTENSION_2D);
        else
            return MainConsts.MEDIA_3D_SAVE_PATH + previewName.replace(MainConsts.PREVIEW_EXTENSION, MainConsts.MOVIE_EXTENSION_3D);
    }

    public static String getSharedMediaPathFromPreviewPath(String previewPath)
    {
   	    String previewName = (new File(previewPath)).getName();

        if(is2d(previewPath))
            return MainConsts.MEDIA_3D_SHARED_PATH + previewName.replace(MainConsts.PREVIEW_EXTENSION, MainConsts.MOVIE_EXTENSION_2D);
        else
            return MainConsts.MEDIA_3D_SHARED_PATH + previewName.replace(MainConsts.PREVIEW_EXTENSION, MainConsts.MOVIE_EXTENSION_3D);

    }

    public static String getMyMediaPathFromOrigName(String origName)
    {
        if(isPreview(origName))
        {
            if(is2d(origName))
	            return MainConsts.MEDIA_3D_SAVE_PATH + origName.replace(MainConsts.PREVIEW_EXTENSION, MainConsts.MOVIE_EXTENSION_2D);
            else
                return MainConsts.MEDIA_3D_SAVE_PATH + origName.replace(MainConsts.PREVIEW_EXTENSION, MainConsts.MOVIE_EXTENSION_3D);
        }
        else
        {
		    return MainConsts.MEDIA_3D_SAVE_PATH + origName;
        }
    }

    public static String getSharedMediaPathFromOrigName(String origName)
    {
        if(isPreview(origName))
        {
            if(is2d(origName))
	            return MainConsts.MEDIA_3D_SHARED_PATH + origName.replace(MainConsts.PREVIEW_EXTENSION, MainConsts.MOVIE_EXTENSION_2D);
            else
                return MainConsts.MEDIA_3D_SHARED_PATH + origName.replace(MainConsts.PREVIEW_EXTENSION, MainConsts.MOVIE_EXTENSION_3D);
        }
        else
        {
		    return MainConsts.MEDIA_3D_SHARED_PATH + origName;
        }
    }

    public static String getTargetPathFromOrigName(String origName)
    {
        if(isPreview(origName))
	    {
            return MainConsts.MEDIA_3D_THUMB_PATH + origName;
	    }
	    else
	    {
            return MainConsts.MEDIA_3D_SHARED_PATH + origName;
	    }
    }

    public static String getThumbPathFromMediaPath(String mediaPath)
    {
        return MainConsts.MEDIA_3D_THUMB_PATH +
                getMovieNameNoExt(mediaPath) + "." + MainConsts.PHOTO_EXTENSION;
    }

    public static String getPreviewPathFromMediaPath(String mediaPath)
    {
        return MainConsts.MEDIA_3D_THUMB_PATH +
                getMovieNameNoExt(mediaPath) + "." + MainConsts.PREVIEW_EXTENSION;
    }

    public static String getPreviewPathFromOrigName(String origName)
    {
        if(isPreview(origName))
        {
	        return MainConsts.MEDIA_3D_THUMB_PATH + origName;
        }
        else if(isMovie(origName))
        {
            if(is2d(origName))
	            return MainConsts.MEDIA_3D_THUMB_PATH + origName.replace(MainConsts.MOVIE_EXTENSION_2D, MainConsts.PREVIEW_EXTENSION);
            else
                return MainConsts.MEDIA_3D_THUMB_PATH + origName.replace(MainConsts.MOVIE_EXTENSION_3D, MainConsts.PREVIEW_EXTENSION);
        }
        else 
        {
        	    // shouldn't be here
	        return null;
        }
    }

    public static String getThumbPathFromOrigName(String origName)
    {
        if(isPreview(origName))
        {
	        return MainConsts.MEDIA_3D_THUMB_PATH + origName.replace(MainConsts.PREVIEW_EXTENSION, MainConsts.PHOTO_EXTENSION);
        }
        else if(is2dMovie(origName))
        {
	        return MainConsts.MEDIA_3D_THUMB_PATH + origName.replace(MainConsts.MOVIE_EXTENSION_2D, MainConsts.PHOTO_EXTENSION);
        }
        else if(is3dMovie(origName))
        {
            return MainConsts.MEDIA_3D_THUMB_PATH + origName.replace(MainConsts.MOVIE_EXTENSION_3D, MainConsts.PHOTO_EXTENSION);
        }
        else
        {
	        return MainConsts.MEDIA_3D_THUMB_PATH + origName;
        }
    }

	public static boolean isMovie(String filePath)
	{
		return filePath.endsWith(MainConsts.MOVIE_EXTENSION_2D) || filePath.endsWith(MainConsts.MOVIE_EXTENSION_3D);
	}

	public static boolean is2dMovie(String filePath)
	{
		return filePath.endsWith(MainConsts.MOVIE_EXTENSION_2D);
	}

    public static boolean is3dMovie(String filePath)
    {
	    return filePath.endsWith(MainConsts.MOVIE_EXTENSION_3D);
    }

    public static boolean is2d(String pathOrName)
    {
        return pathOrName.contains("solo");
    }

    public static boolean isPreview(String filePath)
    {
	    return filePath.endsWith(MainConsts.PREVIEW_EXTENSION);
    }

    public static boolean isPhoto(String filePath)
    {
	    return filePath.endsWith(MainConsts.PHOTO_EXTENSION);
    }

    public static boolean isLink(String filePath)
    {
	    return filePath.endsWith(MainConsts.LINK_EXTENSION);
    }

    public static boolean isEmpty(String filePath)
    {
   	    File file = new File(filePath);
        return (file.length() == 0);
    }

    public static boolean oldCvrExists()
    {
   	    boolean found = false;

        if(MainConsts.MEDIA_3D_THUMB_DIR.exists())
        {
            File files[] = MainConsts.MEDIA_3D_THUMB_DIR.listFiles();
        
            if(files != null)
            {
                for (int i=0; i < files.length; i++)
                {
       	            if(files[i].getName().endsWith("png"))
       	            {
   	            	    found = true;
   	            	    break;
       	            }
                }
            }
        }
        return found;
    }

    /**
     * returns true if ArrayList a and b have same content (ignore ordering).
     * returns false otherwise.
     */
    public static boolean isContentSame(ArrayList<String>a, ArrayList<String> b)
    {
   	    if((a == null) || (b == null) || (a.size() != b.size()))
   	    {
    	    return false;
   	    }

   	    for(String i : a)
   	    {
    	    boolean found = false;
    	    for(String j: b)
    	    {
  	    	    if(i.equals(j))
   	    	    {
    	    	    found = true;
    	    	    break;
   	    	    }
    	    }
    	    if(!found)
    	    {
  	    	    return false;
    	    }
   	    }
   	    return true;
    }

    public static void deleteFile(String path)
    {
		File mediaFile = new File(path);

	    if(path.endsWith(MainConsts.MOVIE_EXTENSION_3D))
	    {
    	    if(mediaFile.length() != 0)
    	    {
   	    	    /*
   	    	     * if this is not a placeholder file
   	    	     */
    	        ArrayList<String> cachedFilenames = null;
    	        try {
				    cachedFilenames = ZipUtil.getZipEntries(path);
			    } catch (IOException e) {
                    if(BuildConfig.DEBUG) Log.d(TAG, "deleteFile: unable to get zip entries");
			    }
	    	        
    	        if(cachedFilenames != null)
    	        {
    	            for(String filename: cachedFilenames)
    	            {
	                    File f = new File(MainConsts.MEDIA_3D_ROOT_PATH + filename);
	                    f.delete();
    	            }
    	        }
    	    }
    	    else
    	    {
	            File previewFile = new File(MainConsts.MEDIA_3D_THUMB_PATH + 
            		mediaFile.getName().replace(MainConsts.MOVIE_EXTENSION_3D, MainConsts.PREVIEW_EXTENSION));
                previewFile.delete();

	            File thumbFile = new File(MainConsts.MEDIA_3D_THUMB_PATH + 
            		mediaFile.getName().replace(MainConsts.MOVIE_EXTENSION_3D, MainConsts.PHOTO_EXTENSION));;
                thumbFile.delete();
    	    }
	    }
		else if(path.endsWith(MainConsts.MOVIE_EXTENSION_2D))
		{
			File previewFile = new File(MainConsts.MEDIA_3D_THUMB_PATH +
				mediaFile.getName().replace(MainConsts.MOVIE_EXTENSION_2D, MainConsts.PREVIEW_EXTENSION));
			previewFile.delete();

			File thumbFile = new File(MainConsts.MEDIA_3D_THUMB_PATH +
				mediaFile.getName().replace(MainConsts.MOVIE_EXTENSION_2D, MainConsts.PHOTO_EXTENSION));;
			thumbFile.delete();
		}
	    else
	    {
	        File thumbFile = new File(MainConsts.MEDIA_3D_THUMB_PATH + mediaFile.getName());
            thumbFile.delete();
	    }

   	    ArrayList<String> exportedFilenames = getExportedFilenames(mediaFile.getName());
        for(String filename: exportedFilenames)
        {
            File f = new File(MainConsts.MEDIA_3D_EXPORT_PATH + filename);
            f.delete();
        }

        mediaFile.delete();
        removeItemMediaList(path);
    }

    public static void insertExifInfo(String path, String info)
    {
        ExifInterface exif;
		try {
			exif = new ExifInterface(path);
			/*
			 * TAG_USER_COMMENT only added in API 24
			 */
            //exif.setAttribute(ExifInterface.TAG_USER_COMMENT, photoUrl);
			if(info != null)
			{
                exif.setAttribute("UserComment", info);
			}
            exif.saveAttributes();
		} catch (IOException e) {
            if(BuildConfig.DEBUG) Log.e(TAG, "insertExifInfo: " + e.getMessage());
		}
    }

    public static String extractExifInfo(String path)
    {
        ExifInterface exif;
		try {
		    exif = new ExifInterface(path);
        } catch (Exception e) {
            if(BuildConfig.DEBUG) Log.e(TAG, "extractExifInfo: " + e.getMessage());

            String msg = e.getMessage();
            return null;
        }

	    /*
	     * TAG_USER_COMMENT only added in API 24
	     */
        //return exif.getAttribute(ExifInterface.TAG_USER_COMMENT);
        return exif.getAttribute("UserComment");
    }

    /**
     * if we decide to put some sample pictures in the asset directory,
     * we need to initialize them here
     */
    public static void moveAsset(Context ctx)
    {
        AssetManager am = ctx.getAssets();
        String[] files = null;
		try {
			files = am.list("media");
		} catch (IOException e) {
			// NOOP
			return;
		}

        for(String filename : files) 
        {
            InputStream in = null;
            OutputStream out = null;
            try {
                in = am.open("media/" + filename);
                File outFile = new File(MainConsts.MEDIA_3D_SAVE_PATH, filename);
                if(!outFile.exists())
                {
                    out = new FileOutputStream(outFile);
                    copyFile(in, out);
                }
            } catch(IOException e) {
                if(BuildConfig.DEBUG) Log.e("tag", "Failed to copy asset file: " + filename, e);
            }     
            finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        // NOOP
                    }
                }
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException e) {
                        // NOOP
                    }
                }
            }  
        }
    }

    private static void copyFile(InputStream in, OutputStream out) throws IOException 
    {
        byte[] buffer = new byte[1024];
        int read;
        while((read = in.read(buffer)) != -1)
        {
            out.write(buffer, 0, read);
        }
    }
}