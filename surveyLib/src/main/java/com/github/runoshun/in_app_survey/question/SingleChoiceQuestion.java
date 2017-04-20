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

/**
 * Single choice question
 */
public class SingleChoiceQuestion extends ChoiceQuestion {

    private int selected = -1;

    public SingleChoiceQuestion(@NonNull String title, @NonNull String question,
                                boolean isRequired, @NonNull String[] choices) {
        super(title, question, isRequired, choices);
    }

    public void saveAnswer(int selected) {
        setAnswered(true);
        this.selected = selected;
    }

    public int getAnswer() {
        return selected;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeInt(selected);
    }

    protected SingleChoiceQuestion(Parcel src) {
        super(src);
        this.selected = src.readInt();
    }

    public static final Parcelable.Creator<SingleChoiceQuestion> CREATOR =
            new Parcelable.Creator<SingleChoiceQuestion>() {

                @Override
                public SingleChoiceQuestion createFromParcel(Parcel source) {
                    return new SingleChoiceQuestion(source);
                }

                @Override
                public SingleChoiceQuestion[] newArray(int size) {
                    return new SingleChoiceQuestion[size];
                }
            };
}
