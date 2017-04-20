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

package com.github.runoshun.in_app_survey;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

import com.github.runoshun.in_app_survey.question.FreeWritingQuestion;
import com.github.runoshun.in_app_survey.question.MultiChoiceQuestion;
import com.github.runoshun.in_app_survey.question.Question;
import com.github.runoshun.in_app_survey.question.SingleChoiceQuestion;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Survey implements Parcelable {

    private static final String TAG = "Survey";

    public final @NonNull String name;
    public final @NonNull Question[] questions;
    private int currentPosition = -1;
    private OnQuestionChangeListener listener = null;

    private Survey(@NonNull String name, @NonNull Question[] questions) {
        this.name = name;
        this.questions = questions;
        this.currentPosition = 0;
    }

    public boolean nextQuestion() {
        return setPosition(currentPosition + 1);
    }

    public boolean prevQuestion() {
        return setPosition(currentPosition - 1);
    }

    public boolean canGoNext() {
        //Log.v(TAG, "canGoNext(), position = " + currentPosition  +
                //", isLast = " + isLastQuestion() +
                //", isRequired = " + getCurrentQuestion().isRequired +
                //", isAnswered = " + getCurrentQuestion().isAnswered());
        //return !isLastQuestion() && (getCurrentQuestion().isAnswered() || !getCurrentQuestion().isRequired);
        return getCurrentQuestion().isAnswered() || !getCurrentQuestion().isRequired;
    }

    public boolean canGoPrev() {
        return !isFirstQuestion();
    }

    public boolean setPosition(int position) {
        Question q = getCurrentQuestion();
        int current = getCurrentPosition();
        if (current < position && !canGoNext() || current > position && !canGoPrev()) {
            return false;
        }
        else {
            this.currentPosition = position;
            if (this.listener != null) {
                listener.onQuestionChanged(getCurrentQuestion(), position);
            }
            return true;
        }
    }

    public int getCurrentPosition() {
        return this.currentPosition;
    }

    public boolean isLastQuestion() {
        return (currentPosition == questions.length - 1);
    }

    public boolean isFirstQuestion() {
        return currentPosition == 0;
    }


    public Question getCurrentQuestion() {
        return questions[this.currentPosition];
    }

    public OnQuestionChangeListener getListener() {
        return listener;
    }

    public void setQuestionChangeListener(OnQuestionChangeListener listener) {
        this.listener = listener;
    }

    public interface OnQuestionChangeListener {
        void onQuestionChanged(Question question, int position);
    }

    // ================= Parcelable implements =================

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeInt(currentPosition);
        dest.writeParcelableArray(questions, flags);
    }

    private Survey(Parcel src) {
        this.name = src.readString();
        this.currentPosition = src.readInt();
        Parcelable[] ps = src.readParcelableArray(Survey.class.getClassLoader());

        this.questions = new Question[ps.length];
        for(int i = 0; i < ps.length; ++i) {
            this.questions[i] = (Question)ps[i];
        }
    }

    public static final Parcelable.Creator<Survey> CREATOR = new Parcelable.Creator<Survey>() {
        @Override
        public Survey createFromParcel(Parcel source) {
            return new Survey(source);
        }

        @Override
        public Survey[] newArray(int size) {
            return new Survey[size];
        }
    };

    // ===================== build from json =========================

    private static final String JSON_SURVEY_NAME = "name";
    private static final String JSON_SURVEY_QUESTIONS = "questions";
    private static final String JSON_QUESTION_TYPE = "type";
    private static final String JSON_QUESTION_ID = "id";
    private static final String JSON_QUESTION_REQUIRED = "required";
    private static final String JSON_QUESTION_QUESTION = "question";
    private static final String JSON_QUESTION_MAX_CHOICE = "max";
    private static final String JSON_QUESTION_MIN_CHOICE = "min";
    private static final String JSON_QUESTION_CHOICES = "choices";

    private enum QuestionType {
        FREE_WRITE("freewrite"),
        SINGLE_CHOICE("singlechoice"),
        MULTI_CHOICE("multichoice"),
        UNKNOWN("unknown"),;

        public final @NonNull String name;

        QuestionType(@NonNull final String name) {
            this.name = name;
        }

        @NonNull
        public static QuestionType fromName(@NonNull String name) throws IllegalArgumentException {
            if (FREE_WRITE.name.equals(name)) {
                return FREE_WRITE;
            } else if (SINGLE_CHOICE.name.equals(name)) {
                return SINGLE_CHOICE;
            } else if (MULTI_CHOICE.name.equals(name)) {
                return MULTI_CHOICE;
            }

            throw new IllegalArgumentException("Unknown type : " + name);
        }
    }

    @NonNull
    public static Survey fromJson(@NonNull JSONObject json) throws JSONException {
        String surveyName = json.getString(JSON_SURVEY_NAME);
        JSONArray jsonQuestions = json.getJSONArray(JSON_SURVEY_QUESTIONS);

        Question[] questions = new Question[jsonQuestions.length()];
        for (int i = 0; i < jsonQuestions.length(); ++i) {
            questions[i] = buildQuestion(jsonQuestions.getJSONObject(i));
        }

        return new Survey(surveyName, questions);
    }

    @NonNull
    private static Question buildQuestion(@NonNull JSONObject json) throws JSONException {
        String typename = json.getString(JSON_QUESTION_TYPE);
        QuestionType type;
        try {
            type = QuestionType.fromName(typename);
        } catch (IllegalArgumentException e) {
            type = QuestionType.UNKNOWN;
        }

        switch (type) {
            case FREE_WRITE:
                return buildFreeWriteQuestion(json);
            case SINGLE_CHOICE:
                return buildSingleChoiceQuestion(json);
            case MULTI_CHOICE:
                return buildMultiChoiceQuestion(json);

            default:
                throw new JSONException("unknown question type is found : " + typename);
        }
    }

    private static Question buildFreeWriteQuestion(JSONObject json) throws JSONException {
        String id = json.getString(JSON_QUESTION_ID);
        String question = json.getString(JSON_QUESTION_QUESTION);
        boolean required = json.optBoolean(JSON_QUESTION_REQUIRED, true);

        return new FreeWritingQuestion(id, question, required);
    }

    private static Question buildSingleChoiceQuestion(JSONObject json) throws JSONException {
        String id = json.getString(JSON_QUESTION_ID);
        String question = json.getString(JSON_QUESTION_QUESTION);
        boolean required = json.optBoolean(JSON_QUESTION_REQUIRED, true);
        String[] choices = getStringArray(json, JSON_QUESTION_CHOICES);

        return new SingleChoiceQuestion(id, question, required, choices);
    }

    private static Question buildMultiChoiceQuestion(JSONObject json) throws JSONException {
        String id = json.getString(JSON_QUESTION_ID);
        String question = json.getString(JSON_QUESTION_QUESTION);
        boolean required = json.optBoolean(JSON_QUESTION_REQUIRED, true);
        String[] choices = getStringArray(json, JSON_QUESTION_CHOICES);
        int maxChoices = json.optInt(JSON_QUESTION_MAX_CHOICE, Integer.MAX_VALUE);
        int minChoices = json.optInt(JSON_QUESTION_MIN_CHOICE, 0);

        return new MultiChoiceQuestion(id, question, required, choices, maxChoices, minChoices);
    }

    @SuppressWarnings("SameParameterValue")
    private static
    @NonNull
    String[] getStringArray(JSONObject json, String field) throws JSONException {
        JSONArray array = json.getJSONArray(field);
        String[] strArray = new String[array.length()];

        for (int i = 0; i < strArray.length; ++i) {
            strArray[i] = array.getString(i);
        }

        return strArray;
    }
}
