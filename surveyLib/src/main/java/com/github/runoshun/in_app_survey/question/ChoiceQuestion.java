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
import android.support.annotation.NonNull;

abstract public class ChoiceQuestion extends Question {

    public final @NonNull String[] choices;

    public ChoiceQuestion(@NonNull String id, @NonNull String question, boolean isRequired, @NonNull String[] choices) {
        super(id, question, isRequired);
        this.choices = choices;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeStringArray(choices);
    }

    protected ChoiceQuestion(Parcel parcel) {
        super(parcel);
        this.choices = parcel.createStringArray();
    }
}
