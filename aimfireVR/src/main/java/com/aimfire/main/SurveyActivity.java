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

package com.aimfire.main;

import com.aimfire.camarada.BuildConfig;
import com.aimfire.camarada.R;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.LayoutRes;
import android.support.annotation.StyleRes;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import com.github.runoshun.in_app_survey.Survey;
import com.github.runoshun.in_app_survey.reporter.FirebaseAnalyticsSurveyReporter;
import com.github.runoshun.in_app_survey.ui.SurveyFragment;
import com.google.firebase.analytics.FirebaseAnalytics;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class SurveyActivity extends AppCompatActivity implements SurveyFragment.InteractionListener {

    private static final String TAG = "SurveyActivity";
    private static final String ARG_THEME = "theme";
    private static final String ARG_LAYOUT = "layout";

    /*
     * firebase analytics
     */
    private FirebaseAnalytics mFirebaseAnalytics;

    public static Intent createIntent(Context context) {
        return new Intent(context, SurveyActivity.class);
    }

    public static Intent createCustomThemeIntent(Context context, @StyleRes int theme) {
        Intent intent = new Intent(context, SurveyActivity.class);
        intent.putExtra(ARG_THEME, theme);
        return intent;
    }

    public static Intent createCustomLayoutIntent(Context context, @LayoutRes int layout) {
        Intent intent = new Intent(context, SurveyActivity.class);
        intent.putExtra(ARG_LAYOUT, layout);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if(BuildConfig.DEBUG) Log.v(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        int layout = getIntent().getIntExtra(ARG_LAYOUT, R.layout.fragment_survey);
        int theme = getIntent().getIntExtra(ARG_THEME, R.style.AppTheme);

        this.setTheme(theme);
        setContentView(R.layout.activity_survey);

        notifySurvey();

        /*
         * Obtain the FirebaseAnalytics instance.
         */
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

        Survey survey = makeSurvey();

        if (survey == null) {
            Toast.makeText(this, "can't create survey, see logcat.", Toast.LENGTH_SHORT).show();
        } else if (savedInstanceState == null) {
            SurveyFragment fragment = SurveyFragment.newInstance(survey, layout);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, fragment)
                    .commit();
        }
    }

    @Override
    protected void onDestroy() {
        if(BuildConfig.DEBUG)  Log.v(TAG, "onDestroy");
        super.onDestroy();
    }

    private void notifySurvey()
    {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this, R.style.AppCompatAlertDialogStyle);
        alertDialogBuilder.setTitle(R.string.information);
        alertDialogBuilder.setMessage(R.string.info_survey_prompt);

        alertDialogBuilder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which)
            {
                // do nothing
            }
        });

        alertDialogBuilder.setNeutralButton(R.string.later, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which)
            {
                finish();
            }
        });

        alertDialogBuilder.setNegativeButton(R.string.dontAskAgain, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which)
            {
                updateShowSurveyPref();
                finish();
            }
        });

        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }

    private void updateShowSurveyPref()
    {
        SharedPreferences settings =
                getSharedPreferences(getString(R.string.settings_file), Context.MODE_PRIVATE);

        /*
         * here settings != null doesn't mean the file necessarily exist!
         */
        if (settings != null)
        {
   	        /*
   	         * disable hint for subsequent launches
   	         */
            SharedPreferences.Editor editor = settings.edit();
            editor.putBoolean(MainConsts.SHOW_SURVEY_PREFS_KEY, false);
            editor.commit();
        }
    }

    private Survey makeSurvey() {
        try {
            InputStream is = this.getAssets().open("survey.json");
            BufferedReader br = new BufferedReader(new InputStreamReader(is));

            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }

            return Survey.fromJson(new JSONObject(sb.toString()));

        } catch (IOException e) {
            if(BuildConfig.DEBUG)  Log.e(TAG, "read json error.", e);
            return null;
        } catch (JSONException e) {
            if(BuildConfig.DEBUG)  Log.e(TAG, "read json error.", e);
            return null;
        }
    }

    @Override
    public void onLastQuestionFinished(Survey survey) {
        Toast.makeText(this, R.string.finish_toast, Toast.LENGTH_SHORT).show();

        FirebaseAnalyticsSurveyReporter reporter = new FirebaseAnalyticsSurveyReporter(survey);

        Bundle params = new Bundle();

        if(BuildConfig.DEBUG)  Log.v(TAG, "== SurveyResult ==");
        for (FirebaseAnalyticsSurveyReporter.FAEvent e : reporter.reportResult()) {

            /*
             * FAEvent.action has the following form:
             * 1) free write. action: [survey name]_[qustion id], label: text input
             * 2) single choice. action: [survey name]_[qustion id]_[selection num]; label: null
             * 2) multiple choice. multiple entries, each entry same as single choice; label: null
             */
            if(e != null) {
                if (BuildConfig.DEBUG)
                    Log.v(TAG, "action = " + e.action + ", label = " + e.label + ", value = " + e.value);
                params.putString(e.action, e.label);
            }
        }

        mFirebaseAnalytics.logEvent(MainConsts.FIREBASE_CUSTOM_EVENT_SURVEY_RESULT, params);
        updateShowSurveyPref();

        finish();
    }
}
