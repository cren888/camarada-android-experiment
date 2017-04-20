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

import android.content.Context;

import com.github.runoshun.in_app_survey.Survey;
import com.github.runoshun.in_app_survey.question.FreeWritingQuestion;
import com.github.runoshun.in_app_survey.question.MultiChoiceQuestion;
import com.github.runoshun.in_app_survey.question.Question;
import com.github.runoshun.in_app_survey.question.SingleChoiceQuestion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Base class of survey result reporter
 */
abstract public class SurveyReporter<T> {

    private static final String TAG = "SurveyReporter";

    private Survey survey;

    public SurveyReporter(Survey survey) {
        this.survey = survey;
    }

    protected String getSurveyName() {
        return survey.name;
    }

    public List<T> reportResult() {
        List<T> results = new ArrayList<T>();

        for(Question question : survey.questions) {
            if (question instanceof FreeWritingQuestion) {
                results.addAll(reportFreeWriteResult((FreeWritingQuestion)question));
            }
            else if (question instanceof SingleChoiceQuestion) {
                results.addAll(reportSingleChoiceResult((SingleChoiceQuestion) question));
            }
            else if (question instanceof MultiChoiceQuestion) {
                results.addAll(reportMultiChoiceResult((MultiChoiceQuestion) question));
            }
        }

        return results;
    }

    protected Collection<T> single(T result) {
        Collection<T> coll = new ArrayList<>(1);
        coll.add(result);
        return coll;
    }

    abstract protected Collection<T> reportFreeWriteResult(FreeWritingQuestion q);
    abstract protected Collection<T> reportSingleChoiceResult(SingleChoiceQuestion q);
    abstract protected Collection<T> reportMultiChoiceResult(MultiChoiceQuestion q);
}
