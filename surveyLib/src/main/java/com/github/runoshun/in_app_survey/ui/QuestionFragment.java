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

import android.os.Bundle;
import android.support.annotation.LayoutRes;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.github.runoshun.in_app_survey.R;
import com.github.runoshun.in_app_survey.Survey;
import com.github.runoshun.in_app_survey.question.FreeWritingQuestion;
import com.github.runoshun.in_app_survey.question.MultiChoiceQuestion;
import com.github.runoshun.in_app_survey.question.Question;
import com.github.runoshun.in_app_survey.question.SingleChoiceQuestion;

import java.util.ArrayList;
import java.util.List;

public class QuestionFragment extends Fragment {

    private static final String ARG_POSITION = "position";
    private static final String ARG_FREE_WRITING_LAYOUT = "free_writing_layout";
    private static final String ARG_SINGLE_CHOICE_LAYOUT = "single_choice_layout";
    private static final String ARG_MULTI_CHOICE_LAYOUT = "multi_choice_layout";

    public QuestionFragment() {}

    public static QuestionFragment newInstance(int position) {
        return newInstance(position,
                R.layout.fragment_default_freewriting,
                R.layout.fragment_default_choice,
                R.layout.fragment_default_choice);
    }

    public static QuestionFragment newInstance(int position,
                                               @LayoutRes int layoutFreeWrite,
                                               @LayoutRes int layoutSingleChoice,
                                               @LayoutRes int layoutMultiChoice) {
        QuestionFragment fragment = new QuestionFragment();

        Bundle args = new Bundle();
        args.putInt(ARG_POSITION, position);
        args.putInt(ARG_FREE_WRITING_LAYOUT, layoutFreeWrite);
        args.putInt(ARG_SINGLE_CHOICE_LAYOUT, layoutSingleChoice);
        args.putInt(ARG_MULTI_CHOICE_LAYOUT, layoutMultiChoice);
        fragment.setArguments(args);

        return fragment;
    }

    @SuppressWarnings("unused")
    public void setFreeWritingLayout(@LayoutRes int layout) {
        getArguments().putInt(ARG_FREE_WRITING_LAYOUT, layout);
    }

    @SuppressWarnings("unused")
    public void setSingleChoiceLayout(@LayoutRes int layout) {
        getArguments().putInt(ARG_SINGLE_CHOICE_LAYOUT, layout);
    }

    @SuppressWarnings("unused")
    public void setMultiChoiceLayout(@LayoutRes int layout) {
        getArguments().putInt(ARG_MULTI_CHOICE_LAYOUT, layout);
    }

    @Nullable
    protected Question getQuestion() {
        SurveyFragment parent = (SurveyFragment) getParentFragment();

        if(parent != null) {
            Survey survey = parent.getSurvey();
            int position = getArguments().getInt(ARG_POSITION);

            if (survey != null) {
                return survey.questions[position];
            }
        }

        return null;
    }

    @Nullable
    @Override
    final public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Question question = getQuestion();

        if(question instanceof MultiChoiceQuestion) {
            return onCreateMultiChoiceView((MultiChoiceQuestion)question, inflater, container, savedInstanceState);
        }
        else if (question instanceof SingleChoiceQuestion) {
            return onCreateSingleChoiceView((SingleChoiceQuestion)question, inflater, container, savedInstanceState);
        }
        else if (question instanceof FreeWritingQuestion) {
            return onCreateFreeWritingView((FreeWritingQuestion)question, inflater, container, savedInstanceState);
        }

        throw new IllegalStateException("Can't create view from question : " + question);
    }

    protected View onCreateFreeWritingView(final FreeWritingQuestion question, LayoutInflater inflater, ViewGroup container,
                                           @SuppressWarnings("UnusedParameters") Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_default_freewriting, container, false);
        TextView questionView = (TextView) view.findViewById(R.id.survey_question);
        EditText answerView = (EditText) view.findViewById(R.id.survey_textarea);

        questionView.setText(question.question);

        answerView.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) { }
            @Override public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) { }
            @Override public void afterTextChanged(Editable editable) {
                Log.v("FreeWriting", "editable = " + editable.toString());
                question.saveAnswer(editable.toString());
            }
        });

        return view;
    }

    protected View onCreateSingleChoiceView(final SingleChoiceQuestion question, LayoutInflater inflater, ViewGroup container,
                                            @SuppressWarnings("UnusedParameters") Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_default_choice, container, false);
        TextView questionView = (TextView) view.findViewById(R.id.survey_question);
        ListView listView = (ListView) view.findViewById(R.id.survey_listview);

        questionView.setText(question.question);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                view.getContext(),
                android.R.layout.simple_list_item_single_choice,
                question.choices
        );
        listView.setAdapter(adapter);
        listView.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
                QuestionFragment.this.onSingleChoiceItemClick((ListView) adapterView, question);
            }
        });

        return view;
    }

    private void onSingleChoiceItemClick(ListView listView, SingleChoiceQuestion question) {
        int i = listView.getCheckedItemPosition();
        if (i != ListView.INVALID_POSITION) {
            question.saveAnswer(i);
        }
    }


    protected View onCreateMultiChoiceView(final MultiChoiceQuestion question, LayoutInflater inflater, ViewGroup container,
                                           @SuppressWarnings("UnusedParameters") Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_default_choice, container, false);
        TextView questionText = (TextView) view.findViewById(R.id.survey_question);
        ListView listView = (ListView) view.findViewById(R.id.survey_listview);

        questionText.setText(question.question);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                view.getContext(),
                android.R.layout.simple_list_item_multiple_choice,
                question.choices);
        listView.setAdapter(adapter);
        listView.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
                QuestionFragment.this.onMultiChoiceItemClick(
                        question, (ListView) adapterView, position);
            }
        });

        return view;
    }

    private void onMultiChoiceItemClick(MultiChoiceQuestion question, ListView listView, int position) {
        SparseBooleanArray checkedItems = listView.getCheckedItemPositions();
        List<Integer> selectedPositions = new ArrayList<>();

        int count = listView.getAdapter().getCount();
        for(int i = 0; i < count; ++i) {
            if(checkedItems.get(i)) {
                selectedPositions.add(i);
            }
        }

        if (selectedPositions.size() > question.maxChoices) {
            listView.setItemChecked(position, false);
            selectedPositions.remove((Integer)position);
        }

        int[] selected = new int[selectedPositions.size()];
        for(int i = 0; i < selected.length; ++i) {
            selected[i] = selectedPositions.get(i);
        }

        if(selectedPositions.size() >= question.minChoices) {
            question.saveAnswer(selected);
        }
        else {
            question.saveAnswer(null);
        }
    }
}
