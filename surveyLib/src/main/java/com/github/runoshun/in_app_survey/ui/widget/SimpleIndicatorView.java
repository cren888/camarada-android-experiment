/*
 * Copyright (c) 2016 runoshun.
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

package com.github.runoshun.in_app_survey.ui.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RadioGroup;

import com.github.runoshun.in_app_survey.R;

public class SimpleIndicatorView extends RadioGroup implements IndicatorView {

    private int mIndicatorWidth = getResources().getDimensionPixelSize(R.dimen.indicator_default);
    private int mIndicatorHeight = getResources().getDimensionPixelSize(R.dimen.indicator_default);
    private int mIndicatorMargin = getResources().getDimensionPixelSize(R.dimen.indicator_margin_default);
    private int mIndicatorRes = R.drawable.indicator_normal;

    private int mCount = 0;

    public SimpleIndicatorView(Context context) {
        super(context);
        init(null);
    }

    public SimpleIndicatorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    private void init(AttributeSet attrs) {

        final TypedArray a = getContext().obtainStyledAttributes(
                attrs, R.styleable.SimpleIndicatorView, 0, 0);

        mIndicatorWidth = a.getDimensionPixelSize(
                R.styleable.SimpleIndicatorView_indicatorWidth,
                mIndicatorWidth);

        mIndicatorHeight = a.getDimensionPixelSize(
                R.styleable.SimpleIndicatorView_indicatorHeight,
                mIndicatorHeight);

        mIndicatorMargin = a.getDimensionPixelSize(
                R.styleable.SimpleIndicatorView_indicatorMargin,
                mIndicatorMargin
        );

        mIndicatorRes = a.getResourceId(
                R.styleable.SimpleIndicatorView_indicatorDrawable,
                mIndicatorRes
        );

        a.recycle();

        setOrientation(HORIZONTAL);
        setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        setGravity(Gravity.CENTER_VERTICAL);

        if (isInEditMode()) {
            initIndicators(8);
            setSelected(2);
        }
    }

    private void initIndicators(int count) {
        for (int i = 0; i < count; ++i) {
            ImageView child = new ImageView(this.getContext());
            child.setImageResource(mIndicatorRes);

            LayoutParams params = new LayoutParams(mIndicatorWidth, mIndicatorHeight);
            params.setMargins(mIndicatorMargin, mIndicatorMargin, mIndicatorMargin, mIndicatorMargin);
            child.setLayoutParams(params);

            this.addView(child);
        }

    }

    public int getCount() {
        return mCount;
    }

    public void setCount(int count) {
        initIndicators(count);
        this.mCount = count;
    }

    public void setSelected(int position) {
        for(int i = 0; i < getChildCount(); ++i) {
            getChildAt(i).setSelected(false);
        }

        getChildAt(position).setSelected(true);
    }

}
