package com.aimfire.plotter;

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

/**
 * Listener used by the PlotResult Fragments to interact with each other and
 * with the Main Activity. The hosting PlotResult Activity should implement
 * this listener.
 */
public interface OnResultNavListener {
    public void fromToFragment(String fromTag, String toTag);

    public void done();
}
