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
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import com.aimfire.camarada.BuildConfig;
import com.aimfire.camarada.R;
import com.aimfire.constants.ActivityCode;
import com.aimfire.main.MainConsts;
import com.aimfire.drive.DownloadFileFragment;
import com.aimfire.drive.UploadFileActivity;
import com.aimfire.drive.DownloadFileFragment.DownloadStatusListener;
import com.aimfire.drive.service.FileDownloaderService;
import com.aimfire.gallery.cardboard.MovieActivity;
import com.aimfire.gallery.cardboard.PhotoActivity;
import com.aimfire.gallery.service.MovieProcessor;
import com.aimfire.main.MainActivity;
import com.aimfire.utilities.FileUtils;
import com.aimfire.utilities.ZipUtil;
import com.google.firebase.analytics.FirebaseAnalytics;

import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.LabeledIntent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.LayoutParams;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewConfiguration;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class GalleryActivity extends AppCompatActivity implements DownloadStatusListener
{
	private static final String TAG = "GalleryActivity";
    private static final boolean VERBOSE = true;

    private static final String KEY_PAGER_POSITION = "KPP";
    private static final String KEY_PAGER_MY_MEDIA = "KPMM";

    /*
     * this will control memory usage by ViewPager. smaller number will
     * mean less memory usage. however it might impact UI performance
     */
    private static final int MAX_PAGE = 3;
    
    /**
     * Whether or not the action bar should be auto-hidden after
     * {@link #AUTO_HIDE_DELAY_SHORT_MILLIS} milliseconds.
     */
    private static final boolean AUTO_HIDE = true;

    /**
     * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
     * user interaction before hiding the system UI.
     */
    private static final int AUTO_HIDE_DELAY_SHORT_MILLIS = 5000;
    private static final int AUTO_HIDE_DELAY_LONG_MILLIS = 15000;

    /*
     * firebase analytics
     */
    private FirebaseAnalytics mFirebaseAnalytics;

    /*
     * display modes
     */
    private DisplayMode mDisplayMode;
    private boolean mDisplaySwap;
    private boolean mDisplayColor;

    /*
     * view pager for full image display
     */
    private ViewPager mViewPager;
    private ArrayList<String> mMediaList;
    private ImageView mNoMedia;
    private String mMediaPath;
    private String mMediaName;
    private String mPreviewPath;
    private String mPreviewName;

    /*
     * whether we see our media or shared media
     */
    private boolean mIsMyMedia;

    /*
     * cardboard button
     */
    private FloatingActionButton mCardboardButton;
    
    Handler mHideHandler = new Handler();
    Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() 
        {
            getSupportActionBar().hide();
        }
    };

    /**
     * BroadcastReceiver for download completion and status messages.
     */
    private BroadcastReceiver mDownloadMsgReceiver = new BroadcastReceiver() 
    {
        @Override
        public void onReceive(Context context, Intent intent) 
        {
   	        if(BuildConfig.DEBUG) Log.d(TAG, "mDownloadMsgReceiver");

            int what = intent.getIntExtra(MainConsts.EXTRA_WHAT, -1);
            String path = intent.getStringExtra(MainConsts.EXTRA_PATH);
            boolean isSuccess = intent.getBooleanExtra(MainConsts.EXTRA_STATUS, false);
            int percentage = intent.getIntExtra(MainConsts.EXTRA_MSG, -1);

            switch(what)
            {
 		    case MainConsts.MSG_FILE_DOWNLOADER_COMPLETION:
                if(!mIsMyMedia)
                {
                    if(!isSuccess)
                    {
               	        /*
               	         * we either failed to download a file or the downloaded file
               	         * is empty (which means upload on the other end is not done)
               	         */
                        updateViewPager(null, mViewPager.getCurrentItem(), -1);
                    }
                    else
                    {
   	                    /*
   	                     * we come here if we successfully downloaded a file
   	                     */
           	            if(MediaScanner.isPreview(path))
           	            {
                            intent.putExtra(MainConsts.EXTRA_PATH, 
                       		    MediaScanner.getSharedMediaPathFromPreviewPath(path));
           	            }
       	                parseIntent(intent);
                    }
                }
                break;
 		    case MainConsts.MSG_FILE_DOWNLOADER_PROGRESS:
                if(!mIsMyMedia)
                {
                    updateViewPager(path, -1, percentage);
                }
                break;
 		    case MainConsts.MSG_FILE_DOWNLOADER_SAMPLES_START:
                if(!mIsMyMedia)
                {
                    updateViewPager(null, mViewPager.getCurrentItem(), -1);
                }
	    	    break;
		    default:
	    	    break;
            }
        }
    };

    /**
     * BroadcastReceiver for incoming media processor messages.
     */
    private BroadcastReceiver mPhotoProcessorMsgReceiver = new BroadcastReceiver() 
    {
        @Override
        public void onReceive(Context context, Intent intent) 
        {
            int messageCode = intent.getIntExtra(MainConsts.EXTRA_WHAT, -1);
            switch(messageCode)
            {
            case MainConsts.MSG_PHOTO_PROCESSOR_RESULT:
   	            /*
   	             * ignore if we are showing shared media files
   	             */
                if(mIsMyMedia)
                {
           	        parseIntent(intent);
                }
           	    break;
            case MainConsts.MSG_PHOTO_PROCESSOR_ERROR:
                String filePath = intent.getStringExtra(MainConsts.EXTRA_PATH);

           		if(BuildConfig.DEBUG) if(VERBOSE) Log.d(TAG, "onReceive: " + filePath +
       				" auto alignment error");

                if(mIsMyMedia)
                {
           		    /*
           		     * if we happen to be on the page waiting for this file, need to update
           		     */
                    updateViewPager(null, mViewPager.getCurrentItem(), -1);
                }
                break;
            default:
           	    break;
            }
        }
    };

    /**
     * BroadcastReceiver for incoming media processor messages.
     */
    private BroadcastReceiver mMovieProcessorMsgReceiver = new BroadcastReceiver() 
    {
        @Override
        public void onReceive(Context context, Intent intent) 
        {
            int messageCode = intent.getIntExtra(MainConsts.EXTRA_WHAT, -1);
            switch(messageCode)
            {
            case MainConsts.MSG_MOVIE_PROCESSOR_RESULT:
   	            /*
   	             * ignore if we are showing shared media files
   	             */
                if(mIsMyMedia)
                {
           	        parseIntent(intent);
                }
           	    break;
            case MainConsts.MSG_MOVIE_PROCESSOR_ERROR:
                String filePath = intent.getStringExtra(MainConsts.EXTRA_PATH);

           		if(BuildConfig.DEBUG) if(VERBOSE) Log.d(TAG, "onReceive: " + filePath +
       				" auto alignment error");

                if(mIsMyMedia)
                {
           		    /*
           		     * if we happen to be on the page waiting for this file, need to update
           		     */
                    updateViewPager(null, mViewPager.getCurrentItem(), -1);
                }
                break;
            default:
           	    break;
            }
        }
    };

    /**
     * Schedules a call to hide() in [delay] milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) 
    {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }

    private OnTouchListener otl = new OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) 
        {
            getSupportActionBar().show();
            if (AUTO_HIDE) 
            {
                // Schedule a hide().
                delayedHide(AUTO_HIDE_DELAY_SHORT_MILLIS);
            }
            return false;
        }
    };

    OnPageChangeListener opcl = new OnPageChangeListener() {
        @Override
   		public void onPageScrollStateChanged(int state)
        {
   		   //Called when the scroll state changes.
   		}

   		@Override
   		public void onPageScrolled(int position,
   		    float positionOffset, int positionOffsetPixels)
   		{
   		   //This method will be invoked when the current page is scrolled,
   		   //either as part of a programmatically initiated smooth scroll
   		   //or a user initiated touch scroll.
        }

   		@Override
   		public void onPageSelected(int i)
    		{
    			shareUpdate(i);
    		}
   	};

    private void loadDisplayPrefs() 
    {
        SharedPreferences settings =
            getSharedPreferences(getString(R.string.settings_file), Context.MODE_PRIVATE);

        if (settings != null) 
        {
            mDisplaySwap = settings.getBoolean(MainConsts.DISPLAY_SWAP_PREFS_KEY, false);
            mDisplayColor = settings.getBoolean(MainConsts.DISPLAY_COLOR_PREFS_KEY, true);
            mDisplayMode = DisplayMode.values()[settings.getInt(MainConsts.DISPLAY_MODE_PREFS_KEY,
                DisplayMode.Anaglyph.getValue())];
        }
    }

    private void updateDisplayPrefs() 
    {
        SharedPreferences settings = getSharedPreferences(
            getString(R.string.settings_file), Context.MODE_PRIVATE);
        if (settings != null) 
        {
            SharedPreferences.Editor editor = settings.edit();
            editor.putBoolean(MainConsts.DISPLAY_SWAP_PREFS_KEY, mDisplaySwap);
            editor.putBoolean(MainConsts.DISPLAY_COLOR_PREFS_KEY, mDisplayColor);
            editor.putInt(MainConsts.DISPLAY_MODE_PREFS_KEY, mDisplayMode.getValue());
            editor.commit();
        }
    }

    /**
     * onClick handler for "cardboard" button.
     */
    OnClickListener oclCardboard = new OnClickListener() {
        @Override
        public void onClick(View view) 
        {
   	        mDisplayMode = DisplayMode.Cardboard;
   	        switchDisplayMode();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) 
    {
        supportRequestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY);

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_gallery);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        /*
         * load display mode, swap and color mode that were last used
         */
        loadDisplayPrefs();

        /*
         * show overflow button even if we have a physical menu button
         */
        forceOverflowButton();

        /*
         * Obtain the FirebaseAnalytics instance.
         */
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

        /*
         * initialize the view pager with current media we have
         */
        initViewPager();

        mNoMedia = (ImageView) findViewById(R.id.no_media_bg);
        mNoMedia.setOnTouchListener(otl);

        mCardboardButton = (FloatingActionButton) findViewById(R.id.cardboardFAB);
        mCardboardButton.setOnClickListener(oclCardboard);

        if (savedInstanceState != null)
        {
            mIsMyMedia = savedInstanceState.getBoolean(KEY_PAGER_MY_MEDIA);
            updateViewPager(null, savedInstanceState.getInt(KEY_PAGER_POSITION), -1);
        }
        else
        {
            /*
             * parse the intent
             */
            Intent intent = getIntent();
            parseIntent(intent);
        }

        showHideView();

        /*
         * initial command to hide action bar
         */
        if(AUTO_HIDE)
        {
            delayedHide(AUTO_HIDE_DELAY_SHORT_MILLIS);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) 
    {
   	    if(BuildConfig.DEBUG) Log.d(TAG, "onNewIntent");
        parseIntent(intent);
    }

    /**
     * Override Activity lifecycle method.
     */
    @Override
    protected void onResume() 
    {
   	    super.onResume();

        if(mDisplayMode == DisplayMode.Cardboard)
        {
       	    /*
       	     * reload display preferences (that's different from Cardboard)
       	     */
       	    loadDisplayPrefs();
   	        invalidateOptionsMenu();
       	    switchDisplayMode();
        }

//      /*
//       * upon resuming, the page that was being displayed may have
//       * been deleted, so need to update the view pager
//       */
//      updateViewPager(null, mViewPager.getCurrentItem(), -1);

   	    /*
   	     * register for intents sent by the media processor service
   	     */
        LocalBroadcastManager.getInstance(this).registerReceiver(mPhotoProcessorMsgReceiver,
            new IntentFilter(MainConsts.PHOTO_PROCESSOR_MESSAGE));

        LocalBroadcastManager.getInstance(this).registerReceiver(mMovieProcessorMsgReceiver,
            new IntentFilter(MainConsts.MOVIE_PROCESSOR_MESSAGE));

	    /*
	     * register for intents sent by the download completion service
	     */
        LocalBroadcastManager.getInstance(this).registerReceiver(mDownloadMsgReceiver,
            new IntentFilter(MainConsts.FILE_DOWNLOADER_MESSAGE));
    }

    /**
     * Override Activity lifecycle method.
     */
    @Override
    protected void onPause() 
    {
   	    super.onPause();

   	    if(BuildConfig.DEBUG) if(VERBOSE) Log.d(TAG, "onPause: unregister local broadcast receiver");
   	    /*
   	     * de-register for intents sent by the download completion service
   	     */
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mDownloadMsgReceiver);

   	    /*
   	     * de-register for intents sent by the media processor service
   	     */
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mPhotoProcessorMsgReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMovieProcessorMsgReceiver);
    }

    /**
     * Override Activity lifecycle method.
     */
    @Override
    protected void onDestroy() 
    {
   	    if(mViewPager != null)
   	    {
            mViewPager.setAdapter(null);
   	    }
   	    super.onDestroy();
    }
    
//  @Override
//  public void onBackPressed()
//  {
//      finish();
//  }

    @Override
    public void onWindowFocusChanged (boolean hasFocus) 
    {
        super.onWindowFocusChanged(hasFocus);
//      if (hasFocus)
//      {
//     	    forceFullScreen();
//      }
    }

    /**
     * this activity (GalleryActivity) can receive intent from: 
     * 1. ThumbsFragment - when a thumbnail is selected
     * 2. PhotoProcessor - when a photo is finished processing
     * 3. MovieProcessor - when a movie is finished processing
     * 4. user click a .cvr file from file browser
     * 5. user click link (of amifire-vr scheme) in browser
     * 6. user click link (with our domain name) in mail or other programs (other than browser)
     * 
     * in the first three cases, the intent we get will have extras EXTRA_PATH and EXTRA_MSG.
     * in the fourth case, we will have intent with getData with path to the .cvr file (or occasionally jpg file).
     * in the last two cases, we will have a link that we need to parse.
     */
    private void parseIntent(Intent intent)
    {
   	    /*
   	     * set mIsMyMedia according to intent. we may or may not have EXTRA_MSG
   	     * passed in. in case we don't have it passed in we will set it to false
   	     * here by default. it may get overriden after we parsed the intent.
   	     */
        mIsMyMedia = intent.getBooleanExtra(MainConsts.EXTRA_MSG, false);

        String filePath = null;

        Uri uri = intent.getData();

        if (uri != null) 
        {
       	    if(uri.getScheme().equalsIgnoreCase("file"))
       	    {
   	    	    filePath = handleLocalOpen(uri);
       	    }
       	    else if(uri.getScheme().equalsIgnoreCase("https") ||
   	    		    uri.getScheme().equalsIgnoreCase("http")  ||
   	    		    uri.getScheme().equalsIgnoreCase("aimfire-vr"))
       	    {
                filePath = handleDownload(uri);
       	    }
        }
        else
        {
            filePath = intent.getStringExtra(MainConsts.EXTRA_PATH);
        }
        	
        if(filePath == null)
        {
            if(BuildConfig.DEBUG) Log.e(TAG, "parseIntent: filePath is null");
        	    return;
        }
            
        /*
         * update view pager - if file is already in the view pager, this will 
         * updates its view only. if it is not, this will refresh the entire
         * view pager to add it.
         */
        updateViewPager(filePath, -1, -1);
    }

    /**
     * handle opening of a cvr file already on the phone. if it is not in our directory
     * structure, we copy it there first.
     * 
     * @param uri
     * @return path to the cvr file in our directory structure
     */
    private String handleLocalOpen(Uri uri) {
        String path = null;

   	    /*
	     * we are opened from file broswer
	     */
        File f = new File(uri.getPath());
        if (f != null) {
            path = f.getAbsolutePath();
        }
        
        /*
         * we support file open of cvr file only
         */
        if ((path == null) || !MediaScanner.isMovie(path)) {
            Toast.makeText(this, R.string.error_incorrect_link, Toast.LENGTH_LONG).show();
            return null;
        }

        if (path.contains(MainConsts.MEDIA_3D_SAVE_PATH))
        {
            mIsMyMedia = true;
        }
        else if (path.contains(MainConsts.MEDIA_3D_SHARED_PATH))
        {
            mIsMyMedia = false;
        }
        else
        {
            mIsMyMedia = false;

       	    /*
       	     * in case we got here directly without going thru MainActivity
       	     * first, it's possible we don't have storage initialized yet.
       	     */
            if (FileUtils.initStorage()) {
                /*
                 * we are launched from external apps by file type. move the file 
   	             * to our root dir. if rename fails, we will have to do the actual 
   	             * copy (rather than rename, as we may be copying the file from
   	             * internal to external storage; or we don't have write permission
   	             * on the source directory)
                 */
                String newFilePath = MainConsts.MEDIA_3D_SHARED_PATH + (new File(path)).getName();
                boolean success = false;
                try {
                    File from = (new File(path));
                    File to = (new File(newFilePath));
                    success = from.renameTo(to);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (!success) {
                    FileUtils.copyFile(path, newFilePath);
                }

                path = newFilePath;

                /*
                 * make MediaScanner aware of the new file
                 */
                MediaScanner.addItemMediaList(path);
            } else {
                Toast.makeText(this, R.string.error_accessing_storage, Toast.LENGTH_LONG).show();
                return null;
            }
        }
        
        /*
         * import/convert the file if it has the old format (.png preview frame)
         */
        if (MediaScanner.is3dMovie(path))
        {
            convertCvr(path);
        }

        return path;
    }

    private void convertCvr(String path)
    {
   	    boolean isOldCvr = false;
   	    ArrayList<String> filenames = new ArrayList<String>();

	    try {
    	    filenames = ZipUtil.getZipEntries(path);
			for(String i: filenames)
			{
			    if(i.endsWith("png"))
			    {
		    	    isOldCvr = true;
		    	    break;
			    }
			}
		} catch (IOException e) {
            if(BuildConfig.DEBUG) if(VERBOSE) Log.e(TAG, "convertCvr: expection " + e.getMessage());
            return;
		}
	    
	    if(!isOldCvr)
	    {
    	    return;
	    }
	    
        DecodeCvrTask cvrTask = new DecodeCvrTask(path);
        cvrTask.execute();
	    try {
            cvrTask.get();
		} catch (CancellationException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		}
	    
	    String leftFilename = null;
	    String rightFilename = null;
		for(String i: filenames)
		{
			if(i.contains("left") && i.endsWith("mp4"))
			{
				leftFilename = i.replace("cache/", "");
			}
			else if(i.contains("right") && i.endsWith("mp4"))
			{
				rightFilename = i.replace("cache/", "");;
			}
		}
	    int timeOffsetMs = MediaScanner.getMpgTimeOffsetMs(leftFilename, rightFilename);

	    String leftFilepath = MainConsts.MEDIA_3D_RAW_PATH + leftFilename;
	    String rightFilepath = MainConsts.MEDIA_3D_RAW_PATH + rightFilename;

        Intent serviceIntent = new Intent(this, MovieProcessor.class);
        serviceIntent.putExtra("lname", leftFilepath);
        serviceIntent.putExtra("rname", rightFilepath);

        serviceIntent.putExtra(MainConsts.EXTRA_SCALE, 1.0f);
		serviceIntent.putExtra(MainConsts.EXTRA_FACING, 0/*assuming back facing*/);
		serviceIntent.putExtra(MainConsts.EXTRA_OFFSET, timeOffsetMs);

        startService(serviceIntent);
    }

    /**
     * parse the link that led us here. check if the file already exists. if not, start
     * DownloadFileFragment. 
     * 
     * @param uri
     * @return path to the file download location. in the case of a chained download, we 
     * will always return the path to the cvr file (rather than the preview file)
     */
    private String handleDownload(Uri uri)
    {
        if(!isDeviceOnline())
        {
		    Toast.makeText(this, R.string.error_no_network, Toast.LENGTH_LONG).show();
       	    return null;
        }

	    String mePath = null;
	    String sharePath = null;

	    String resId = uri.getQueryParameter("id");
        String origName = uri.getQueryParameter("name");

        if((resId == null) || (origName == null))
        {
            Toast.makeText(this, R.string.error_incorrect_link, Toast.LENGTH_LONG).show();
   	        return null;
        }
    
        saveDriveFileRecord(origName, resId);

        /*
         * we could have the size if this is the cvr download (chained-mode)
         */
        int size = -1;
        String sizeStr = uri.getQueryParameter("size");
        if(sizeStr != null)
        {
   	        try {
                size = Integer.parseInt(sizeStr);
            } catch (Exception e) {
                Toast.makeText(this, R.string.error_incorrect_link, Toast.LENGTH_LONG).show();
   	            return null;
            }
        }
    
	    /*
	     * we check if the file already exists. file name may be:
	     * 
	     * SBS...jpg - this is a 3D SBS image, non-chained mode
	     * MPG...jpeg - this is a preview frame of movie, chained mode
	     * 
	     * if a jpg or movie file exists in my media or shared media path, then we
	     * either have successfully downloaded the photo or preview or we are 
	     * currently downloading it - remember we delete the placeholder jpg or 
	     * cvr file in case of failure. 
	     * 
	     * the link may specify a movie (cvr) file - we have the legacy code here 
	     * for it, but we currently don't use it. in that case the file name is:
	     * 
	     * MPG...cvr - this is a 3D cvr movie, non-chained mode
	     */
        mePath = MediaScanner.getMyMediaPathFromOrigName(origName);
        sharePath = MediaScanner.getSharedMediaPathFromOrigName(origName);

	    if (new File(mePath).exists())
	    {
	        /*
	         * this only happens if we click on the link that we sent to ourselves
	         */
            if(BuildConfig.DEBUG) Log.d(TAG, "handleDownload: file already exists in My Media!");
            mIsMyMedia = true;
            return mePath;
        }
	    else if (new File(sharePath).exists())
	    {
	        /*
	         * this happens if we have downloaded this file before
	         */
            if(BuildConfig.DEBUG) Log.d(TAG, "handleDownload: file already exists in Shared Media!");
            mIsMyMedia = false;
            return sharePath;
	    }
	    
	    /*
	     * the file doesn't exist and we need to download it.
	     * 
         * create an empty, placeholder file, so GalleryActivity/ThumbsFragment knows 
         * its existence and show a progress bar while this file is downloaded
         */
        try {
		    MediaScanner.addItemMediaList(sharePath);
	        (new File(sharePath)).createNewFile();
        } catch (IOException e) {
	        Toast.makeText(this, R.string.error_accessing_storage, Toast.LENGTH_LONG).show();
	        return null;
		}

        if(MediaScanner.isMovie(origName))
        {
            /*
             * legacy code - download cvr from a link.
             */
            addDownloadFileFragment(resId, origName, size);
        }
        else
        {
            Intent serviceIntent = new Intent(this, FileDownloaderService.class);

            if(MediaScanner.isPreview(origName))
            {
                serviceIntent.putExtra(MainConsts.EXTRA_PATH, 
               		MediaScanner.getPreviewPathFromOrigName(origName));
            }
            else
            {
                serviceIntent.putExtra(MainConsts.EXTRA_PATH, sharePath);
            }
            startService(serviceIntent);
        }
        
        return sharePath;
    }

    private void forceFullScreen()
    {
        if (Build.VERSION.SDK_INT < 16) 
        {
            /*
             * if the Android version is lower than Jellybean, use this call to hide
             * the status bar.
             */
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                                 WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        else
        {
       	    View decorView = getWindow().getDecorView();
       	    /*
       	     * hide the status bar.
       	     */
       	    int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN;
       	    decorView.setSystemUiVisibility(uiOptions);
        }
    }

    /**
     * c.f. "http://stackoverflow.com/questions/9286822/how-to-force-use-of-overflow-
     * menu-on-devices-with-menu-button"
     */
    private void forceOverflowButton()
    {
        try {
            ViewConfiguration config = ViewConfiguration.get(this);
            Field menuKeyField = ViewConfiguration.class.getDeclaredField("sHasPermanentMenuKey");
            if(menuKeyField != null) 
            {
                menuKeyField.setAccessible(true);
                menuKeyField.setBoolean(config, false);
            }
        } catch (Exception ex) {
            // Ignore
        }
    }

    /**
     * Override Activity lifecycle method.
     */
    @Override
    protected void onSaveInstanceState (Bundle outState) 
    {
        outState.putInt(KEY_PAGER_POSITION, mViewPager.getCurrentItem());
        outState.putBoolean(KEY_PAGER_MY_MEDIA, mIsMyMedia);
        super.onSaveInstanceState(outState);
    }

    /**
     * Override Activity lifecycle method.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) 
    {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.activity_gallery, menu);

        if(mViewPager != null)
        {
   	        shareUpdate(mViewPager.getCurrentItem());
        }

   	    /*
         * To show icon (instead of only text) in action bar overflow
         */
        if(menu.getClass().getSimpleName().equals("MenuBuilder"))
        {
            try{
                Method m = menu.getClass().getDeclaredMethod(
                    "setOptionalIconsVisible", Boolean.TYPE);
                m.setAccessible(true);
                m.invoke(menu, true);
            }
            catch(NoSuchMethodException e){
                if(BuildConfig.DEBUG) if(VERBOSE) Log.e(TAG, "onCreateOptionsMenu", e);
            }
            catch(Exception e){
                throw new RuntimeException(e);
            }
        }
        return super.onCreateOptionsMenu(menu);
    }

    /**
     * Override Activity lifecycle method.
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) 
    {
        MenuItem sbs = menu.findItem(R.id.action_sbs);
        MenuItem tv = menu.findItem(R.id.action_3dtv);
        MenuItem ag = menu.findItem(R.id.action_anaglyph);
        MenuItem sw = menu.findItem(R.id.action_swap);
        MenuItem gs = menu.findItem(R.id.action_grayscale);

    	    switch(mDisplayMode)
    	    {
    	    case SbsFull:
   	    	    sbs.setChecked(true);
    	        sw.setEnabled(true);
    	        if(mDisplaySwap)
    	        {
    	            sbs.setIcon(R.drawable.ic_crossed_eye);
   	        	    sbs.setTitle(R.string.action_sbs_cross);
    	        }
    	        else
    	        {
    	            sbs.setIcon(R.drawable.ic_parallel_eye_white);
   	        	    sbs.setTitle(R.string.action_sbs_parallel);
    	        }
   	    	    break;
    	    case SbsHalf:
   	    	    tv.setChecked(true);
    	        sw.setEnabled(false);
   	    	    break;
    	    case Anaglyph:
   	    	    ag.setChecked(true);
    	        sw.setEnabled(true);
    	        if(mDisplaySwap)
    	        {
    	            ag.setIcon(R.drawable.ic_cyan_red);
   	        	    ag.setTitle(R.string.action_anaglyph_cyanred);
    	        }
    	        else
    	        {
    	            ag.setIcon(R.drawable.ic_red_cyan);
   	        	    ag.setTitle(R.string.action_anaglyph_redcyan);
    	        }
    	    	    break;
   	    	default:
   	    		break;
    	    }
    	    
    	    if(mDisplaySwap)
    	    {
   	    	    sw.setChecked(true);
    	    }
    	    else
    	    {
   	    	    sw.setChecked(false);
    	    }

    	    if(mDisplayColor)
    	    {
   	    	    gs.setChecked(false);
    	    }
    	    else
    	    {
   	    	    gs.setChecked(true);
    	    }
        return super.onPrepareOptionsMenu(menu);
    }

    /**
     * Override Activity lifecycle method.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) 
    {
        Intent intent;

        /* 
         * if user interacted with action bar, delay any scheduled hide()
         * operations to prevent the jarring behavior of controls going away
         * while interacting with the UI.
         */
        if (AUTO_HIDE) 
        {
            delayedHide(AUTO_HIDE_DELAY_SHORT_MILLIS);
        }

        switch (item.getItemId()) 
        {
        case android.R.id.home:
            intent = new Intent(GalleryActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(MainConsts.EXTRA_MSG, mIsMyMedia);
            startActivity(intent);
            break;
        case R.id.action_share:
       	    createDriveLink();
            break;
        case R.id.action_thumb_view:
       	    switchThumbView();
            break;
        case R.id.action_delete:
       	    deleteFile();
            break;
        case R.id.action_anaglyph:
            if(!item.isChecked())
            {
       	        mDisplayMode = DisplayMode.Anaglyph;
       	        if(item.getTitle().equals(getString(R.string.action_anaglyph_cyanred)))
       	        {
   	        	    mDisplaySwap = true;
       	        }
       	        else
       	        {
   	        	    mDisplaySwap = false;
       	        }

       	        item.setChecked(true);
       	        updateDisplayPrefs();
       	        invalidateOptionsMenu();
       	        switchDisplayMode();
            }
            break;
        case R.id.action_sbs:
            if(!item.isChecked())
            {
       	        mDisplayMode = DisplayMode.SbsFull;
       	        if(item.getTitle().equals(getString(R.string.action_sbs_cross)))
       	        {
   	        	    mDisplaySwap = true;
       	        }
       	        else
       	        {
   	        	    mDisplaySwap = false;
       	        }

       	        item.setChecked(true);
       	        updateDisplayPrefs();
       	        invalidateOptionsMenu();
       	        switchDisplayMode();
            }
            break;
        case R.id.action_3dtv:
            if(!item.isChecked())
            {
       	        mDisplayMode = DisplayMode.SbsHalf;
       	        item.setChecked(true);
       	        updateDisplayPrefs();
       	        invalidateOptionsMenu();
       	        switchDisplayMode();
            }
            break;
        case R.id.action_swap:
   	        if(item.isChecked())
   	        {
   	            mDisplaySwap = false;
   	            item.setChecked(false);
   	        }
   	        else
   	        {
   	            mDisplaySwap = true;
   	            item.setChecked(true);
   	        }
   	        updateDisplayPrefs();
   	        invalidateOptionsMenu();
            switchDisplayMode();
            break;
        case R.id.action_grayscale:
   	        if(item.isChecked())
   	        {
   	            mDisplayColor = true;
   	            item.setChecked(false);
   	        }
   	        else
   	        {
   	            mDisplayColor = false;
   	            item.setChecked(true);
   	        }
   	        updateDisplayPrefs();
   	        invalidateOptionsMenu();
            switchDisplayMode();
            break;
        default:
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    /**
     * Handles activity callbacks.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode,
            Intent data) 
    {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ActivityCode.SHARE_FILE.getValue())
        {
            if(resultCode == RESULT_OK) 
            {
       	        shareMedia(data);
            }
            else
            {
                 Toast.makeText(getApplication(), 
               		getString(R.string.error_network_error),
               		Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * start the ShareFileActivity to create an empty file in google drive,
     * and returned the link here.
     */
    private void createDriveLink() 
    {
        String shareType = null;

        if(!(new File(mMediaPath)).exists())
        {
       	    /*
       	     * something's wrong
       	     */
            if(BuildConfig.DEBUG) Log.e(TAG, "createCloudLink: " + mMediaPath + " not found");
       	    return;
        }

        Intent intent = new Intent(GalleryActivity.this, UploadFileActivity.class);
   	    intent.putExtra(MainConsts.EXTRA_PATH, mMediaPath);

   	    /*
         *  filename: SBS_<refCode>_<index>.jpg
         *            MPG_<refCode>_<index>.cvr
         */
        if(MediaScanner.isMovie(mMediaPath))
        {
            shareType = "movie";
            mPreviewPath = MediaScanner.getPreviewPathFromMediaPath(mMediaPath);
       	    mPreviewName = (new File(mPreviewPath)).getName();
            
            if(!(new File(mPreviewPath).exists()))
            {
           	    /*
           	     * sanity check
           	     */
                if(BuildConfig.DEBUG) if(VERBOSE) Log.e(TAG, "createDriveLink: preview frame not found " + mPreviewPath);
           	    mPreviewPath = null;
           	    mPreviewName = null;
            }
        }
        else
        {
            shareType = "photo";

       	    /*
       	     * non-chained upload
       	     */
       	    mPreviewPath = null;
       	    mPreviewName = null;
        }

        intent.putExtra(MainConsts.EXTRA_PATH_PREVIEW, mPreviewPath);
        startActivityForResult(intent, ActivityCode.SHARE_FILE.getValue());

        Bundle bundle = new Bundle();
        bundle.putString(MainConsts.FIREBASE_CUSTOM_PARAM_SHARE_TYPE, shareType);
        mFirebaseAnalytics.logEvent(MainConsts.FIREBASE_CUSTOM_EVENT_SHARE_START, bundle);
    }

    /**
     * share only to certain apps. code based on "http://stackoverflow.com/questions/
     * 9730243/how-to-filter-specific-apps-for-action-send-intent-and-set-a-different-
     * text-for/18980872#18980872"
     * 
     * "copy link" inspired by http://cketti.de/2016/06/15/share-url-to-clipboard/
     * 
     * in general, "deep linking" is supported by the apps below. Facebook, Wechat,
     * Telegram are exceptions. click on the link would bring users to the landing
     * page. 
     * 
     * Facebook doesn't take our EXTRA_TEXT so user will have to "copy link" first 
     * then paste the link
     */
    private void shareMedia(Intent data) 
    {
        /*
         * we log this as "share complete", but user can still cancel the share at this point,
         * and we wouldn't be able to know
         */
        mFirebaseAnalytics.logEvent(MainConsts.FIREBASE_CUSTOM_EVENT_SHARE_COMPLETE, null);

        Resources resources = getResources();

   	    /*
   	     * get the resource id for the shared file
   	     */
        String id = data.getStringExtra(MainConsts.EXTRA_ID_RESOURCE);

        /*
         * construct link
         */
        String link = "https://" + resources.getString(R.string.app_domain) + 
       		"/?id=" + id + "&name=" + ((mPreviewName != null)? mPreviewName: mMediaName);

        /*
         * message subject and text
         */
        String emailSubject, emailText, twitterText;

        if(MediaScanner.isPhoto(mMediaPath))
        {
       	    emailSubject = resources.getString(R.string.emailSubjectPhoto);
       	    emailText = resources.getString(R.string.emailBodyPhotoPrefix) + link;
       	    twitterText = resources.getString(R.string.emailBodyPhotoPrefix) + link + resources.getString(R.string.twitterHashtagPhoto) +
       	        resources.getString(R.string.app_hashtag);
        }
        else if(MediaScanner.is3dMovie(mMediaPath))
        {
       	    emailSubject = resources.getString(R.string.emailSubjectVideo);
       	    emailText = resources.getString(R.string.emailBodyVideoPrefix) + link;
       	    twitterText = resources.getString(R.string.emailBodyVideoPrefix) + link + resources.getString(R.string.twitterHashtagVideo) +
       	        resources.getString(R.string.app_hashtag);
        }
        else //if(MediaScanner.is2dMovie(mMediaPath))
        {
            emailSubject = resources.getString(R.string.emailSubjectVideo2d);
            emailText = resources.getString(R.string.emailBodyVideoPrefix2d) + link;
            twitterText = resources.getString(R.string.emailBodyVideoPrefix2d) + link + resources.getString(R.string.twitterHashtagVideo) +
                    resources.getString(R.string.app_hashtag);
        }

        Intent emailIntent = new Intent();
        emailIntent.setAction(Intent.ACTION_SEND);
        // Native email client doesn't currently support HTML, but it doesn't hurt to try in case they fix it
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, emailSubject);
        emailIntent.putExtra(Intent.EXTRA_TEXT, emailText);
        //emailIntent.putExtra(Intent.EXTRA_TEXT, Html.fromHtml(resources.getString(R.string.share_email_native)));
        emailIntent.setType("message/rfc822");

        PackageManager pm = getPackageManager();
        Intent sendIntent = new Intent(Intent.ACTION_SEND);     
        sendIntent.setType("text/plain");

        Intent openInChooser = Intent.createChooser(emailIntent, resources.getString(R.string.share_chooser_text));

        List<ResolveInfo> resInfo = pm.queryIntentActivities(sendIntent, 0);
        List<LabeledIntent> intentList = new ArrayList<LabeledIntent>();        
        for (int i = 0; i < resInfo.size(); i++) 
        {
            // Extract the label, append it, and repackage it in a LabeledIntent
            ResolveInfo ri = resInfo.get(i);
            String packageName = ri.activityInfo.packageName;
            if(packageName.contains("android.email")) 
            {
                emailIntent.setPackage(packageName);
            } 
            else if(packageName.contains("twitter") || 
            		    packageName.contains("facebook") || 
            		    packageName.contains("whatsapp") || 
            		    packageName.contains("tencent.mm") || //wechat
            		    packageName.contains("line") || 
            		    packageName.contains("skype") || 
            		    packageName.contains("viber") || 
            		    packageName.contains("kik") || 
            		    packageName.contains("sgiggle") || //tango
            		    packageName.contains("kakao") || 
            		    packageName.contains("telegram") || 
            		    packageName.contains("nimbuzz") || 
            		    packageName.contains("hike") || 
            		    packageName.contains("imoim") || 
            		    packageName.contains("bbm") || 
            		    packageName.contains("threema") || 
            		    packageName.contains("mms") || 
            		    packageName.contains("android.apps.messaging") || //google messenger
            		    packageName.contains("android.talk") || //google hangouts
            		    packageName.contains("android.gm")) 
            {
                Intent intent = new Intent();
                intent.setComponent(new ComponentName(packageName, ri.activityInfo.name));
                intent.setAction(Intent.ACTION_SEND);
                intent.setType("text/plain");
                if(packageName.contains("twitter")) 
                {
                    intent.putExtra(Intent.EXTRA_TEXT, twitterText);
                } 
                else if(packageName.contains("facebook")) 
                {
                    /*
                     * the warning below is wrong! at least on GS5, Facebook client does take
                     * our text, however it seems it takes only the first hyperlink in the
                     * text.
                     * 
                     * Warning: Facebook IGNORES our text. They say "These fields are intended 
                     * for users to express themselves. Pre-filling these fields erodes the 
                     * authenticity of the user voice."
                     * One workaround is to use the Facebook SDK to post, but that doesn't 
                     * allow the user to choose how they want to share. We can also make a 
                     * custom landing page, and the link will show the <meta content ="..."> 
                     * text from that page with our link in Facebook.
                     */
                    intent.putExtra(Intent.EXTRA_TEXT, link);
                } 
                else if(packageName.contains("tencent.mm")) //wechat
                {
               	    /*
               	     * wechat appears to do this similar to Facebook
               	     */
                    intent.putExtra(Intent.EXTRA_TEXT, link);
                }
                else if(packageName.contains("android.gm")) 
                { 
               	    // If Gmail shows up twice, try removing this else-if clause and the reference to "android.gm" above
                    intent.putExtra(Intent.EXTRA_SUBJECT, emailSubject);               
                    intent.putExtra(Intent.EXTRA_TEXT, emailText);
                    //intent.putExtra(Intent.EXTRA_TEXT, Html.fromHtml(resources.getString(R.string.share_email_gmail)));
                    intent.setType("message/rfc822");
                }
                else if(packageName.contains("android.apps.docs")) 
                { 
               	    /*
               	     * google drive - no reason to send link to it
               	     */
               	    continue;
                }
                else 
                {
                    intent.putExtra(Intent.EXTRA_TEXT, emailText);
                } 

                intentList.add(new LabeledIntent(intent, packageName, ri.loadLabel(pm), ri.icon));
            }
        }

        /*
         *  create "Copy Link To Clipboard" Intent
         */
        Intent clipboardIntent = new Intent(this, CopyToClipboardActivity.class);
        clipboardIntent.setData(Uri.parse(link));
        intentList.add(new LabeledIntent(clipboardIntent, getPackageName(),
       		getResources().getString(R.string.clipboard_activity_name), R.drawable.ic_copy_link));

        // convert intentList to array
        LabeledIntent[] extraIntents = intentList.toArray( new LabeledIntent[ intentList.size() ]);

        openInChooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, extraIntents);
        startActivity(openInChooser);       
    }

	private void shareUpdate(int position)
    {
        if((mMediaList != null) && 
        		(position >= 0) && (position <= mMediaList.size()))
   		{
		    mMediaPath = mMediaList.get(position);
		    mMediaName = (new File(mMediaPath)).getName();
   		}
        else
   	    {
    	    mMediaPath = null;
		    mMediaName = null;
   	    }
    }

    @Override
    public boolean onMenuOpened(int featureId, Menu menu) 
    {
   	    /*
   	     * if the overflow menu was opened, we will hide action bar
   	     * after a longer delay, to allow user enough time to see
   	     * what's in the overflow menu
   	     */
        if(featureId == Window.FEATURE_ACTION_BAR)
        {
            if (AUTO_HIDE) 
            {
                delayedHide(AUTO_HIDE_DELAY_LONG_MILLIS);
            }
        }

        /*
         * opening menu apparently reset the full screen flags. so 
         * redo it here.
         */
        //forceFullScreen();

        return super.onMenuOpened(featureId, menu);
    }

    @SuppressWarnings("deprecation")
	private void initViewPager()
    {
        if(BuildConfig.DEBUG) if(VERBOSE) Log.d(TAG, "initViewPager");

        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        
        mViewPager = (ViewPager) findViewById(R.id.view_pager);
        mViewPager.setAdapter(new MediaPagerAdapter(this, size.x, size.y));
        mViewPager.setOnTouchListener(otl);
        mViewPager.setOnPageChangeListener(opcl);
        mViewPager.setOffscreenPageLimit(MAX_PAGE);  
    }

    /**
     * delete current file
     */
    private void deleteFile()
    {
        if(mViewPager.getAdapter().getCount() == 0)
        {
       	    if(BuildConfig.DEBUG) if(VERBOSE) Log.d(TAG, "deleteFile: nothing to delete");
       	    return;
        }
		        
        final int index = mViewPager.getCurrentItem();
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this, R.style.AppCompatAlertDialogStyle);
        alertDialogBuilder.setTitle(R.string.delete);
        alertDialogBuilder.setMessage(R.string.warning_item_delete);

        alertDialogBuilder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface arg0, int arg1) 
            {
		        /*
		         * delete sbs/cvr, thumbnail, preview and exported files
		         */
				String mediaFilePath = mMediaList.get(index);
				MediaScanner.deleteFile(mediaFilePath);

                updateViewPager(null, index, -1);
            }
        });
        
        alertDialogBuilder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
           @Override
           public void onClick(DialogInterface dialog, int which) 
           {
   	           //do nothing
           }
        });
        
        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }

    public void switchDisplayMode()
    {
   	    if((mMediaList == null) || (mMediaList.size() == 0))
   	    {
    	    finish();
    	    return;
  	    }

        final int index = mViewPager.getCurrentItem();

        if(index == -1)
        {
       	    return;
        }

        /*
         * if current image is deleted in ThumbView, then index 
         * may go out of bound
         */
        final int i = Math.min(index, mMediaList.size()-1);

        if(mDisplayMode == DisplayMode.Cardboard)
        {
   	        Intent intent;
   	        String filePath = mMediaList.get(i);
       	    if(MediaScanner.isMovie(filePath))
       	    {
                mFirebaseAnalytics.logEvent(MainConsts.FIREBASE_CUSTOM_EVENT_VIEW_MOVIE, null);

                if(!MediaScanner.isEmpty(filePath))
                {
   	                intent = new Intent(this, MovieActivity.class);
                }
                else
                {
                    AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this, R.style.AppCompatAlertDialogStyle);
                    alertDialogBuilder.setTitle(R.string.information);
                    alertDialogBuilder.setMessage(R.string.info_download_first);

                    alertDialogBuilder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface arg0, int arg1)
                        {
                        }
                    });

                    AlertDialog alertDialog = alertDialogBuilder.create();
                    alertDialog.show();
                    return;
                }
       	    }
   	        else
   	        {
                mFirebaseAnalytics.logEvent(MainConsts.FIREBASE_CUSTOM_EVENT_VIEW_PHOTO, null);

   	            intent = new Intent(this, PhotoActivity.class);
   	            intent.putExtra(MainConsts.EXTRA_COLOR, mDisplayColor);
   	        }
            intent.setData(Uri.fromFile(new File(filePath)));
            intent.putExtra(MainConsts.EXTRA_MSG, mIsMyMedia);
   	        startActivity(intent);
   	        return;
        }

        MediaPagerAdapter mpa = (MediaPagerAdapter) mViewPager.getAdapter();
        /*
         * calling below, plus the fact we have getItemPosition returning POSITION_NONE 
         * will cause all pages to be redrawn. c.f. http://stackoverflow.com/questions/
         * 7263291/viewpager-pageradapter-not-updating-the-view/8024557#8024557
         */
        mpa.notifyDataSetChanged();

        new Handler().post(new Runnable() {
            @Override
            public void run() 
            {
		        mViewPager.setCurrentItem(i, true);
            	    shareUpdate(i);
            }
        });
    }

    /**
     * updates the view pager by either path name OR index. either path should be
     * non-null, or index should be other than -1. if this is a movie download 
     * progress update, percentage will be different from -1.
     */
    private void updateViewPager(String path, int index, int percentage)
    {
   	    ArrayList<String> newMediaList = MediaScanner.getMediaList(mIsMyMedia);
        if(BuildConfig.DEBUG) Log.d(TAG, "updateViewPager: newMediaList size=" + newMediaList.size()); 

        if(mMediaList != null)
        {
            if(BuildConfig.DEBUG) Log.d(TAG, "updateViewPager: current mediaList size=" + mMediaList.size());
        }

   	    if(newMediaList.size() == 0)
   	    {
    	    /*
    	     * sanity check
    	     */
    	    finish();
    	    return;
   	    }

   	    if(!MediaScanner.isContentSame(newMediaList, mMediaList))
   	    {
    	    /*
    	     * we have additions or subtractions in our list. in this case, we
    	     * will update the entire view pager.
    	     */
    	    mMediaList = newMediaList;
    	    	    
    	    if(path != null)
    	    {
                index = mMediaList.indexOf(path);
    	    }
    	    else
    	    {
                /*
                 * if current image is deleted in ThumbView, then index 
                 * may go out of bound
                 */
   	    	    index = Math.min(index, mMediaList.size()-1);
    	    }

            MediaPagerAdapter mpa = (MediaPagerAdapter) mViewPager.getAdapter();
            mpa.setMedia(mMediaList);
            mpa.setIsMyMedia(mIsMyMedia);
            mpa.notifyDataSetChanged();

            /*
             * if we want to change the data and then go to a specific position,
             * we'd have to do this or we will have a very buggy behavior, c.f.
             * "http://stackoverflow.com/questions/12008716/setcurrentitem-in-
             * viewpager-not-scroll-immediately-in-the-correct-position"
             */
            final int i = index;
            if(i != -1)
            {
                new Handler().post(new Runnable() {
                    @Override
                    public void run() 
                    {
    		                mViewPager.setCurrentItem(i, true);
                	        shareUpdate(i);
                    }
                });
            }
            else
            {
                if(BuildConfig.DEBUG) Log.e(TAG, "updateViewPager: error finding path=" + path);
            }
   	    }
   	    else
   	    {
   	        /*
   	         * we have status update to a member of our current list, identified by
   	         * its path. in this case, we find the relevant view in the view pager
   	         * and update it.
   	         */
            FrameLayout fl = null;

  	        if(path != null)
   	        {
  	            fl = (FrameLayout) (mViewPager.findViewWithTag(path));
   	        }
   	        else
   	        {
                if(BuildConfig.DEBUG) Log.e(TAG, "updateViewPager: trying to update an " +
               		"existing view but path unspecified.");

                /*
                 * we cannot update a view identified by its index in the view pager. code
                 * below doesn't work. "c.f. http://stackoverflow.com/questions/12854783/
                 * android-viewpager-get-the-current-view"
                 */
  	            //fl = (FrameLayout) (mViewPager.getChildAt(index));
   	            //path = (String) fl.getTag();
    	    }
    	            
            if((fl != null) && (path != null))
            {
                if(BuildConfig.DEBUG) Log.d(TAG, "updateViewPager: invalidate view");
                ((MediaPagerAdapter) mViewPager.getAdapter()).refreshItem(fl, path, percentage);
            }
   	    }
		
		showHideView();
    }

    private void switchThumbView()
    {
        /*
         * note we use FLAG_ACTIVITY_CLEAR_TOP below which means if we came
         * here from Camera/CamcorderActivity, then they will be destroyed
         * once we return to main. this currently won't happen as we always 
         * go from Camera/Camcorder to Photo/MovieActivity directly, without
         * going through gallery, but in the future if we do that, we will 
         * need to revisit below
         */
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(MainConsts.EXTRA_MSG, mIsMyMedia);
        startActivity(intent);
    }

    private void showHideView()
    {
   	    if((mMediaList == null) || (mMediaList.size() == 0))
   	    {
    	    finish();
    	    return;
  	    }

        mViewPager.setVisibility(View.VISIBLE);
        mNoMedia.setVisibility(View.GONE);
    }

    public DisplayMode getDisplayMode()
    {
    	    return mDisplayMode;
    }

    public boolean getDisplayColor()
    {
    	    return mDisplayColor;
    }

    public boolean getDisplaySwap()
    {
    	    return mDisplaySwap;
    }

    public void onDownloadFileStarted(String name)
    {
        if(BuildConfig.DEBUG) Log.d(TAG, "onDownloadFileStarted");
        mFirebaseAnalytics.logEvent(MainConsts.FIREBASE_CUSTOM_EVENT_DOWNLOAD_FILE_STARTED, null);
    }

    public void onDownloadFileComplete(String name)
    {
        if(BuildConfig.DEBUG) Log.d(TAG, "onDownloadFileComplete");
        mFirebaseAnalytics.logEvent(MainConsts.FIREBASE_CUSTOM_EVENT_DOWNLOAD_FILE_COMPLETE, null);

        removeDownloadFileFragment(name);
    }

    public void onDownloadFileFailure(String name)
    {
        if(BuildConfig.DEBUG) Log.d(TAG, "onDownloadFileFailure");
        mFirebaseAnalytics.logEvent(MainConsts.FIREBASE_CUSTOM_EVENT_DOWNLOAD_FILE_FAILURE, null);

        if(!MediaScanner.isMovie(name))
        {
            /*
             * if this is a preview frame or photo, we need to delete the placeholder 
             * file, because we would have nothing to show to the user
             * 
             * if we failed on cvr download, leave the placeholder file alone. 
             */
   	        String sharePath = MediaScanner.getSharedMediaPathFromOrigName(name);
   	        MediaScanner.removeItemMediaList(sharePath);
   	        (new File(sharePath)).delete();
        }

        updateViewPager(null, mViewPager.getCurrentItem(), -1);

        removeDownloadFileFragment(name);
    }

    public void onDownloadFileProgress(String name, final int downloadBytes, final int totalBytes)
    {
   	    if(MediaScanner.isMovie(name))
   	    {
   	        if(totalBytes != -1)
   	        {
                int percentage = (int)((float)downloadBytes*100/(float)totalBytes);

                if(BuildConfig.DEBUG) Log.d(TAG, "onDownloadFileProgress: " + 
   	                percentage + "%");
    	    
                String path = MediaScanner.getSharedMediaPathFromOrigName(name);
                
                Intent messageIntent = new Intent(MainConsts.FILE_DOWNLOADER_MESSAGE);
                messageIntent.putExtra(MainConsts.EXTRA_WHAT, MainConsts.MSG_FILE_DOWNLOADER_PROGRESS);
                messageIntent.putExtra(MainConsts.EXTRA_PATH, path);
                messageIntent.putExtra(MainConsts.EXTRA_MSG, percentage);
                LocalBroadcastManager.getInstance(this).sendBroadcast(messageIntent);
   	        }
   	    }
    }

    private void addDownloadFileFragment(String resId, String name, int size)
    {
   	    try {
            FragmentManager fragmentManager = getSupportFragmentManager();
            FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

            Bundle bundle = new Bundle();
            bundle.putString(MainConsts.EXTRA_ID_RESOURCE, resId);
            bundle.putString(MainConsts.EXTRA_NAME, name);
            bundle.putInt(MainConsts.EXTRA_SIZE, size);
            DownloadFileFragment ddf = new DownloadFileFragment();
            ddf.setArguments(bundle);
            ddf.setRetainInstance(true);
    
            fragmentTransaction.add(R.id.display_panel, ddf, name);
            fragmentTransaction.commitAllowingStateLoss();
	    } catch (Exception e) {
    	    /*
    	     * we could potentially have the same kind of exception we saw
    	     * when we remove fragment in the midst of screen rotation. it
    	     * is not clear if/when this happens, if the fragment will be
    	     * created/added.
    	     */
            if(BuildConfig.DEBUG) Log.e(TAG, "addDownloadFileFragment: commit failed " + 
                e.getMessage());
        }
    }

    private void removeDownloadFileFragment(String name)
    {
        /*
         * we do not need the fragment anymore. remove it.
         */
   	    try {
            FragmentManager fragmentManager = getSupportFragmentManager();
            FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

            Fragment fragment = fragmentManager.findFragmentByTag(name);
    
            fragmentTransaction.remove(fragment);
            fragmentTransaction.commitAllowingStateLoss();
	    } catch (Exception e) {
    	    /*
    	     * observed exception when we remove while at the same time doing a screen
    	     * rotation. this happens despite we call commitAllowingStateLoss instead
    	     * of commit. if we encounter this occasionally, it's not a big deal - the
    	     * fragment will eventually be destroyed when we exit the activity.
    	     *
    	     * it is not clear if the remove is successful (thus memory be freed) if
    	     * commit fails
    	     */
            if(BuildConfig.DEBUG) Log.e(TAG, "removeDownloadFileFragment: commit failed " +
                e.getMessage());
        }
    }
    
    /**
     * Checks whether the device currently has a network connection.
     * @return true if the device has a network connection, false otherwise.
     */
    private boolean isDeviceOnline() 
    {
        ConnectivityManager connMgr =
            (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
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

    public static String fixedLengthHumanReadableByteCount(long bytes, boolean si, int length) 
    {
   	    String hr = humanReadableByteCount(bytes, si);

        String spaces = "";
        for(int i=0; i<(length-hr.length()); i++)
        {
       	    spaces += ".";
        }
        return spaces+hr;
    }

    public static String humanReadableByteCount(long bytes, boolean si) 
    {
        int unit = si ? 1000 : 1024;
        if (bytes < unit) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp-1) + (si ? "" : "i");
        return String.format("%3.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

    private class MediaPagerAdapter extends PagerAdapter 
    {
	    private final WeakReference<GalleryActivity> mActivityRef;

        private ArrayList<String>mMediaList = new ArrayList<String>();

        private boolean mIsMyMedia;
        private int mScreenWidth, mScreenHeight;
        private DisplayMetrics mDisplayMetrics;
        private Drawable mPlayButtonDrawable;
        private Drawable mDownloadButtonDrawable;
        private Drawable m3dIconDrawable;

        public MediaPagerAdapter(GalleryActivity activity, int width, int height)
        {

       	    mScreenWidth = width;
       	    mScreenHeight = height;

            mDisplayMetrics = new DisplayMetrics();
            WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            windowManager.getDefaultDisplay().getMetrics(mDisplayMetrics);

            mActivityRef = new WeakReference<GalleryActivity>(activity);

            mPlayButtonDrawable = mActivityRef.get().getResources().
        		getDrawable(R.drawable.ic_play_circle_outline_white_24dp);

            mDownloadButtonDrawable = mActivityRef.get().getResources().
           		getDrawable(R.drawable.ic_cloud_download_white_24dp);

            m3dIconDrawable = mActivityRef.get().getResources().
                    getDrawable(R.drawable.ic_3d_icon);
        }

        public void setMedia(ArrayList<String> list)
        {
        	    mMediaList = list;
        }

        public void setIsMyMedia(boolean isMyMedia)
        {
        	    mIsMyMedia = isMyMedia;
        }

        @Override
        public int getCount() 
        {
            return mMediaList.size();
        }

        @Override
        public boolean isViewFromObject(View view, Object object) 
        {
            return view == ((FrameLayout) object);
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) 
        {
       	    GalleryActivity activity;

       	    String path = mMediaList.get(position);

       	    if(mActivityRef != null)
       	    {
   	    	    activity = mActivityRef.get();
       	    }
       	    else
       	    {
                if(BuildConfig.DEBUG) Log.e(TAG, "instantiateItem: activity ref null");
   	    	    return null;
       	    }

            FrameLayout fl = new FrameLayout(activity);
            fl.setLayoutParams(new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            fl.setTag(path);

            /*
             * image view for holding the photo or video preview frame
             */
            ImageView imageView = new ImageView(activity);
            imageView.setScaleType(ImageView.ScaleType.CENTER);
       	    imageView.setImageDrawable(null);
            imageView.setVisibility(View.VISIBLE);

   		    FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            layoutParams.gravity = Gravity.CENTER;

            /*
             * progress bar while downloading/decoding/processing
             */
            ProgressBar progBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleLarge);
            progBar.setLayoutParams(layoutParams);
            progBar.setVisibility(View.VISIBLE);

            TextView progText = new TextView(activity);
            progText.setLayoutParams(layoutParams);
            progText.setTextAppearance(activity, R.style.ProgText);
            progText.setVisibility(View.VISIBLE);

            /*
             * add imageView and progress bar
             */
            fl.addView(imageView);
            fl.addView(progBar);
            fl.addView(progText);

            if(MediaScanner.isMovie(path))
            {
                /*
                 * download/play button - only visible for video
                 */
                ImageButton downloadPlayButton = new ImageButton(activity);
                downloadPlayButton.setLayoutParams(layoutParams);
    
                downloadPlayButton.setScaleType(ImageButton.ScaleType.FIT_CENTER);
                downloadPlayButton.setOnClickListener(new MovieOnClickListener(path, progBar, progText));
                downloadPlayButton.setBackground(null);
    
                fl.addView(downloadPlayButton);
            }

            if(!MediaScanner.is2d(path))
            {
                int iconDim = Math.min(mScreenHeight, mScreenWidth)/8;
                layoutParams = new FrameLayout.LayoutParams(iconDim, iconDim);
                layoutParams.gravity = Gravity.TOP | Gravity.START;

                /*
                 * 3d icon
                 */
                layoutParams.setMargins(0, iconDim, 0, 0); //just below actionbar
                ImageView modeIconView = new ImageView(activity);
                modeIconView.setLayoutParams(layoutParams);
                modeIconView.setImageDrawable(m3dIconDrawable);

                fl.addView(modeIconView);
            }

            refreshItem(fl, path, -1);

            ((ViewPager) container).addView(fl, 0);

            return fl;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) 
        {
       	    /*
       	     * recycle bitmap
       	     */
            ImageView imageView = (ImageView) ((FrameLayout)object).getChildAt(0);

            BitmapDrawable bd = (BitmapDrawable)imageView.getDrawable();
            if(bd != null)
            {
                Bitmap bitmap = bd.getBitmap();
                if(bitmap != null)
                {
                    if(BuildConfig.DEBUG) if(VERBOSE) Log.d(TAG, "destroying bitmap at position " + position);
           	        bitmap.recycle();
           	        imageView.setImageBitmap(null);
                }
                bd.setCallback(null);
            }

            ((ViewPager) container).removeView((FrameLayout) object);
        }
        
        /**
         * returning POSITION_NONE - when you call notifyDataSetChanged(), the view pager 
         * will remove all views and reload them all. So the reload effect is obtained.
         * c.f. "http://stackoverflow.com/questions/7263291/viewpager-pageradapter-not-
         * updating-the-view"
         * 
         * we may need to use the more efficient updating method as suggested by Alvaro 
         * Luis Bustamante in that same post
         */
        @Override
        public int getItemPosition(Object object) 
        {
            return POSITION_NONE;
        }
        
        public void refreshItem(FrameLayout fl, String path, int percentage) 
        {
       	    GalleryActivity activity;
       	    DisplayMode displayMode;
       	    boolean displayColor, displaySwap;

    	    activity = mActivityRef.get();
    	    displayMode = activity.getDisplayMode();
    	    displayColor = activity.getDisplayColor();
    	    displaySwap = activity.getDisplaySwap();

            ProgressBar progBar = (ProgressBar)fl.getChildAt(1);
            TextView progText = (TextView)fl.getChildAt(2);

            if(MediaScanner.isMovie(path))
            {
                ImageButton downloadPlayButton = (ImageButton)fl.getChildAt(3);
                if(percentage != -1)
                {
               	    /*
               	     * this is a progress update refresh. just set the percentage
               	     * and return. if we go through the rest of the logic, the
               	     * progress bar would be stopped then restarted causing some
               	     * visual problems
               	     */
                    progText.setText(percentage + "%");
                    return;
                }

           	    String previewPath = MediaScanner.getPreviewPathFromMediaPath(path);

           	    if(!MediaScanner.isEmpty(previewPath))
           	    {
                    downloadPlayButton.setVisibility(View.VISIBLE);
                    progBar.setVisibility(View.GONE);
                    progText.setVisibility(View.GONE);

                    if(BuildConfig.DEBUG) Log.d(TAG, "refreshItem: decode sbs path=" + previewPath);
                    new DecodePreviewTask(fl, previewPath,
	                    displayMode, displayColor, displaySwap, 
	                    mScreenWidth, mScreenHeight).execute();
           	    }
           	    else
           	    {
                    downloadPlayButton.setVisibility(View.GONE);
                    progBar.setVisibility(View.VISIBLE);
                    progText.setVisibility(View.VISIBLE);

                    if(!mIsMyMedia)
                    {
                        Intent serviceIntent = new Intent(activity, FileDownloaderService.class);
                        serviceIntent.putExtra(MainConsts.EXTRA_PATH, previewPath);
                        startService(serviceIntent);
                    }
           	    }

                if(mIsMyMedia)
                {
                    downloadPlayButton.setImageDrawable(mPlayButtonDrawable);
                }
                else
                {
           	        if(!MediaScanner.isEmpty(path))
           	        {
                        downloadPlayButton.setImageDrawable(mPlayButtonDrawable);
           	        }
           	        else if(!isDownloading(path))
           	        {
                        downloadPlayButton.setImageDrawable(mDownloadButtonDrawable);
           	        }
           	        else
           	        {
                        downloadPlayButton.setVisibility(View.GONE);
                        progBar.setVisibility(View.VISIBLE);
                        progText.setVisibility(View.VISIBLE);
           	        }
                }
            }
            else
            {
           	    if(!MediaScanner.isEmpty(path))
           	    {
                    progBar.setVisibility(View.GONE);
                    progText.setVisibility(View.GONE);

                    if(BuildConfig.DEBUG) Log.d(TAG, "refreshItem: decode sbs path=" + path);
                    new DecodePreviewTask(fl, path,
   		                displayMode, displayColor, displaySwap,
   		                mScreenWidth, mScreenHeight).execute();
           	    }
           	    else
           	    {
                    progBar.setVisibility(View.VISIBLE);
                    progText.setVisibility(View.GONE);

                    if(!mIsMyMedia)
                    {
                        Intent serviceIntent = new Intent(activity, FileDownloaderService.class);
                        serviceIntent.putExtra(MainConsts.EXTRA_PATH, path);
                        startService(serviceIntent);
                    }
           	    }
            }
            fl.invalidate();
        }

        private boolean isDownloading(String filePath)
        {
       	    return (new File(filePath + ".tmp")).exists();
        	    
       	    /*
       	     * originally we pass a downloading list when updating the view pager,
       	     * and we rely on that list to determine if a file is being downloaded.
       	     * but that don't work as updateViewPager may not be called
       	     */
        }

        /*
        private void getCreatorInfo(String path)
        {
       	    if(MediaScanner.isMovie(path))
       	    {
                path = getThumbPath(path); 
       	    }

       	    String info = MediaScanner.extractExifInfo(path);
   		    String creatorName = null;
   		    String creatorPhotoUrl = null;
            if(info != null)
            {
   	    	    int offset1 = info.indexOf("name=");
   	    	    int offset2 = info.indexOf("photourl=");
     	        if((offset1 != -1) && (offset2 != -1))
       	        {
       		        creatorName = info.substring(offset1+5, offset2);
       		        creatorPhotoUrl = info.substring(offset2);
       	        }
            }
            if(BuildConfig.DEBUG) Log.e(TAG, "getCreatorInfo: " + 
                "creatorName=" + creatorName + ", creatorPhotoUrl=" + creatorPhotoUrl);
        }
        */

        private class MovieOnClickListener implements OnClickListener
        {
            String path;
            ProgressBar progBar; 
            TextView progText; 

            public MovieOnClickListener(String path, ProgressBar progBar, TextView progText) 
            {
                 this.path = path;
                 this.progBar = progBar;
                 this.progText = progText;
            }

            @Override
            public void onClick(View v)
            {
	            GalleryActivity activity = mActivityRef.get();
       	    	    boolean displayColor = activity.getDisplayColor();

           	    if(!isDownloading(path) && !MediaScanner.isEmpty(path))
           	    {
                    Intent intent;
	                if(MediaScanner.isMovie(path))
	                {
                        intent = new Intent(activity, MovieActivity.class);
	                }
	                else
	                {
                        intent = new Intent(activity, PhotoActivity.class);
                        intent.putExtra(MainConsts.EXTRA_COLOR, displayColor);
	                }
                    intent.setData(Uri.fromFile(new File(path)));
                    intent.putExtra(MainConsts.EXTRA_MSG, mIsMyMedia);
       	            activity.startActivity(intent);
           	    }
           	    else if(!mIsMyMedia && !isDownloading(path))
           	    {
       	    	    if(!startChainedDownload())
       	    	    {
                        Toast.makeText(activity, R.string.error_incorrect_link, Toast.LENGTH_LONG).show();
       	    	    }
       	    	    else
       	    	    {
   	    	    	    //trigger progress bar
   	    	    	    v.setVisibility(View.GONE);
   	    	    	    progBar.setVisibility(View.VISIBLE);
   	    	    	    progText.setVisibility(View.VISIBLE);
      	    	    }
           	    }
            }
            
            /**
             * start chained download based on resId found in the preview frame
             * exif header. format of chain info: "resId=...size=..."
             */
            private boolean startChainedDownload()
            {
       			String info = MediaScanner.extractExifInfo(MediaScanner.getPreviewPathFromMediaPath(path));
       	        if(BuildConfig.DEBUG) Log.d(TAG, "chain info=" + info);

       	        if(info == null)
       	        {
                    if(BuildConfig.DEBUG) Log.e(TAG, "startChainedDownload: chain info not available");
       	        	    return false;
       	        }

           	    String resId = null;
           	    int size = -1;

       	        int offset1 = -1;
                int offset2 = -1;

       	        offset1 = info.indexOf("resId=");
       	        offset2 = info.indexOf("size=");

       	        if((offset1 != -1) && (offset2 != -1))
       	        {
      		        resId = info.substring(offset1+6, offset2);
       		        try {
        		            size = Integer.parseInt(info.substring(offset2+5));
       			    } catch (Exception e) {
                        if(BuildConfig.DEBUG) Log.e(TAG, "startChainedDownload: chain info wrong, " + 
                            e.getMessage());
                        return false;
       			    }
       	        }
       	        else
       	        {
                    if(BuildConfig.DEBUG) Log.e(TAG, "startChainedDownload: chain info wrong");
                    return false;
       	        }
        	    
                addDownloadFileFragment(resId, (new File(path)).getName(), size);
                return true;
            }
       };
    }
}