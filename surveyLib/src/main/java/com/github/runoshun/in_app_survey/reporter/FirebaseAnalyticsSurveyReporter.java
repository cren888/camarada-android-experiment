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

package com.github.runoshun.in_app_survey.reporter;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.github.runoshun.in_app_survey.Survey;
import com.github.runoshun.in_app_survey.question.FreeWritingQuestion;
import com.github.runoshun.in_app_survey.question.MultiChoiceQuestion;
import com.github.runoshun.in_app_survey.question.SingleChoiceQuestion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;

public class FirebaseAnalyticsSurveyReporter extends SurveyReporter<FirebaseAnalyticsSurveyReporter.FAEvent> {

    public FirebaseAnalyticsSurveyReporter(Survey survey) {
        super(survey);
    }

    @Override
    protected Collection<FAEvent> reportFreeWriteResult(FreeWritingQuestion q) {
        return single(new FAEvent(
                String.format(Locale.US, "%s_%s", getSurveyName(), q.id),
                q.getAnswer(),
                null));
    }

    @Override
    protected Collection<FAEvent> reportSingleChoiceResult(SingleChoiceQuestion q) {
        return single(new FAEvent(
                String.format(Locale.US, "%s_%s", getSurveyName(), q.id),
                Integer.toString(q.getAnswer()),
                null));
    }

    @Override
    protected Collection<FAEvent> reportMultiChoiceResult(MultiChoiceQuestion q) {
        if(q.getAnswers() != null) {
            StringBuilder builder = new StringBuilder();
            for (int i : q.getAnswers()) {
                builder.append(i);
            }
            return single(new FAEvent(
                    String.format(Locale.US, "%s_%s", getSurveyName(), q.id),
                    builder.toString(),
                    null));
        }
        return null;
    }

    public static class FAEvent {
        public final @NonNull String action;
        public final @Nullable String label;
        public final @Nullable String value;

        public FAEvent(@NonNull String action, @Nullable String label, @Nullable String value) {
            this.action = action;
            this.label = label;
            this.value = value;
        }
    }
}
