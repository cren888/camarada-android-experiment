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
import com.aimfire.camarada.R;
import com.aimfire.camarada.BuildConfig;
import com.aimfire.main.MainConsts;
import com.aimfire.drive.service.FileDownloaderService;
import com.aimfire.main.MainActivity;

//import android.app.AlertDialog;
import android.support.v7.app.AlertDialog;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Checkable;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ProgressBar;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * A fragment for displaying thumbnails (for my own or shared with me)
 */
public class ThumbsFragment extends Fragment 
{
    private static final String TAG = "ThumbsFragment";
    private static final boolean VERBOSE = true;

    private static final String KEY_THUMB_POSITION = "KTP";

    /*
     * identify this tab to be "My Media" or "Shared With Me"
     */
	private boolean mIsMyMedia;

    /*
     * UI components for thumbnail view
     */
    private GridView mGridView = null;
    private LinearLayout mNoMedia = null;

    /*
     * keeping track of the thumbs
     */
    private ArrayList<String> mMediaList; //sbs images and cvr movies
    private int mThumbPosition = 0;

    /*
     * share action provider
     */
    //private ShareActionProvider mShareActionProvider;

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
                        updateGridView(null, mGridView.getFirstVisiblePosition());
                    }
                    else
                    {
   	                    /*
   	                     * we come here if we successfully downloaded a file
   	                     */
           	            if(MediaScanner.isPreview(path))
           	            {
                            path = MediaScanner.getSharedMediaPathFromPreviewPath(path);
           	            }
                        updateGridView(path, -1);
                    }
                }
                break;
		    case MainConsts.MSG_FILE_DOWNLOADER_SAMPLES_START:
                if(!mIsMyMedia)
                {
                    updateGridView(null, 0);
                }
                break;
		    case MainConsts.MSG_FILE_DOWNLOADER_PROGRESS:
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
            String path = intent.getStringExtra(MainConsts.EXTRA_PATH);

            switch(messageCode)
            {
            case MainConsts.MSG_PHOTO_PROCESSOR_RESULT:
   	            /*
   	             * ignore if we are showing shared media files
   	             */
   	            if(mIsMyMedia)
   	            {
                    updateGridView(path, -1);
   	            }
           	    break;
            case MainConsts.MSG_PHOTO_PROCESSOR_ERROR:
           		if(BuildConfig.DEBUG) if(VERBOSE) Log.d(TAG, "onReceive: " + path +
           				" auto alignment error");

   	            if(mIsMyMedia)
   	            {
                    updateGridView(null, mGridView.getFirstVisiblePosition());
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
            String path = intent.getStringExtra(MainConsts.EXTRA_PATH);

            switch(messageCode)
            {
            case MainConsts.MSG_MOVIE_PROCESSOR_RESULT:
   	            /*
   	             * ignore if we are showing shared media files
   	             */
   	            if(mIsMyMedia)
   	            {
                    updateGridView(path, -1);
   	            }
           	    break;
            case MainConsts.MSG_MOVIE_PROCESSOR_ERROR:

           		if(BuildConfig.DEBUG) if(VERBOSE) Log.d(TAG, "onReceive: " + path +
           				" auto alignment error");

   	            if(mIsMyMedia)
   	            {
                    updateGridView(null, mGridView.getFirstVisiblePosition());
   	            }
                break;
            default:
           	    break;
            }
        }
    };

    private OnItemClickListener oicl = new OnItemClickListener() {
        public void onItemClick(AdapterView<?> parent, View v, int position, long id) 
        {
            String path = mMediaList.get(position);

            Intent intent = new Intent(getActivity(), GalleryActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(MainConsts.EXTRA_PATH, path);
            intent.putExtra(MainConsts.EXTRA_MSG, mIsMyMedia);

   	        startActivity(intent);
        }
    };


	@Override
	public void onCreate(Bundle savedInstanceState) 
	{		
		super.onCreate(savedInstanceState);
		Bundle data = getArguments();
		int index = data.getInt(MainConsts.EXTRA_INDEX);
        mIsMyMedia = (index == MainActivity.TAB_INDEX_MY_MEDIA);

        if(BuildConfig.DEBUG) if(VERBOSE) Log.d(TAG, "onCreate: mIsMyMedia =" + mIsMyMedia);
	}

    /**
     * Override Fragment lifecycle method.
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) 
    {
        if(BuildConfig.DEBUG) if(VERBOSE) Log.d(TAG, "onCreateView: mIsMyMedia =" + mIsMyMedia);

        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) 
        {
            mThumbPosition = savedInstanceState.getInt(KEY_THUMB_POSITION);
            if(BuildConfig.DEBUG) if(VERBOSE) Log.d(TAG, "onCreateView: restored thumb position=" + mThumbPosition);
        }

        /*
         * set up UI
         */
		//View v = inflater.inflate(R.layout.fragment_thumbs, null);
        View v = inflater.inflate(R.layout.fragment_thumbs, container, false);
        mNoMedia = (LinearLayout) v.findViewById(R.id.noMediaLayout);
        mGridView = (GridView) v.findViewById(R.id.grid_view);
        mGridView.setChoiceMode(GridView.CHOICE_MODE_MULTIPLE_MODAL);
        mGridView.setMultiChoiceModeListener(new MultiChoiceModeListener());
        mGridView.setOnItemClickListener(oicl);
        mGridView.setAdapter(new MediaAdapter(this, mGridView, mIsMyMedia));

        return v;
    }

    /**
     * Override fragment lifecycle method.
     */
    @Override
	public void onResume() 
    {
        if(BuildConfig.DEBUG) if(VERBOSE) Log.d(TAG, "onResume: mIsMyMedia =" + mIsMyMedia);

   	    /*
   	     * reload image list, in case images were added/deleted during
   	     * the time we were paused
   	     */
        updateGridView(null, mThumbPosition);

   	    /*
   	     * register for intents sent by the media processor service
   	     */
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mPhotoProcessorMsgReceiver,
                new IntentFilter(MainConsts.PHOTO_PROCESSOR_MESSAGE));

        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mMovieProcessorMsgReceiver,
                new IntentFilter(MainConsts.MOVIE_PROCESSOR_MESSAGE));

   	    /*
   	     * register for intents sent by the download completion service
   	     */
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mDownloadMsgReceiver,
            new IntentFilter(MainConsts.FILE_DOWNLOADER_MESSAGE));

   	    super.onResume();
    }

    /**
     * Override fragment lifecycle method.
     */
    @Override
    public void onPause() 
    {
        if(BuildConfig.DEBUG) if(VERBOSE) Log.d(TAG, "onPause: mIsMyMedia =" + mIsMyMedia);

	    /*
	     * de-register for intents sent by the download completion service
	     */
        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(mDownloadMsgReceiver);

	    /*
	     * de-register for intents sent by the media processor service
	     */
        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(mPhotoProcessorMsgReceiver);
        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(mMovieProcessorMsgReceiver);

   	    super.onPause();
    }

    /**
     * Override fragment lifecycle method.
     */
    @Override
    public void onDestroy() 
    {
        mGridView.setAdapter(null);

   	    super.onDestroy();
    }
    
    /**
     * Override fragment lifecycle method.
     */
    @Override
    public void onSaveInstanceState (Bundle outState) 
    {
        outState.putInt(KEY_THUMB_POSITION, mGridView.getFirstVisiblePosition());
        super.onSaveInstanceState(outState);
    }

    /** 
     * Defines a default (dummy) share intent to initialize the action provider.
     * However, as soon as the actual content to be used in the intent is known 
     * or changes, you must update the share intent by again calling
     * mShareActionProvider.setShareIntent()
     */
//  private void createDefaultIntent() 
//  {
//      Intent intent = new Intent(Intent.ACTION_SEND);
//      intent.setType("image/*");
//      mShareActionProvider.setShareIntent(intent);
//  }

//	@SuppressLint("InlinedApi") @SuppressWarnings("deprecation")
//	private void createShareIntent() 
//  {
//      Intent shareIntent = new Intent(Intent.ACTION_SEND_MULTIPLE);
//      if (Build.VERSION.SDK_INT < 21) 
//      {
//          shareIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
//      }
//      else
//      {
//          shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
//      }
//      
//      shareIntent.setType("image/*");
//
//      ArrayList<Uri> files = new ArrayList<Uri>();
// 	    final SparseBooleanArray checkedItems = mGridView.getCheckedItemPositions();                    
//
// 	    for (int i = 0; i < checkedItems.size(); i++)
// 	    {
// 	        final boolean isChecked = checkedItems.valueAt(i);
// 	        if (isChecked)
// 	        {
// 	            int position = checkedItems.keyAt(i);
//        	    String path = mMediaList.get(position);
//              File file = new File(path);
//              if(file.exists())
//              {
//                  Uri uri = Uri.fromFile(file);
//                  files.add(uri);
//              }
// 	        }
// 	    }
// 	    shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, files);
//
//      shareIntent.putExtra(android.content.Intent.EXTRA_SUBJECT,
//  		    getResources().getString(R.string.emailSubjectGeneric));
//
//      shareIntent.putExtra(android.content.Intent.EXTRA_TEXT,
//  		    getResources().getString(R.string.emailBodyGeneric));
//
//      if(mShareActionProvider != null)
//      {
//          mShareActionProvider.setShareIntent(shareIntent);
//      }
//  }  


    /**
     * delete current file
     */
    private void deleteFiles()
    {
   	    final SparseBooleanArray checkedItems = mGridView.getCheckedItemPositions();                    

   	    ArrayList<File> mediaFiles = new ArrayList<File>();
   	    for (int i = 0; i < checkedItems.size(); i++)
   	    {
   	        final boolean isChecked = checkedItems.valueAt(i);
   	        if (isChecked)
   	        {
   	            int position = checkedItems.keyAt(i);
                File file = new File(mMediaList.get(position));
                if(file.exists())
                {
                    mediaFiles.add(file);
                }
   	        }
   	    }
   	    final ArrayList<File> mediaFilesToDelete = mediaFiles;

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getActivity(), R.style.AppCompatAlertDialogStyle);
        alertDialogBuilder.setTitle(R.string.delete);
        alertDialogBuilder.setMessage(mediaFilesToDelete.size() + " " + getString(R.string.warning_items_delete));
        
        alertDialogBuilder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface arg0, int arg1) 
            {
		        /*
		         * delete sbs/cvr, thumbnail, preview and exported files
		         */
           	    for(File mediaFile: mediaFilesToDelete)
           	    {
       				String mediaFilePath = mediaFile.getAbsolutePath();
       				MediaScanner.deleteFile(mediaFilePath);
           	    }
                updateGridView(null, mGridView.getFirstVisiblePosition());
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

    /**
     * updates the grid view by either path name OR index. either path should be
     * non-null, or index should be other than -1. 
     */
    private void updateGridView(String path, int index)
    {
        if(BuildConfig.DEBUG) Log.d(TAG, "updateGridView");

	    ArrayList<String> newMediaList = MediaScanner.getMediaList(mIsMyMedia);

	    if(newMediaList.size() == 0)
	    {
    	    /*
    	     * sanity check
    	     */
            mGridView.setVisibility(View.GONE);
            mNoMedia.setVisibility(View.VISIBLE);
    	    return;
	    }
	    else
	    {
            mGridView.setVisibility(View.VISIBLE);
            mNoMedia.setVisibility(View.GONE);
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

            MediaAdapter ma = (MediaAdapter) mGridView.getAdapter();
            ma.updateMediaList(mMediaList);

            mGridView.setSelection(index);
            mGridView.invalidateViews();
        }
	    else
	    {
	        /*
	         * we have status update to a member of our current list, identified by 
	         * its path. in this case, we find the relevant view in the grid view 
	         * and update it.
	         */
            CheckableLayout cl = null;

    	    if(path != null)
    	    {
	            cl = (CheckableLayout) (mGridView.findViewWithTag(path));
    	    }
    	    else
    	    {
                if(BuildConfig.DEBUG) Log.d(TAG, "updateGridView: trying to update an " +
            		    "existing view but path unspecified.");

                /*
                 * we cannot update a view identified by its index in the view pager. code
                 * below doesn't work. "c.f. http://stackoverflow.com/questions/12854783/
                 * android-viewpager-get-the-current-view"
                 */
	            //cl = (CheckableLayout) (mGridView.getChildAt(index));
	            //path = (String) fl.getTag();

                /*
                 * we don't seem to have other choice but to update all views
                 */
                mGridView.setSelection(index);
                mGridView.invalidateViews();
    	    }
	            
            if((cl != null) && (path != null))
            {
                if(BuildConfig.DEBUG) Log.d(TAG, "updateGridView: refresh view");
                ((MediaAdapter) mGridView.getAdapter()).refreshView(cl, path);
            }
	    }
    }

    private static class MediaAdapter extends BaseAdapter 
    {
   	    private final WeakReference<GridView> mGV;

        // references to our images
        private ArrayList<String> mPaths = new ArrayList<String>();

        // videocam image
        private Drawable mMovieIconDrawable;

        // 3d image
        private Drawable m3dIconDrawable;

        // context
        private Context mContext;

        // is this "my media" tab or "shared with me" tab
        private boolean mIsMyMedia;

        // remembers names of all sample files
        private String mSampleNames = null;

        // fade in animation
   	    private Animation mFadeInAnimation;

        public MediaAdapter(Fragment fragment, GridView gv, boolean isMyMedia) 
        {
   	        WeakReference<Fragment> a = new WeakReference<Fragment>(fragment);
            mContext = a.get().getActivity().getApplicationContext();

            mMovieIconDrawable = mContext.getResources().getDrawable(R.drawable.ic_videocam_white_24dp);
            m3dIconDrawable = mContext.getResources().getDrawable(R.drawable.ic_3d_icon);

       	    mGV = new WeakReference<GridView>(gv);
       	    mIsMyMedia = isMyMedia;
   	        mFadeInAnimation = AnimationUtils.loadAnimation(mContext, R.anim.fade);
        }

        public void updateMediaList(ArrayList<String> list) 
        {
        	    mPaths = list;
        }

        public int getCount() 
        {
            return mPaths.size();
        }

        public Object getItem(int position) 
        {
            return null;
        }

        public long getItemId(int position) 
        {
            return 0;
        }

        private boolean isPositionChecked(int position)
        {
       	    /*
       	     *  Get all of the items that have been clicked - either on or off
       	     */
       	    final SparseBooleanArray checkedItems = mGV.get().getCheckedItemPositions();
        	    
   	        for (int i = 0; i < checkedItems.size(); i++)
   	        {
   	            final boolean isChecked = checkedItems.valueAt(i);
   	            if (isChecked)
   	            {
   	                if(position == checkedItems.keyAt(i))
   	                {
                	    return true;
   	                }
   	            }
   	        }
   	        return false;
        }

        // create a new ImageView for each item referenced by the Adapter
        public View getView(int position, View convertView, ViewGroup parent) 
        {
       	    String path = mPaths.get(position);

            CheckableLayout cl = null;
            ImageView imageView = null;
            ImageView movieIconView = null;
            ProgressBar progBar = null;
            TextView tagText = null;
            ImageView modeIconView = null;

       	    int columnWidth;

            if (convertView == null) 
            {
           	    columnWidth = ((GridView)parent).getColumnWidth();

                cl = new CheckableLayout(mContext);
                cl.setLayoutParams(new GridView.LayoutParams(columnWidth, columnWidth));

                imageView = new ImageView(mContext);
                imageView.setLayoutParams(new ViewGroup.LayoutParams(columnWidth, columnWidth));
                imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                imageView.setPadding(0, 0, 0, 0);

                cl.addView(imageView);

   		        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(columnWidth/4, columnWidth/4);
                layoutParams.gravity = Gravity.BOTTOM | Gravity.START;
                movieIconView = new ImageView(mContext);
                movieIconView.setLayoutParams(layoutParams);
                movieIconView.setPadding(columnWidth/16, 0, 0, columnWidth/16);
                movieIconView.setImageDrawable(mMovieIconDrawable);

                cl.addView(movieIconView);
                
                progBar = new ProgressBar(mContext, null, android.R.attr.progressBarStyleSmall);
   		        layoutParams = new FrameLayout.LayoutParams(columnWidth/2, columnWidth/2);
                layoutParams.gravity = Gravity.CENTER;
                progBar.setLayoutParams(layoutParams);

                cl.addView(progBar);
                
                tagText = new TextView(mContext);
   		        layoutParams = new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, columnWidth/4);
                layoutParams.gravity = Gravity.TOP | Gravity.END;
                layoutParams.setMargins(0, 0, 5, 0);
                tagText.setLayoutParams(layoutParams);
                tagText.setTextAppearance(mContext, R.style.ProgText);
                tagText.setVisibility(View.VISIBLE);

                cl.addView(tagText);

                layoutParams = new FrameLayout.LayoutParams(columnWidth/4, columnWidth/4);
                layoutParams.gravity = Gravity.TOP | Gravity.START;
                modeIconView = new ImageView(mContext);
                modeIconView.setLayoutParams(layoutParams);
                modeIconView.setPadding(columnWidth/16, 0, 0, columnWidth/16);
                modeIconView.setImageDrawable(m3dIconDrawable);

                cl.addView(modeIconView);
            }
            else
            {
                cl = (CheckableLayout) convertView;
                imageView = (ImageView) cl.getChildAt(0);

                BitmapDrawable bd = (BitmapDrawable) imageView.getDrawable();
                if(bd != null)
                {
                    Bitmap bitmap = bd.getBitmap();
                    if(bitmap != null)
                    {
               	        bitmap.recycle();
               	        imageView.setImageBitmap(null);
                    }
                    bd.setCallback(null);
                }

                /*
                 * we figure out from mGridView whether this image was checked before and 
                 * re-apply if it is. we only need to do this check when we are recycling,
                 * i.e., convertView != null
                 */
                if(isPositionChecked(position))
                {
           	        cl.setChecked(true);
                }
                else
                {
           	        cl.setChecked(false);
                }
            }

            cl.setTag(path);
            refreshView(cl, path);
            return cl;
        }
        
        public void refreshView(CheckableLayout cl, String path) 
        {
  	        String mediaName = (new File(path)).getName();
            String thumbPath = MediaScanner.getThumbPathFromOrigName(mediaName);
            String previewPath = null;
  
            boolean thumbExists = (new File(thumbPath)).exists();
            boolean isMovie = MediaScanner.isMovie(mediaName);
            boolean isSample = false;
            boolean is2d = false;

            if(isMovie)
            {
           	    previewPath = MediaScanner.getPreviewPathFromOrigName(mediaName);

                is2d = MediaScanner.is2d(mediaName);

           	    if(!mIsMyMedia)
           	    {
           	        isSample = isSample(previewPath);
           	    }
            }
            else
            {
           	    if(!mIsMyMedia)
           	    {
           	        isSample = isSample(path);
           	    }
            }

            ImageView imageView = (ImageView)cl.getChildAt(0);
            ImageView movieIconView = (ImageView)cl.getChildAt(1);
            ProgressBar progBar = (ProgressBar)cl.getChildAt(2);
            TextView tagText = (TextView)cl.getChildAt(3);
            ImageView modeIconView = (ImageView)cl.getChildAt(4);

            if(thumbExists)
            {
                Bitmap thumb = BitmapFactory.decodeFile(thumbPath);
                if(thumb != null)
                {
                    imageView.setImageBitmap(thumb);
   	                imageView.startAnimation(mFadeInAnimation);
                }
                else
                {
                    if(BuildConfig.DEBUG) Log.e(TAG, "refreshView: cannot decode thumbnail!" +
                    		" path = " + path);
                }

                imageView.setVisibility(View.VISIBLE);
                progBar.setVisibility(View.GONE);
                tagText.setVisibility(View.VISIBLE);

                if(isSample)
                {
                    tagText.setText("sample");
                }
                else
                {
                    tagText.setText("");
                }

                if(is2d)
                {
                    modeIconView.setVisibility(View.INVISIBLE);
                }
                else
                {
                    modeIconView.setVisibility(View.VISIBLE);
                }
            }
            else
            {
                imageView.setVisibility(View.GONE);
                progBar.setVisibility(View.VISIBLE);
                tagText.setVisibility(View.GONE);
                modeIconView.setVisibility(View.GONE);

                if(!mIsMyMedia)
                {
    	            /*
    	             * start the background service to download preview/photo (which will be
    	             * used to generate the thumb)
    	             */
                    Intent serviceIntent = new Intent(mContext, FileDownloaderService.class);
                    if(isMovie)
                    {
    	                serviceIntent.putExtra(MainConsts.EXTRA_PATH, previewPath);
	                    if(BuildConfig.DEBUG) Log.d(TAG, "refreshView: download " + previewPath);
                    }
                    else
                    {
    	                serviceIntent.putExtra(MainConsts.EXTRA_PATH, path);
	                    if(BuildConfig.DEBUG) Log.d(TAG, "refreshView: download " + path);
                    }
                    mContext.startService(serviceIntent);
                }
            }

            if(isMovie)
            {
                movieIconView.setVisibility(View.VISIBLE);
            }
            else
            {
                movieIconView.setVisibility(View.GONE);
            }
            
            cl.invalidate();
        }
        
        public boolean isSample(String path) 
        {
       	    String name = (new File(path)).getName();

            if(mSampleNames == null)
       	    {
                Resources res = mContext.getResources();

       			String samplesLinkPath = MainConsts.MEDIA_3D_ROOT_PATH +
                    res.getString(R.string.samples_link_file_name) + "." + MainConsts.LINK_EXTENSION;

       			try {
                    FileInputStream fis = new FileInputStream(new File(samplesLinkPath));
                    BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
                    StringBuilder sb = new StringBuilder();
                    String line = null;
                    while ((line = reader.readLine()) != null) 
                    {
                        sb.append(line).append("\n");
                    }
                    reader.close();
                    fis.close();        
                    mSampleNames = sb.toString();
       			} catch (Exception e) {
                    if(BuildConfig.DEBUG) Log.e(TAG, "isSample: error reading samples.lnk " + 
                        e.getMessage());
                    return false;
                }
       	    }
            
            if(mSampleNames != null)
       	    {
                return mSampleNames.contains(name);
       	    }
            else
            {
           	    return false;
            }
        }
    }

    private static class CheckableLayout extends FrameLayout implements Checkable 
    {
        private boolean mChecked;

        public CheckableLayout(Context context) 
        {
            super(context);
        }

        public void setChecked(boolean checked) 
        {
            mChecked = checked;
            //setBackground(checked ?
                    //getResources().getDrawable(R.drawable.blue)
                    //: null);
            setForeground(checked ?
                    getResources().getDrawable(R.drawable.blue_highlight_image)
                    : null);
        }

        public boolean isChecked() 
        {
            return mChecked;
        }

        public void toggle() 
        {
            setChecked(!mChecked);
        }
    }

    private class MultiChoiceModeListener implements GridView.MultiChoiceModeListener 
    {
        public boolean onCreateActionMode(ActionMode mode, Menu menu) 
        {
            mode.setTitle("Select Items");
            mode.setSubtitle("One item selected");
            
            MenuInflater inflater = getActivity().getMenuInflater();
            inflater.inflate(R.menu.activity_thumb_select, menu);

//     	    final ActionMode savedMode = mode;
//
//          /* 
//           * Fetch and store ShareActionProvider
//           */
//          MenuItem item = menu.findItem(R.id.action_share);
//          mShareActionProvider = (ShareActionProvider) item.getActionProvider();
//          mShareActionProvider.setOnShareTargetSelectedListener(new OnShareTargetSelectedListener() {
//
//              @Override
//              public boolean onShareTargetSelected(ShareActionProvider arg0, Intent arg1)
//              {
//                  //(new FileFormatDialogFragment()).show(getFragmentManager(), "FileFormatDialogFragment"); 
//              	    savedMode.finish();
//                  return false;
//              }
//          });
//
//          createDefaultIntent();

            return true;
        }

        public boolean onPrepareActionMode(ActionMode mode, Menu menu) 
        {
            return true;
        }

        public boolean onActionItemClicked(ActionMode mode, MenuItem item) 
        {
            switch (item.getItemId()) 
            {
            case R.id.action_delete:
           	    deleteFiles();
           	    mode.finish();
                return true;
            default:
           	    return false;
            }
        }

        public void onDestroyActionMode(ActionMode mode) 
        {
        }

        public void onItemCheckedStateChanged(ActionMode mode, int position, long id,
                boolean checked) 
        {
            int selectCount = mGridView.getCheckedItemCount();
            switch (selectCount) 
            {
            case 0:
                mode.finish();
                break;
            case 1:
                mode.setSubtitle("One item selected");
                break;
            default:
                mode.setSubtitle("" + selectCount + " items selected");
                break;
            }
//          createShareIntent();
        }
    }
}