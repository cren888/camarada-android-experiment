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

import com.aimfire.audio.AudioConfigure;
import com.aimfire.camarada.R;
import com.aimfire.constants.ActivityCode;
import com.aimfire.demo.CamcorderActivity;
import com.aimfire.gallery.CopyToClipboardActivity;
import com.aimfire.service.AimfireService;
import com.aimfire.service.AimfireServiceConn;
import com.aimfire.utilities.FileUtils;
import com.aimfire.backend.Consts;
import com.aimfire.camarada.BuildConfig;
import com.aimfire.gallery.MediaScanner;
import com.aimfire.gallery.ThumbsFragment;
import com.aimfire.gallery.service.SamplesDownloader;
import com.aimfire.intro.IntroductionActivity;
import com.google.firebase.analytics.FirebaseAnalytics;

import android.accounts.AccountManager;
import android.app.Activity;
import android.content.ComponentName;
import android.content.pm.LabeledIntent;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.ActionBar.Tab;
import android.support.v7.app.ActionBar.TabListener;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Surface;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Toast;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import uk.co.samuelwall.materialtaptargetprompt.MaterialTapTargetPrompt;

/**
 * MainActivity for AimFireVR
 */
@SuppressWarnings("deprecation")
public class MainActivity extends AppCompatActivity implements TabListener
{
    private static final String TAG = "MainActivity";

    private static final String KEY_TAB_POSITION = "KTP";

    public static final int TAB_INDEX_MY_MEDIA = 0;
    public static final int TAB_INDEX_SHARED_MEDIA = 1;
    private static final int NUM_OF_TABS = 2;

    /*
     * show intro at first launch after installation (will not show for app update)
     */
    private boolean mShowIntro = true;

    /*
     * show survey to the user
     */
    private boolean mShowSurvey = false;

    /*
     * how many times we are launched by the user
     */
    private int mLaunchCount = 0;

    /*
     * whether we have already incremented count for the current user launch (this static variable
     * is used to make sure we don't increment the count if user switches our app between back and
     * foreground)
     */
    private static boolean sLaunchCountInc = false;

    /*
     * if a new version is available
     */
    private boolean mUpgradeAvailable = false;

    /*
     * firebase analytics
     */
    private FirebaseAnalytics mFirebaseAnalytics;

    /*
     * UI components 
     */
    private ViewPager mThumbsPager;
    private ThumbsPagerAdapter mThumbsPagerAdapter;
    private Tab mMyMediaTab;
    private Tab mSharedMediaTab;
    private int mTabPosition;
    private FloatingActionButton mCameraFab;

    /*
     * AimfireService object
     */
	private AimfireService mAimfireService;
    private AimfireServiceConn mAimfireServiceConn;

    /*
     * cloud backend ready flags. true if signed in to google cloud service
     * regReq with cloud 
     */
	private boolean mCloudReady = false;

    /**
     * BroadcastReceiver for incoming cloud service messages.
     */
    private BroadcastReceiver mAimfireServiceMsgReceiver = new BroadcastReceiver() 
    {
        @Override
        public void onReceive(Context context, Intent intent) 
        {
           int messageCode = intent.getIntExtra(MainConsts.EXTRA_WHAT, -1);
            switch(messageCode)
            {
		    case MainConsts.MSG_AIMFIRE_SERVICE_CLOUD_SERVICE_READY:
 			    /*
 			     * if cloud service is ready (signed in)
 			     */
 		        onCloudServiceReady();
                break;
            default:
                break;
            }
        }
    };

    Runnable mAimfireServiceInitTask = new Runnable() {
	    public void run() 
	    {
    	    while((mAimfireService = mAimfireServiceConn.getAimfireService()) == null)
    	    {
    	        try {
				    Thread.sleep(10);
			    } catch (InterruptedException e) {
                    if(BuildConfig.DEBUG) Log.d(TAG, "mAimfireServiceInitTask interrupted, exiting");
				    return;
			    }
    	    }

		    /*
		     * check if credential is available (first time launch), this will start 
		     * a google activity to sign in if authentication is required AND account
		     * was not set. if account name was set (not first time launch), then the
		     * cloud backend service has already signed in, thus we do nothing. ideally 
		     * the backend service should handle all of this by itself, but being a 
		     * service, it doesn't support launching an activity (the account picker) 
		     * for result. therefore we have to do it here
		     */
   		    if(!mAimfireService.isCloudReady())
   		    {
		        chooseAccount(false/*overrideCurrent*/);
   		    }
	    }
    };

    /*
     * will launch the intro activity (if necessary) after MainActivity 
     * onCreate/onResume is done
     */
    private Runnable mIntroTask = new Runnable() {
        public void run() 
        {
             Intent intent = new Intent(MainActivity.this, IntroductionActivity.class);
             startActivityForResult(intent, ActivityCode.INTRO.getValue());
        }
    };

    /*
     * will launch the survey activity (if necessary) after MainActivity
     * onCreate/onResume is done
     */
    private Runnable mSurveyTask = new Runnable() {
        public void run()
        {
            Intent intent = SurveyActivity.createIntent(MainActivity.this);
            startActivity(intent);
        }
    };

    /**
     * Override Activity lifecycle method.
     */
	@Override
    protected void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) 
        {
       	    /*
       	     * if this is initial launch, start the service
       	     */
            startService(new Intent(this, AimfireService.class));
        }

        PreferenceManager.setDefaultValues(this,getString(R.string.settings_file),MODE_PRIVATE,R.xml.pref_settings,true);

        /*
         * check persistence storage, if this is the first time we run, save certain
         * flags (like features available). check showIntro flag and show intro if 
         * necessary
         */
        checkPreferences();

        /*
         * set up UI
         */
        setContentView(R.layout.activity_main);

        /*
         * show overflow button even if we have a physical menu button
         */
        forceOverflowButton();

        /*
         * make a backup if we find old, non-compatible cvr files
         */
        if(mShowIntro)
        {
       	    checkOldCvr();
        }

        /*
         * Obtain the FirebaseAnalytics instance.
         */
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

        /*
         * create directories if not exist
         */
        if (!FileUtils.initStorage())
        {
		    Toast.makeText(this, R.string.error_accessing_storage, Toast.LENGTH_LONG).show();
       	    finish();
        }

        mThumbsPagerAdapter = new ThumbsPagerAdapter(getSupportFragmentManager());

        mThumbsPager = (ViewPager)findViewById(R.id.thumbs_pager);
        mThumbsPager.setAdapter(mThumbsPagerAdapter);

        mThumbsPager.setOnPageChangeListener(
            new ViewPager.SimpleOnPageChangeListener() {
                @Override
                public void onPageSelected(int position)
                {
                    // When swiping between pages, select the
                    // corresponding tab.
               	    mTabPosition = position;
                    getSupportActionBar().setSelectedNavigationItem(position);
                }
            });


        ActionBar bar = getSupportActionBar();
        bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

        mMyMediaTab = bar.newTab();
        mMyMediaTab.setText(getResources().getString(R.string.tab_my_media_name));
        mMyMediaTab.setTabListener(this);

        mSharedMediaTab = bar.newTab();
        mSharedMediaTab.setText(getResources().getString(R.string.tab_shared_with_me_name));
        mSharedMediaTab.setTabListener(this);

        bar.addTab(mMyMediaTab, TAB_INDEX_MY_MEDIA);
        bar.addTab(mSharedMediaTab, TAB_INDEX_SHARED_MEDIA);

        /*
         * first launch of this activity, "Shared with Me" tab is the default.
         */
        if (savedInstanceState != null) 
        {
       	    /*
       	     * restore tab position after screen rotation
       	     */
            mTabPosition = savedInstanceState.getInt(KEY_TAB_POSITION);
            if(BuildConfig.DEBUG) Log.d(TAG, "onCreate: restored tab position=" + mTabPosition);
        }
        else
        {
            //mTabPosition = TAB_INDEX_MY_MEDIA;
            mTabPosition = TAB_INDEX_SHARED_MEDIA;
            if(BuildConfig.DEBUG) Log.d(TAG, "onCreate: set initial tab position=" + mTabPosition);
        }

        mCameraFab = (FloatingActionButton) findViewById(R.id.cameraFAB);
        mCameraFab.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v)
            {
                WifiManager manager = (WifiManager) getSystemService(Context.WIFI_SERVICE);

                if(!manager.isWifiEnabled())
                {
                    /*
                     * if wifi not enabled, no point to continue
                     */
                    Toast.makeText(getApplicationContext(), R.string.error_wifi_off, Toast.LENGTH_LONG).show();
                    return;
                }

                if(AudioConfigure.getAudioRouting() != AudioConfigure.AUDIO_ROUTING_BUILTIN)
                {
                    Toast.makeText(getApplicationContext(), R.string.error_headset, Toast.LENGTH_LONG).show();
                    return;
                }

                mFirebaseAnalytics.logEvent(MainConsts.FIREBASE_CUSTOM_EVENT_ACTION_CAMERA, null);

   	            /*
   	             * initiate discovery request
   	             */
                mAimfireService.initDemo(true, ActivityCode.CAMCORDER.getValue(), null);
                Intent intent = new Intent(getApplicationContext(), CamcorderActivity.class);
                startActivityForResult(intent, ActivityCode.CAMCORDER.getValue());
            }
        });

        /*
         * initializes Aimfire service. if it is already started, bind to it
         */
        mAimfireServiceConn = new AimfireServiceConn(this);
        
        /*
         * binding doesn't happen until later. wait for it to happen in another 
         * thread and do the necessary initialization on it.
         */
        (new Thread(mAimfireServiceInitTask)).start();

   	    /*
   	     * display app build time in debug window
   	     */
        displayVersion();

        /*
         * show introduction with a delay (to allow onCreate init to finish)
         */
        if (mShowIntro) 
        {
            mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.TUTORIAL_BEGIN, null);

            Handler mHandler = new Handler();
            mHandler.postDelayed(mIntroTask, 1000/*ms*/);

   	        /*
   	         * check if gyroscope is supported (important for Cardboard mode).
   	         * only show warning once after fresh install
   	         */
            checkGyroscope();
        } 
        else if(mShowSurvey)
        {
            mFirebaseAnalytics.logEvent(MainConsts.FIREBASE_CUSTOM_EVENT_SHOW_SURVEY, null);

            Handler mHandler = new Handler();
            mHandler.postDelayed(mSurveyTask, 1000/*ms*/);
        }

        if ((savedInstanceState == null) && isDeviceOnline())
        {
            /*
             * check if new version is available
             */
            startService(new Intent(this, VersionChecker.class));

            /*
             * download samples in the background (if necessary)
             */
            startService(new Intent(this, SamplesDownloader.class));
            
            /*
             * notify user if upgrade is available
             */
            if(mUpgradeAvailable)
            {
	    	    notifyUpgrade();
            }
        }

        //(new Thread(mLatencyTestTask)).start();
    }

    @Override
    protected void onNewIntent(Intent intent) 
    {
        boolean isMyMedia = intent.getBooleanExtra(MainConsts.EXTRA_MSG, false);
        mTabPosition = isMyMedia?TAB_INDEX_MY_MEDIA:TAB_INDEX_SHARED_MEDIA;
   	    if(BuildConfig.DEBUG) Log.d(TAG, "onNewIntent: selected position=" + mTabPosition);
    }

    @Override
    protected void onStart() 
    {
        super.onStart();
    }
    
    /**
     * Override Activity lifecycle method.
     */
    @Override
    protected void onResume() 
    {
        /*
         * we disable the wifi on/off feature as it is quite confusing. we show only a
         * toast to warn user.
         */
        WifiManager manager = (WifiManager) getSystemService(Context.WIFI_SERVICE);

   	    if(!manager.isWifiEnabled())
   	    {
		    Toast.makeText(this, R.string.error_wifi_off, Toast.LENGTH_LONG).show();
   	    }

   	    /*
   	     * register for intents sent by the cloud backend service
   	     */
        LocalBroadcastManager.getInstance(this).registerReceiver(mAimfireServiceMsgReceiver,
                new IntentFilter(MainConsts.AIMFIRE_SERVICE_MESSAGE));

        /*
         * navigate the selected tab
         */
        getSupportActionBar().setSelectedNavigationItem(mTabPosition);

   	    super.onResume();
    }

    /**
     * Override Activity lifecycle method.
     */
    @Override
    protected void onPause() 
    {
   	    /*
   	     * unregister for intents sent by the cloud backend service
   	     */
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mAimfireServiceMsgReceiver);

   	    super.onPause();
    }

    /**
     * Override Activity lifecycle method.
     */
    @Override
    protected void onDestroy() 
    {
   	    if(mAimfireServiceConn != null)
   	        mAimfireServiceConn.unbind();

   	    /*
   	     * stop the service (we are not using cloud for discovery, so no need to keep it around)
   	     */
        stopService(new Intent(this, AimfireService.class));

   	    super.onDestroy();
    }
    
    /**
     * Override Activity lifecycle method.
     */
    @Override
    protected void onSaveInstanceState (Bundle outState) 
    {
        outState.putInt(KEY_TAB_POSITION, mTabPosition);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) 
    {
        super.onWindowFocusChanged(hasFocus);
    }


    /**
     * Override Activity lifecycle method.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) 
    {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.activity_main, menu);

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
                if(BuildConfig.DEBUG) Log.e(TAG, "onMenuOpened", e);
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
        MenuItem loginItem = menu.findItem(R.id.action_switch_account);
        loginItem.setVisible(Consts.IS_AUTH_ENABLED);

        return true;
    }

    /**
     * Override Activity lifecycle method.
     * <p>
     * To add more option menu items in your client, add the item to menu/activity_main.xml,
     * and provide additional case statements in this method.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) 
    {
   	    Intent intent;
        switch (item.getItemId()) 
        {
        case R.id.action_help:
            mFirebaseAnalytics.logEvent(MainConsts.FIREBASE_CUSTOM_EVENT_ACTION_HELP, null);
            intent = new Intent(this, IntroductionActivity.class);
            startActivity(intent);
            break;
        case R.id.action_tutorial:
            mFirebaseAnalytics.logEvent(MainConsts.FIREBASE_CUSTOM_EVENT_ACTION_TUTORIAL, null);
            intent = new Intent(Intent.ACTION_VIEW,
           		Uri.parse(getString(R.string.app_youtube_tutorial)));
            startActivity(intent);
            break;
        case R.id.action_feedback:
            mFirebaseAnalytics.logEvent(MainConsts.FIREBASE_CUSTOM_EVENT_ACTION_FEEDBACK, null);
       	    intent = new Intent(Intent.ACTION_VIEW);
       	    Uri data = Uri.parse("mailto:" + getString(R.string.app_support_email) +
   	    		"?subject=" + getString(R.string.feedbackEmailSubject));
       	    intent.setData(data);
       	    startActivity(intent);
            break;
        case R.id.action_switch_account:
            mFirebaseAnalytics.logEvent(MainConsts.FIREBASE_CUSTOM_EVENT_ACTION_SWITCH_ACCOUNT, null);
       	    mCloudReady = false;
            chooseAccount(true/*overrideCurrent*/);
            break;

            case R.id.action_settings:

                intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);

                break;

        default:
            return super.onOptionsItemSelected(item);
        }

        return true;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) 
    {
        super.onConfigurationChanged(newConfig);
        
        /*
         * force MainActivity in portrait mode
         */
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

	@Override
	public void onTabSelected(Tab tab, FragmentTransaction ft) 
	{
		mTabPosition = tab.getPosition();
        mThumbsPager.setCurrentItem(mTabPosition);
	}

	@Override
	public void onTabUnselected(Tab tab, FragmentTransaction ft)
	{
	}

	@Override
	public void onTabReselected(Tab tab, FragmentTransaction ft)
	{
	}

    public class ThumbsPagerAdapter extends FragmentPagerAdapter 
    {
        public ThumbsPagerAdapter(FragmentManager fm) 
        {
            super(fm);
        }

        @Override
        public int getCount() 
        {
            return NUM_OF_TABS;
        }

        @Override
        public Fragment getItem(int position) 
        {
			Bundle data = new Bundle();
			data.putInt(MainConsts.EXTRA_INDEX,  position);

			ThumbsFragment tf = new ThumbsFragment();
			tf.setArguments(data);
            return tf;
        }
    }

    /**
     * Override Activity lifecycle method.
     * handles media player and intro activity results
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) 
    {
        // handle request codes
   	    ActivityCode code = ActivityCode.values()[requestCode];

   	    switch(code)
   	    {
   	    case INTRO:
            mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.TUTORIAL_COMPLETE, null);
            showHint();
    	    break;
        case CAMCORDER:
            if(resultCode == Activity.RESULT_CANCELED)
            {
                /*
                 * if user didn't attempt to connect
                 */
                showInvite();
            }
            else
            {
                /*
                 * remember if this device ever attempted to pair with another device
                 */
                updateDualModePref();
            }
            break;
        case ACCOUNT_PICKER:
            if (data != null && data.getExtras() != null) 
            {
                // set the picked account name to the credential
                String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                mAimfireService.getCredential().setSelectedAccountName(accountName);
    
                // save account name to shared pref
                SharedPreferences.Editor e = getSharedPreferences(
               		Consts.PREF_KEY_CLOUD_BACKEND, Context.MODE_PRIVATE).edit();
                e.putString(Consts.PREF_KEY_ACCOUNT_NAME, accountName);
                e.commit();
            }
    
            // post create initialization
            mAimfireService.setAccount();
            break;
        default:
        	break;
  	    }

        // call super method to ensure unhandled result codes are handled
        super.onActivityResult(requestCode, resultCode, data);
    }

    public void onCloudServiceReady() 
    {
   	    if(BuildConfig.DEBUG) Log.d(TAG, "onCloudServiceReady: mCloudReady was " + mCloudReady +
    		" changing to True");
   	    mCloudReady = true;
    }


    public void showHint()
    {
        new MaterialTapTargetPrompt.Builder(this)
                .setTarget(mCameraFab)
                .setPrimaryText(R.string.cameraFabHintTitle)
                .setSecondaryText(R.string.cameraFabHintText)
                .show();
    }

    private void showInvite()
    {
        SharedPreferences settings =
                getSharedPreferences(getString(R.string.settings_file), Context.MODE_PRIVATE);

        /*
         * here settings != null doesn't mean the file necessarily exist!
         */
        if (settings != null)
        {
            boolean dualModeAttempted = settings.getBoolean(MainConsts.DUAL_MODE_ATTEMPTED_PREFS_KEY, false);
            boolean canShowInvite = !settings.getBoolean(MainConsts.DONT_SHOW_INVITE_PREFS_KEY, false);

            if(!dualModeAttempted && canShowInvite)
            {
                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this, R.style.AppCompatAlertDialogStyle);
                alertDialogBuilder.setTitle(R.string.information);
                alertDialogBuilder.setMessage(R.string.info_share_prompt);

                alertDialogBuilder.setPositiveButton(R.string.share, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        inviteFriend();
                    }
                });

                alertDialogBuilder.setNeutralButton(R.string.dontAskAgain, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        updateShowInvitePref();
                    }
                });

                alertDialogBuilder.setNegativeButton(R.string.later, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        // do nothing
                    }
                });

                AlertDialog alertDialog = alertDialogBuilder.create();
                alertDialog.show();
            }
        }
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
    private void inviteFriend()
    {
        mFirebaseAnalytics.logEvent(MainConsts.FIREBASE_CUSTOM_EVENT_INVITE, null);

        Resources resources = getResources();

        /*
         * construct link
         */
        String appLink = resources.getString(R.string.app_store_link);

        /*
         * message subject and text
         */
        String emailSubject, emailText, twitterText;

        emailSubject = resources.getString(R.string.emailSubjectInviteFriend);
        emailText = resources.getString(R.string.emailBodyInviteFriend) + appLink;
        twitterText = resources.getString(R.string.emailBodyInviteFriend) + appLink + ", " +
                resources.getString(R.string.app_hashtag);

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
                    intent.putExtra(Intent.EXTRA_TEXT, appLink);
                }
                else if(packageName.contains("tencent.mm")) //wechat
                {
               	    /*
               	     * wechat appears to do this similar to Facebook
               	     */
                    intent.putExtra(Intent.EXTRA_TEXT, appLink);
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
        clipboardIntent.setData(Uri.parse(appLink));
        intentList.add(new LabeledIntent(clipboardIntent, getPackageName(),
                getResources().getString(R.string.clipboard_activity_name), R.drawable.ic_copy_link));

        // convert intentList to array
        LabeledIntent[] extraIntents = intentList.toArray( new LabeledIntent[ intentList.size() ]);

        openInChooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, extraIntents);
        startActivity(openInChooser);
    }


    private void updateDualModePref()
    {
        SharedPreferences settings =
                getSharedPreferences(getString(R.string.settings_file), Context.MODE_PRIVATE);

        /*
         * here settings != null doesn't mean the file necessarily exist!
         */
        if (settings != null)
        {
   	        /*
   	         * disable hint for subsequent launches
   	         */
            SharedPreferences.Editor editor = settings.edit();
            editor.putBoolean(MainConsts.DUAL_MODE_ATTEMPTED_PREFS_KEY, true);
            editor.commit();
        }
    }

    private void updateShowInvitePref()
    {
        SharedPreferences settings =
                getSharedPreferences(getString(R.string.settings_file), Context.MODE_PRIVATE);

        /*
         * here settings != null doesn't mean the file necessarily exist!
         */
        if (settings != null)
        {
   	        /*
   	         * disable hint for subsequent launches
   	         */
            SharedPreferences.Editor editor = settings.edit();
            editor.putBoolean(MainConsts.DONT_SHOW_INVITE_PREFS_KEY, true);
            editor.commit();
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
     * Signs in to the application with an account. Notify App Engine of regId
     *
     * @param overrideCurrent {@code true} if user can choose an account even if
     *            already signed in, {@code false} if the user can choose an
     *            account only if there is no currently signed in user
     */
    public void chooseAccount(boolean overrideCurrent) 
    {
        if (Consts.IS_AUTH_ENABLED) 
        {
            String accountName =
                getSharedPreferences(Consts.PREF_KEY_CLOUD_BACKEND, Context.MODE_PRIVATE).
                    getString(Consts.PREF_KEY_ACCOUNT_NAME, null);
            if (accountName == null || overrideCurrent) 
            {
                super.startActivityForResult(
                		mAimfireService.getCredential().newChooseAccountIntent(),
                    ActivityCode.ACCOUNT_PICKER.getValue());
                return;
            } 
        } 
    }

	/*
	 * check if optional feature Gyroscope (use-feature required = false) is present 
	 * on the device, and warn if it is not.
	 */
    private void checkGyroscope() 
    {
   	    PackageManager pm = getPackageManager();

   	    if(!pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_GYROSCOPE))
   	    {
    	    // warn the user
            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this, R.style.AppCompatAlertDialogStyle);
   	        alertDialogBuilder.setTitle(R.string.warning);
   	        alertDialogBuilder.setMessage(R.string.warning_device_no_gyro);
    	        
   	        alertDialogBuilder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
    	            @Override
    	            public void onClick(DialogInterface dialog, int which) 
    	            {
                dialog.dismiss();
   	            }
    	        });
    	        
   	        AlertDialog alertDialog = alertDialogBuilder.create();
   	        alertDialog.show();
   	    }
    }

	/*
	 * check if old format cvr is left from previous versions. if so, notify
	 * the user that we will make a backup. also need to delete drive_record
	 */
    private void checkOldCvr() 
    {
   	    if(MediaScanner.oldCvrExists())
   	    {
    	    FileUtils.backupStorage();
    	    clearDriveFileRecord();

    	    // warn the user
            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this, R.style.AppCompatAlertDialogStyle);
   	        alertDialogBuilder.setTitle(R.string.warning);
   	        alertDialogBuilder.setMessage(getString(R.string.warning_old_cvr_found) +
        		MainConsts.MEDIA_3D_ROOT_DIR.getPath() + ".bak");
    	        
   	        alertDialogBuilder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
    	            @Override
    	            public void onClick(DialogInterface dialog, int which) 
    	            {
                dialog.dismiss();
   	            }
    	        });

   	        AlertDialog alertDialog = alertDialogBuilder.create();
   	        alertDialog.show();
   	    }
    }

	private void clearDriveFileRecord() 
    {
        SharedPreferences driveFileRecord =
                getSharedPreferences(getString(R.string.drive_file_record), Context.MODE_PRIVATE);

        if (driveFileRecord != null) 
        {
            SharedPreferences.Editor editor = driveFileRecord.edit();
            editor.clear();
            editor.commit();
        }
    }


    private void displayVersion() 
    {
		try{
			ApplicationInfo ai = getPackageManager().getApplicationInfo(getPackageName(), 0);
			ZipFile zf = new ZipFile(ai.sourceDir);
			ZipEntry ze = zf.getEntry("classes.dex");
			long time = ze.getTime();
			String s = SimpleDateFormat.getInstance().format(new java.util.Date(time));
			zf.close();

            if(BuildConfig.DEBUG) printDebugMsg("App creation time: " + s + "\n");

		}catch(Exception e){}
    }

    private void checkPreferences() 
    {
        SharedPreferences settings =
            getSharedPreferences(getString(R.string.settings_file), Context.MODE_PRIVATE);

        if (settings != null) 
        {
       	    /*
       	     * whether we show introduction at app startup
       	     */
            mShowIntro = settings.getBoolean(MainConsts.SHOW_INTRO_PREFS_KEY, true);

            /*
             * TODO: survey activity crashes because it uses attributes as color which is not
             * allowed before API level 21 (see here: http://stackoverflow.com/questions/27986204/
             * cant-convert-to-color-type-0x2-error-when-inflating-layout-in-fragment-but-onl).
             * temporarily disallow survey activity for API < 21 before we find a fix
             */
            if(!sLaunchCountInc &&
                (Build.VERSION.SDK_INT >= 21))
            {
                sLaunchCountInc = true;
                mLaunchCount= settings.getInt(MainConsts.LAUNCH_COUNT_PREFS_KEY, -1);
                mLaunchCount++;

                if((mLaunchCount == 0) && !isFirstInstall())
                {
                    /*
                     * show survey if user just upgraded (from a version that doesn't have the
                     * survey) and launch for the first time
                     */
                    mShowSurvey = true;
                }
                else
                {
                    /*
                     * show survey if
                     * 1. this app is launched PROMPT_SURVEY_LAUNCH_COUNT times. or
                     * 2. we have prompted before, and user asks to prompt later, and launch times
                     * modulo PROMPT_SURVEY_LAUNCH_COUNT is 0
                     */
                    boolean canShowSurvey = settings.getBoolean(MainConsts.SHOW_SURVEY_PREFS_KEY, true);
                    if (canShowSurvey && (mLaunchCount % MainConsts.PROMPT_SURVEY_LAUNCH_COUNT == 0))
                    {
                        mShowSurvey = true;
                    }
                }
            }

       	    /*
       	     * we show intro after first install. write to registry so we don't do it again.
       	     *
             * Offline Mode = true - we will use P2P for everything
             * Offline Mode = false - we will use cloud for initial discovery and messaging, and
             * P2P for file transfer
             *
             * set DEMO_MODE_PREFS_KEY here, for other activities and AimfireService to pick up.
       	     * to test non-offline mode, set DEMO_MODE_PREFS_KEY below to false
       	     */
            SharedPreferences.Editor editor = settings.edit();
            editor.putBoolean(MainConsts.SHOW_INTRO_PREFS_KEY, false);
            editor.putBoolean(MainConsts.DEMO_MODE_PREFS_KEY, true);
            editor.putInt(MainConsts.LAUNCH_COUNT_PREFS_KEY, mLaunchCount);
            editor.commit();
            
            if(mShowIntro)
            {
           	    return;
            }

   	        int latestCode = settings.getInt(MainConsts.LATEST_VERSION_CODE_KEY, -1);
    
   	        int currCode = -1;
   	        PackageInfo pInfo;
		    try {
			    pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
   	            currCode = pInfo.versionCode;
		    } catch (NameNotFoundException e) {
                if(BuildConfig.DEBUG) Log.e(TAG, "parseVerTxt: couldn't get current version" + e.getMessage());
			    return;
		    }

		    if(latestCode > currCode)
		    {
	    	    mUpgradeAvailable = true;
		    }
        }
    }

    private boolean isFirstInstall() {
        try {
            long firstInstallTime =  getPackageManager().getPackageInfo(getPackageName(), 0).firstInstallTime;
            long lastUpdateTime = getPackageManager().getPackageInfo(getPackageName(), 0).lastUpdateTime;
            return firstInstallTime == lastUpdateTime;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void notifyUpgrade() 
    {
   	    // warn the user
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this, R.style.AppCompatAlertDialogStyle);
        alertDialogBuilder.setTitle(R.string.notice);
        alertDialogBuilder.setMessage(R.string.warning_new_version_available);
    	        
        alertDialogBuilder.setPositiveButton(R.string.upgrade, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) 
            {
                final String appPackageName = getPackageName(); // getPackageName() from Context or Activity object
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appPackageName)));
                } catch (android.content.ActivityNotFoundException anfe) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + appPackageName)));
                }
            }
        });
    	        
        alertDialogBuilder.setNegativeButton(R.string.later, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) 
            {
     	        //do nothing
            }
         });

        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }

    public int getScreenOrientation()
    {
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_PORTRAIT) 
        {
            if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_270) 
            {
                return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
            }   
            else 
            {
                return ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
            }
        }
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) 
        {
            if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_90) 
            {
                return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
            }   
            else 
            {
                return ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
            }
        }
        return ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
    }

    /**
     * print debug message to on-screen console
     */
    public void printDebugMsg(String s)
    {
   	    if(BuildConfig.DEBUG) Log.d(TAG, s);
    	    
   	    // no longer has a on-screen console
    }
}