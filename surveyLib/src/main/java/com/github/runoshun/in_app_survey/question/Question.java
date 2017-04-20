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

package com.github.runoshun.in_app_survey.question;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

/**
 * Base class for survey question
 */
abstract public class Question implements Parcelable {

    public final @NonNull String id;
    public final @NonNull String question;
    public final boolean isRequired;

    private boolean isAnswered;
    private @Nullable OnAnswerSavedListener answerSavedListener;

    public interface OnAnswerSavedListener {
        void onAnswerSaved(boolean answered);
    }

    public Question(@NonNull String title, @NonNull String question, boolean isRequired) {
        this.id = title;
        this.question = question;
        this.isRequired = isRequired;
        this.isAnswered = false;
    }

    public boolean isAnswered() {
        return isAnswered;
    }

    protected void setAnswered(boolean answered) {
        isAnswered = answered;
        if(answerSavedListener != null) {
            answerSavedListener.onAnswerSaved(answered);
        }
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(question);
        dest.writeInt(isRequired ? 1 : 0);
        dest.writeInt(isAnswered ? 1 : 0);
    }

    protected Question(@NonNull Parcel src) {
        this.id = src.readString();
        this.question = src.readString();
        this.isRequired = (src.readInt() == 1);
        this.isAnswered = (src.readInt() == 1);
    }

    public void setAnswerSavedListener(@Nullable OnAnswerSavedListener answerSavedListener) {
        this.answerSavedListener = answerSavedListener;
    }

    @Nullable
    public OnAnswerSavedListener getAnswerSavedListener() {
        return this.answerSavedListener;
    }

}
