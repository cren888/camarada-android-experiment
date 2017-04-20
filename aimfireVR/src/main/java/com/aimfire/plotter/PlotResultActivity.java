package com.aimfire.plotter;

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
import com.aimfire.main.MainConsts;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.Window;

/**
 * This activity holds the Fragments of the result plots and coordinates
 * communication.
 */
public class PlotResultActivity extends Activity implements OnResultNavListener 
{
    boolean isInitiator;
    boolean isSuccess;

    @Override
    protected void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_plotresult);

        Intent intent = getIntent();
        isInitiator = intent.getBooleanExtra(MainConsts.EXTRA_INITIATOR, false);
        isSuccess = intent.getBooleanExtra(MainConsts.EXTRA_RESULT, true);
        
        FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
        fragmentTransaction.add(R.id.result_frame, 
        		                    new ResultFirstFragment(isInitiator, isSuccess), ResultFirstFragment.TAG);
        fragmentTransaction.commit();
    }

    @Override
    public void fromToFragment(String fromTag, String toTag) 
    {
        FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
        Fragment fragmentFrom = getFragmentManager().findFragmentByTag(fromTag);
        Fragment fragmentTo = getFragmentManager().findFragmentByTag(toTag);

        if(fragmentTo != null)
        {
            fragmentTransaction.attach(fragmentTo);
        }
        else
        {
        	    Fragment newFrag = null;
        	    /*
        	     * switch statement not allowed on String
        	     */
        	    if(toTag.equals(ResultFirstFragment.TAG))
        	    {
        	        newFrag = new ResultFirstFragment(isInitiator, isSuccess);
        	    }
        	    else if(toTag.equals(ResultSecondFragment.TAG))
        	    {
        	        newFrag = new ResultSecondFragment(isInitiator, isSuccess);
        	    }

            fragmentTransaction.add(R.id.result_frame, newFrag, toTag);
        }

        if(fragmentFrom != null)
        {
            fragmentTransaction.detach(fragmentFrom);
        }

        fragmentTransaction.commit();
    }

    public void done() 
    {
        FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
        Fragment fragment1 = getFragmentManager().findFragmentByTag(ResultFirstFragment.TAG);
        Fragment fragment2 = getFragmentManager().findFragmentByTag(ResultSecondFragment.TAG);
        if (fragment1 != null) 
        {
            fragmentTransaction.remove(fragment1);
        }
        if (fragment2 != null) 
        {
            fragmentTransaction.remove(fragment2);
        }
        fragmentTransaction.commit();
        finish();
    }
    
    @Override
    public void onConfigurationChanged(Configuration newConfig) 
    {
        super.onConfigurationChanged(newConfig);
        
        /*
         * force IntroActivity in portrait mode
         */
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }
}
