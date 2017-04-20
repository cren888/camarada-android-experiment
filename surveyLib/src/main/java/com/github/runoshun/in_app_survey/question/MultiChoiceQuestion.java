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

public class MultiChoiceQuestion extends ChoiceQuestion {

    public final int maxChoices;
    public final int minChoices;

    private int[] answers = null;

    public MultiChoiceQuestion(@NonNull String title, @NonNull String question, boolean isRequired,
                               @NonNull String[] choices, int maxChoices, int minChoices) {
        super(title, question, isRequired, choices);
        this.maxChoices = maxChoices;
        this.minChoices = minChoices;
    }

    public void saveAnswer(int[] indexes) {
        if(indexes != null) {
            setAnswered(true);
            this.answers = indexes;
        }
        else {
            setAnswered(false);
            this.answers = null;
        }
    }

    @Nullable
    public int[] getAnswers() {
        return answers;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeInt(maxChoices);
        dest.writeInt(minChoices);
        dest.writeIntArray(answers);
    }

    protected MultiChoiceQuestion(Parcel src) {
        super(src);
        this.maxChoices = src.readInt();
        this.minChoices = src.readInt();
        this.answers = src.createIntArray();
    }

    public static final Parcelable.Creator<MultiChoiceQuestion> CREATOR =
            new Parcelable.Creator<MultiChoiceQuestion>() {

                @Override
                public MultiChoiceQuestion createFromParcel(Parcel source) {
                    return new MultiChoiceQuestion(source);
                }

                @Override
                public MultiChoiceQuestion[] newArray(int size) {
                    return new MultiChoiceQuestion[size];
                }
            };

}
