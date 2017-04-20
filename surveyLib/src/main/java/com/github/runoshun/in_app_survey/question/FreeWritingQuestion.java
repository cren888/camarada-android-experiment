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

public class FreeWritingQuestion extends Question {

    private static final String TAG = "FreeWritingQuestion";

    private String answer = null;

    public FreeWritingQuestion(@NonNull String title, @NonNull String question, boolean isRequired) {
        super(title, question, isRequired);
    }

    public void saveAnswer(@NonNull String answer) {
        //noinspection ConstantConditions
        if(answer != null && answer.length() != 0) {
            setAnswered(true);
            this.answer = answer;
        }
        else {
            setAnswered(false);
            this.answer = null;
        }
    }

    @Nullable
    public String getAnswer() {
        return answer;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeString(answer);
    }

    private FreeWritingQuestion(Parcel src) {
        super(src);
        this.answer = src.readString();
    }

    public static final Parcelable.Creator<FreeWritingQuestion> CREATOR =
            new Parcelable.Creator<FreeWritingQuestion>() {

                @Override
                public FreeWritingQuestion createFromParcel(Parcel source) {
                    return new FreeWritingQuestion(source);
                }

                @Override
                public FreeWritingQuestion[] newArray(int size) {
                    return new FreeWritingQuestion[size];
                }
            };

}
