package com.aimfire.gallery.cardboard;

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
import android.content.Context;

public class HeadGestureDetector 
{
	//5 degree angle for pitch change to be recognized as a nodding gesture
    	private final static float PITCH_ANGLE_THRESH = (float)(5.0/180.0 * Math.PI);
    	private final static float YAW_ANGLE_THRESH = (float)(10.0/180.0 * Math.PI);

	private final static float NOD_INTERVAL = 1.0f;
	private final static float SHAKE_INTERVAL = 1.25f;
	private final static int frameRate = 30;

	private int pitchBufferLen = (int)(NOD_INTERVAL*(float)frameRate);
	private int yawBufferLen = (int)(SHAKE_INTERVAL*(float)frameRate);
	private float []pitchBuffer = new float[pitchBufferLen];
	private float []yawBuffer = new float[yawBufferLen];
	private int currInd = -1;

    public HeadGestureDetector(Context context) 
    {
    	    reset();
    }

    public void reset () 
    {
	    currInd = -1;

    	    for(int i=0; i<pitchBufferLen; i++)
    	    {
	        pitchBuffer[i] = 0.0f;
    	    }

    	    for(int i=0; i<yawBufferLen; i++)
    	    {
	        yawBuffer[i] = 0.0f;
    	    }
    }
    
    public HeadGestureType detect(float [] headEulerAngles) 
    {
	    int i, j;

        currInd++;
        pitchBuffer[currInd%pitchBufferLen] = headEulerAngles[0];
        yawBuffer[currInd%yawBufferLen] = headEulerAngles[1];
        
    	    if(currInd < (Math.max(pitchBufferLen, yawBufferLen)-1))
    	    {
    	        return HeadGestureType.NONE;
    	    }

	    float []pitchHistory = new float[pitchBufferLen];
	    i=j=0;
	    for(i=(currInd%pitchBufferLen + 1); i<pitchBufferLen; i++)
	    {
	    	    pitchHistory[j] = pitchBuffer[i];
	    	    j++;
	    }
	    for(i=0; i<=currInd%pitchBufferLen; i++)
	    {
	    	    pitchHistory[j] = pitchBuffer[i];
	    	    j++;
	    }

	    float []yawHistory = new float[yawBufferLen];
	    i=j=0;
	    for(i=(currInd%yawBufferLen + 1); i<yawBufferLen; i++)
	    {
	    	    yawHistory[j] = yawBuffer[i];
	    	    j++;
	    }
	    for(i=0; i<=currInd%yawBufferLen; i++)
	    {
	    	    yawHistory[j] = yawBuffer[i];
	    	    j++;
	    }

	    HeadGestureType result;
	    if((result = naiveNodDetect(pitchHistory)) != HeadGestureType.NONE)
	    {
	    	    return result;
	    }
	    else 
	    {
	    	    return naiveShakeDetect(yawHistory);
	    }
    }
    
    private HeadGestureType naiveNodDetect(float[] pitchHistory)
    {
    	    int i, j;
        // head rotation around x axis (pitch) full range of pitch eulerAngle is 
        // +/- PI/2. signature for nod would be decrease then increase of this angle
    	    float max[] = {-(float)Math.PI/2.0f, -(float)Math.PI/2.0f, -(float)Math.PI/2.0f};
    	    float min[] = {(float)Math.PI/2.0f, (float)Math.PI/2.0f, (float)Math.PI/2.0f};

    	    //obtain min and max of every third of samples
    	    for(j=0; j<3; j++)
    	    {
    	        for(i=j*pitchHistory.length/3; i<(j+1)*pitchHistory.length/3; i++)
    	        {
    	    	        if(pitchHistory[i] > max[j])
    	    	        {
    	    	            max[j] = pitchHistory[i];
    	    	        }
    	    	        if(pitchHistory[i] < min[j])
    	    	        {
    	    	            min[j] = pitchHistory[i];
    	    	        }
    	        }
    	    }

    	    if( ((max[0]-min[1]) > PITCH_ANGLE_THRESH) && ((max[2]-min[1]) > PITCH_ANGLE_THRESH) )
        {
    	    	    // clear buffer after a positive detection
    	    	    reset();
    	        return HeadGestureType.NOD_DOWN;
        }
    	    if( ((max[1]-min[0]) > PITCH_ANGLE_THRESH) && ((max[1]-min[2]) > PITCH_ANGLE_THRESH) )
    	    {
    	    	    // clear buffer after a positive detection
    	    	    reset();
    	        return HeadGestureType.NOD_UP;
    	    }
        return HeadGestureType.NONE;
    }

    private HeadGestureType naiveShakeDetect(float[] yawHistory)
    {
    	    int i, j;
        // head rotation around y axis (yaw) full range of pitch eulerAngle is 
        // +/- PI. 
    	    float max[] = {-(float)Math.PI, -(float)Math.PI, -(float)Math.PI};
    	    float min[] = {(float)Math.PI, (float)Math.PI, (float)Math.PI};

    	    //obtain min and max of every third of samples
    	    for(j=0; j<3; j++)
    	    {
    	        for(i=j*yawHistory.length/3; i<(j+1)*yawHistory.length/3; i++)
    	        {
    	    	        if(yawHistory[i] > max[j])
    	    	        {
    	    	            max[j] = yawHistory[i];
    	    	        }
    	    	        if(yawHistory[i] < min[j])
    	    	        {
    	    	            min[j] = yawHistory[i];
    	    	        }
    	        }
    	    }

    	    if( ((max[0]-min[1]) > YAW_ANGLE_THRESH) && ((max[2]-min[1]) > YAW_ANGLE_THRESH) ) 
        {
    	    	    // clear buffer after a positive detection
    	    	    reset();
    	        return HeadGestureType.SHAKE_RIGHT;
        }

    	    if( ((max[1]-min[0]) > YAW_ANGLE_THRESH) && ((max[1]-min[2]) > YAW_ANGLE_THRESH) )
        {
    	    	    // clear buffer after a positive detection
    	    	    reset();
    	        return HeadGestureType.SHAKE_LEFT;
        }
        return HeadGestureType.NONE;
    }
}
