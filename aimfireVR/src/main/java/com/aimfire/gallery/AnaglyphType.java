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
/*
 * make sure this matches the enum defined in af_3d.h
 */
public enum AnaglyphType 
{
   RedGreen(0),
   RedCyan(1),
   RedBlue(2),
   GreenMagenta(3), //TrioScopics3D
   AmberBlue(4), //ColorCode3D, a.k.a Brown/blue, Yellow/Blue, Ochre/Blue
   TriOviz(5); //complex magenta left lens and complex green right lens

		
    private final int value;
    private AnaglyphType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}