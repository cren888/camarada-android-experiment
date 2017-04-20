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
import com.aimfire.audio.AudioDebug;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

/**
 * This Fragment shows the first screen of the result plot.
 */
@SuppressLint("ValidFragment")
public class ResultFirstFragment extends Fragment implements OnClickListener {

    public static final String TAG = "FIRST";
    private boolean isInitiator;
    private boolean isSuccess;

    private OnResultNavListener mCallback;
    private Button mPrevBtn, mNextBtn;

    public ResultFirstFragment(boolean isInit, boolean isSuccess)
    {
    	    this.isInitiator = isInit;
    	    this.isSuccess = isSuccess;
    }

    @Override
    public void onAttach(Activity activity) 
    {
        super.onAttach(activity);

        // Set the callback activity to use
        try {
            mCallback = (OnResultNavListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(
                    activity.toString() + " must implement OnResultNavListener");
        }
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_result_first, container, false);

        mPrevBtn = (Button) v.findViewById(R.id.prev_btn);
        mPrevBtn.setOnClickListener(this);
        
        mNextBtn = (Button) v.findViewById(R.id.next_btn);
        mNextBtn.setOnClickListener(this);
        
        // Create the view
        AudioDebug.plotRemoteSignal((Context)getActivity(), isInitiator, isSuccess, 
        		                        (LinearLayout) v.findViewById(R.id.waveformLayout1), 
        		                        (LinearLayout) v.findViewById(R.id.waveformLayout2), 
        		                        (LinearLayout) v.findViewById(R.id.waveformLayout3), 
        		                        (LinearLayout) v.findViewById(R.id.phaseLayout1), 
        		                        (LinearLayout) v.findViewById(R.id.phaseLayout2));

        return v;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.prev_btn:
                mCallback.done();
                break;
            case R.id.next_btn:
                mCallback.fromToFragment(TAG, ResultSecondFragment.TAG);
                break;
        }
    }
}
