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

package com.github.runoshun.in_app_survey.ui;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.LayoutRes;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.github.runoshun.in_app_survey.R;
import com.github.runoshun.in_app_survey.Survey;
import com.github.runoshun.in_app_survey.question.Question;
import com.github.runoshun.in_app_survey.ui.widget.IndicatorView;
import com.github.runoshun.in_app_survey.ui.widget.Pager;

public class SurveyFragment extends Fragment {

    private static final String TAG = "SurveyFragment";

    private static final String ARG_SURVEY = "survey";
    private static final String ARG_LAYOUT = "layout";

    private IndicatorView indicator;
    private Pager pager;
    private View nextButton;
    private View prevButton;
    private Survey survey;

    private InteractionListener interactionListener;

    public interface InteractionListener {
        void onLastQuestionFinished(Survey survey);
    }

    public SurveyFragment() { }

    public static SurveyFragment newInstance(Survey survey) {
        return newInstance(survey, R.layout.fragment_survey);
    }

    public static SurveyFragment newInstance(Survey survey, @LayoutRes int layout) {
        SurveyFragment fragment = new SurveyFragment();

        Bundle args = new Bundle();
        args.putParcelable(ARG_SURVEY, survey);
        args.putInt(ARG_LAYOUT, layout);
        fragment.setArguments(args);

        return fragment;
    }

    @Nullable
    public Survey getSurvey() {
        return getArguments().getParcelable(ARG_SURVEY);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        if (context instanceof InteractionListener) {
            interactionListener = (InteractionListener)context;
        }
        else {
            throw new IllegalArgumentException(context.toString() +
                    " must be implement InteractionListener");
        }
    }
    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        survey = getArguments().getParcelable(ARG_SURVEY);
        int layoutRes = getArguments().getInt(ARG_LAYOUT);

        Log.v(TAG, "onCreateView(), survey.currentPosition = " + survey.getCurrentPosition());

        View view = inflater.inflate(layoutRes, container, false);
        indicator = (IndicatorView) view.findViewById(R.id.survey_indicator);
        pager = (Pager) view.findViewById(R.id.survey_pager);
        nextButton = view.findViewById(R.id.survey_forward_button);
        prevButton = view.findViewById(R.id.survey_backward_button);

        indicator.setCount(survey.questions.length);

        FragmentPagerAdapter adapter = createPagerAdapter(
                getChildFragmentManager(),
                survey.questions.length);
        pager.setAdapter(adapter);
        pager.setCurrentItem(survey.getCurrentPosition());

        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SurveyFragment.this.onNextButtonClicked();
            }
        });

        prevButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SurveyFragment.this.onPrevButtonClicked();
            }
        });

        survey.setQuestionChangeListener(new Survey.OnQuestionChangeListener() {
            @Override
            public void onQuestionChanged(Question question, int position) {
                SurveyFragment.this.updateControlsState(question, position);
            }
        });

        updateControlsState(survey.getCurrentQuestion(), survey.getCurrentPosition());

        return view;
    }

    protected FragmentPagerAdapter createPagerAdapter(FragmentManager childFragmentManager, int length) {
        return new QuestionFragmentPagerAdapter(childFragmentManager, length);
    }

    @SuppressWarnings("UnusedParameters")
    private void updateControlsState(Question question, int position) {
        Log.v(TAG, "updateControlsState(), survey.currentPosition = " + survey.getCurrentPosition());

        indicator.setSelected(position);
        pager.setCurrentItem(position);

        prevButton.setEnabled(survey.canGoPrev());

        if(survey.canGoNext()) {
            nextButton.setEnabled(true);
            question.setAnswerSavedListener(null);
        }
        else {
            nextButton.setEnabled(false);
            question.setAnswerSavedListener(new Question.OnAnswerSavedListener() {
                @Override
                public void onAnswerSaved(boolean answered) {
                    nextButton.setEnabled(answered);
                }
            });
        }
    }

    private void onNextButtonClicked() {
        Log.v(TAG, "onNextButtonClicked(), survey.currentPosition = " + survey.getCurrentPosition());
        if(survey.isLastQuestion()) {
            if(interactionListener != null) {
                interactionListener.onLastQuestionFinished(survey);
            }
        }
        else {
            survey.nextQuestion();
        }
    }

    private void onPrevButtonClicked() {
        Log.v(TAG, "onNextButtonClicked(), survey.currentPosition = " + survey.getCurrentPosition());
        if (!survey.isFirstQuestion()) {
            survey.prevQuestion();
        }
    }

}
