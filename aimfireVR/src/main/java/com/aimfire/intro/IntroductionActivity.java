package com.aimfire.intro;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.aimfire.camarada.BuildConfig;
import com.aimfire.camarada.R;
import com.aimfire.main.MainConsts;

import android.app.Activity;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.View.OnClickListener;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * This activity holds the Fragments of the introduction 
 */
public class IntroductionActivity extends FragmentActivity 
{
    private static final String TAG = "IntroductionActivity";

	private final static int NUM_PAGES = 4;

	private static List<String> sPageTitles;
	private static List<String> sPageDesc;

	private static int[] sPageGraphics0 = new int[]{ R.drawable.intro_welcome_0, R.drawable.intro_welcome_1, R.drawable.intro_welcome_2};
    private static int[] sPageGraphics1 = new int[]{ R.drawable.intro_howitworks};
    private static int[] sPageGraphics2 = new int[]{ R.drawable.intro_capture};
    private static int[] sPageGraphics3 = new int[]{ R.drawable.intro_adjust};

    private IntroCollectionPagerAdapter mIntroCollectionPagerAdapter;
    private ViewPager mViewPager;
    private List<ImageView> dots;
    private Button mNextBtn;
    private Button mSkipBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_introduction);

        // ViewPager and its adapters use support library
        // fragments, so use getSupportFragmentManager.
        mIntroCollectionPagerAdapter =
                new IntroCollectionPagerAdapter(
                        getSupportFragmentManager());
        mViewPager = (ViewPager) findViewById(R.id.intro_pager);
        mViewPager.setAdapter(mIntroCollectionPagerAdapter);
        mViewPager.setPageTransformer(true, new ZoomOutPageTransformer());

        mNextBtn = (Button) findViewById(R.id.next_button);
        mSkipBtn = (Button) findViewById(R.id.skip_button);

        mNextBtn.setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
           	    int pageNum = mViewPager.getCurrentItem();
            	    
           	    if(pageNum == (NUM_PAGES-1))
           	    {
           	        //done
                    updateIntroPrefs();
                    setResult(Activity.RESULT_OK, null);
           	        finish();
           	    }
           	    else
           	    {
           	        mViewPager.setCurrentItem(pageNum+1);
           	    }
            } 
        }); 

        mSkipBtn.setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
           	    //done
                updateIntroPrefs();
                setResult(Activity.RESULT_CANCELED, null);
           	    finish();
            } 
        }); 

        addDots();
        selectDot(0);

	    sPageTitles = Arrays.asList(getResources().getStringArray(R.array.introPageTitles));
	    sPageDesc = Arrays.asList(getResources().getStringArray(R.array.introPageDescs));
    }

    @SuppressWarnings("deprecation")
	public void addDots() 
    {
        dots = new ArrayList<ImageView>();
        LinearLayout dotsLayout = (LinearLayout)findViewById(R.id.dots);

        for(int i = 0; i < NUM_PAGES; i++) 
        {
            ImageView dot = new ImageView(this);
            dot.setImageDrawable(getResources().getDrawable(R.drawable.pager_dot_not_selected));

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );

		    params.setMargins(20, 0, 20, 0);
            dotsLayout.addView(dot, params);

            dots.add(dot);
        }

        mViewPager.setOnPageChangeListener(new ViewPager.OnPageChangeListener() 
        {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) 
            {
            	    updateNavBar(position);
            }

            @Override
            public void onPageScrollStateChanged(int state) 
            {
            }
        });
    }

    private void updateIntroPrefs() 
    {
        SharedPreferences settings = getSharedPreferences(getString(R.string.settings_file), 
        		Context.MODE_PRIVATE);

        if (settings != null) 
        {
             SharedPreferences.Editor editor = settings.edit();
             editor.putBoolean(MainConsts.SHOW_INTRO_PREFS_KEY, false);
             editor.commit();
         }
    }

    private void updateNavBar(int idx) 
    {
        if(idx == (NUM_PAGES-1))
        {
       	    mNextBtn.setText("DONE");
            mSkipBtn.setVisibility(View.INVISIBLE);
        }
        else
        {
       	    mNextBtn.setText("NEXT");
            mSkipBtn.setVisibility(View.VISIBLE);
        }

        selectDot(idx);
    }

    private void selectDot(int idx) 
    {
        Resources res = getResources();
        for(int i = 0; i < NUM_PAGES; i++)
        {
            int drawableId = (i==idx)?(R.drawable.pager_dot_selected):(R.drawable.pager_dot_not_selected);
            Drawable drawable = res.getDrawable(drawableId);
            dots.get(i).setImageDrawable(drawable);
        }
    }

    /*
     * Since we may have a relatively large set of intro fragments, make this 
     * an object collection, and use a FragmentStatePagerAdapter, NOT a 
     * FragmentPagerAdapter.
     */
    public class IntroCollectionPagerAdapter extends FragmentStatePagerAdapter 
    {
        public IntroCollectionPagerAdapter(FragmentManager fm) 
        {
            super(fm);
        }
    
        @Override
        public Fragment getItem(int i) 
        {
            Fragment fragment = new IntroObjectFragment();
            Bundle args = new Bundle();
            // Our object is just an integer :-P
            args.putInt(IntroObjectFragment.ARG_OBJECT, i);
            fragment.setArguments(args);
            return fragment;
        }
       
        @Override
        public int getCount() 
        {
            return NUM_PAGES;
        }
       
        @Override
        public CharSequence getPageTitle(int position) 
        {
            return "OBJECT " + (position + 1);
        }
    }

    /*
     * Instances of this class are fragments representing a single
     * object in our collection.
     */
    public static class IntroObjectFragment extends Fragment
    {
        public static final String ARG_OBJECT = "object";

        @Override
        public View onCreateView(LayoutInflater inflater,
                                 ViewGroup container, Bundle savedInstanceState)
        {
            // The last two arguments ensure LayoutParams are inflated
            // properly.
            View rootView = inflater.inflate(
                    R.layout.intro_collection_object, container, false);
            Bundle args = getArguments();
            int pageNum = args.getInt(ARG_OBJECT);

            ((TextView) rootView.findViewById(R.id.intro_subtitle)).setText(
                    sPageTitles.get(pageNum));
            ((TextView) rootView.findViewById(R.id.intro_desc)).setText(
                    sPageDesc.get(pageNum));

            int[] pictureIds = sPageGraphics0;
            switch (pageNum) {
                case 0:
                    ((LinearLayout) rootView).setBackground(getResources().getDrawable(R.drawable.background_instagram));
                    pictureIds = sPageGraphics0;
                    break;
                case 1:
                    ((LinearLayout) rootView).setBackground(getResources().getDrawable(R.drawable.background_purple_bliss));
                    pictureIds = sPageGraphics1;
                    break;
                case 2:
                    ((LinearLayout) rootView).setBackground(getResources().getDrawable(R.drawable.background_predawn));
                    pictureIds = sPageGraphics2;
                    break;
                case 3:
                    //((LinearLayout)rootView).setBackground(getResources().getDrawable(R.drawable.background_red_mist));
                    ((LinearLayout) rootView).setBackground(getResources().getDrawable(R.drawable.background_sage_percussion));
                    pictureIds = sPageGraphics3;
                    break;
                default:
                    if (BuildConfig.DEBUG) Log.e(TAG, "onCreateView: page number out of bound");
                    break;
            }

            ImageView iv = (ImageView) rootView.findViewById(R.id.intro_pictures);
            slideShow(iv, pictureIds, 0, true);

            return rootView;
        }

        /**
         * imageView <-- The View which displays the images
         * images[] <-- Holds R references to the images to display
         * imageIndex <-- index of the first image to show in images[]
         * forever <-- If equals true then after the last image it starts all over again with the first image resulting in an infinite loop. You have been warned.
         * c.f. http://stackoverflow.com/questions/8720626/android-fade-in-and-fade-out-with-imageview/10471479#10471479
         */
        private void slideShow(final ImageView imageView, final int images[], final int imageIndex, final boolean forever)
        {
            int fadeInDuration = 1000; // Configure time values here
            int timeBetween = 3000;
            int fadeOutDuration = 1000;

            if(images.length == 1)
            {
                // if we have only one image, then no slide show
                imageView.setVisibility(View.VISIBLE);
                imageView.setImageResource(images[0]);
                return;
            }

            imageView.setVisibility(View.INVISIBLE);    //Visible or invisible by default - this will apply when the animation ends
            imageView.setImageResource(images[imageIndex]);

            Animation fadeIn = new AlphaAnimation(0, 1);
            fadeIn.setInterpolator(new AccelerateInterpolator()); // and this
            fadeIn.setDuration(fadeInDuration);

            Animation fadeOut = new AlphaAnimation(1, 0);
            fadeOut.setInterpolator(new DecelerateInterpolator()); // add this
            fadeOut.setStartOffset(fadeInDuration + timeBetween);
            fadeOut.setDuration(fadeOutDuration);

            AnimationSet animation = new AnimationSet(false); // change to false
            animation.addAnimation(fadeIn);
            animation.addAnimation(fadeOut);
            animation.setRepeatCount(1);
            imageView.setAnimation(animation);

            animation.setAnimationListener(new Animation.AnimationListener() {
                public void onAnimationEnd(Animation animation)
                {
                    if(imageIndex < (images.length - 1))
                    {
                        slideShow(imageView, images, imageIndex + 1, forever); //Calls itself until it gets to the end of the array
                    }
                    else
                    {
                        if (forever == true)
                        {
                            slideShow(imageView, images, 0, forever);  //Calls itself to start the animation all over again in a loop if forever = true
                        }
                    }
                }

                public void onAnimationRepeat(Animation animation) { }

                public void onAnimationStart(Animation animation) { }
            });
        }
    }

    /**
     * c.f. "http://developer.android.com/training/animation/screen-slide.html"
     */
    public class ZoomOutPageTransformer implements ViewPager.PageTransformer 
    {
        private static final float MIN_SCALE = 0.95f;
        private static final float MIN_ALPHA = 0.5f;

        public void transformPage(View view, float position) 
        {
            int pageWidth = view.getWidth();
            int pageHeight = view.getHeight();

            if (position < -1)  // [-Infinity,-1)
           	{
                // This page is way off-screen to the left.
                view.setAlpha(0);

            } 
            else if (position <= 1)  // [-1,1]
           	{
                // Modify the default slide transition to shrink the page as well
                float scaleFactor = Math.max(MIN_SCALE, 1 - Math.abs(position));
                float vertMargin = pageHeight * (1 - scaleFactor) / 2;
                float horzMargin = pageWidth * (1 - scaleFactor) / 2;
                if (position < 0) 
                {
                    view.setTranslationX(horzMargin - vertMargin / 2);
                } 
                else 
                {
                    view.setTranslationX(-horzMargin + vertMargin / 2);
                }

                // Scale the page down (between MIN_SCALE and 1)
                view.setScaleX(scaleFactor);
                view.setScaleY(scaleFactor);

                // Fade the page relative to its size.
                view.setAlpha(MIN_ALPHA +
                        (scaleFactor - MIN_SCALE) /
                        (1 - MIN_SCALE) * (1 - MIN_ALPHA));

            } 
            else  // (1,+Infinity]
           	{
                // This page is way off-screen to the right.
                view.setAlpha(0);
            }
        }
    }
}