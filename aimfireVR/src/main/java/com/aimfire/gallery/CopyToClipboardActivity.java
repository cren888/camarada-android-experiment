package com.aimfire.gallery;

/*
 * Copyright (c) 2016 Aimfire Inc.
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
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

public class CopyToClipboardActivity extends Activity 
{
    @Override
    protected void onCreate(Bundle savedInstanceState) 
    {
	    super.onCreate(savedInstanceState);

	    Uri uri = getIntent().getData();
	    if (uri != null) 
	    {
	        copyTextToClipboard(uri.toString());
	        Toast.makeText(this, "Link copied to clipboard", Toast.LENGTH_SHORT).show();
	    }

	    // Finish right away. We don't want to actually display a UI.
	    finish();
    }

	private void copyTextToClipboard(String url) 
	{
	    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
	    ClipData clip = ClipData.newPlainText("URL", url);
	    clipboard.setPrimaryClip(clip);
    }
}