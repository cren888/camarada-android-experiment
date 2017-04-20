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
import com.aimfire.main.MainConsts;

/*
 * make sure this matches the enum defined in af_3d.h
 */
public class StereoPair 
{
    private String leftFilename="";
    private String rightFilename="";
    private String anaglyphFilename="";
    private String fullSbsFilename="";
    private String halfSbsFilename="";
    private int timeOffsetMs = 0;

    /*
     *  filename: MPG/IMG_left/right_<refCode>_m/p<offset>_<index>.mp4/jpg
     *  filename(processed): MPG/IMG_left/right_<refCode>_m/p<offset>_<index>_Y.mp4/jpg
     */
    public StereoPair(String[] f1, String[] f2)
    {
    	    String[] lf;
    	    String[] rf;

        if(f1[1].equals("left") && f2[1].equals("right")) 
        {
    	        lf = f1;
    	        rf = f2;
        }
        else 
        {
    	        lf = f2;
    	        rf = f1;
        }
        
        /*
         * reassemble file name from tokens
         */
        for(int i=0; i<lf.length-1; i++)
        {
    	        leftFilename += lf[i] + "_";
        }
	    leftFilename += lf[lf.length-1];//index.jpg/mp4 or Y.jpg/mp4

        for(int i=0; i<rf.length-1; i++)
        {
    	        rightFilename += rf[i] + "_";
        }
	    rightFilename += rf[rf.length-1];//index.jpg/mp4 or Y.jpg/mp4
    
        /*  
         * generate anaglyph and sbs file name. for the name:
         * 
         * "IMG/MPG_left/right_<refCode>_m/p<offset>_<index>.jpg/mp4"
         * 
         * corresponding anaglyph name would be:
         * 
         * "A_<refCode>_<index>.jpg/mp4"
         * 
         * corresponding sbs name would be:
         * 
         * "SBS_<refCode>_<index>.jpg/mp4"
         * 
         * i.e. we take left/right and <offset> out of the name
         */ 
	    	anaglyphFilename = "A_" + lf[2] + "_" + lf[4];
	    	fullSbsFilename = "SBS_" + lf[2] + "_" + lf[4];
	    	halfSbsFilename = "H" + fullSbsFilename;

        /*
         *  figure out the time offset
         */
        String tmp0, tmp1;
        if(lf[3].contains("m"))
        {
            tmp0 = lf[3].replace("m", "-");
        }
        else
        {
            tmp0 = lf[3].replace("p", "");
        }
        if(rf[3].contains("m"))
        {
            tmp1 = rf[3].replace("m", "-");
        }
        else
        {
            tmp1 = rf[3].replace("p", "");
        }
        
        try{
            timeOffsetMs = Integer.parseInt(tmp1) - Integer.parseInt(tmp0);
        } catch (NumberFormatException e) {
            // just in case the file names are not what we expect
            timeOffsetMs = 0;
    		    return;
        }
    }
    
    public String getLeftPath()
    {
    	    return MainConsts.MEDIA_3D_RAW_PATH+leftFilename;
    }
    	    	    
    public String getRightPath()
    {
    	    return MainConsts.MEDIA_3D_RAW_PATH+rightFilename;
    }

    public String getDebugLeftPath()
    {
    	    return MainConsts.MEDIA_3D_DEBUG_PATH+leftFilename;
    }
    	    	    
    public String getDebugRightPath()
    {
    	    return MainConsts.MEDIA_3D_DEBUG_PATH+rightFilename;
    }
    	    	    
    public String getAnaglyphPath()
    {
    	    return MainConsts.MEDIA_3D_EXPORT_PATH+anaglyphFilename;
    }

    public String getHalfSbsPath()
    {
    	    return MainConsts.MEDIA_3D_EXPORT_PATH+halfSbsFilename;
    }

    public String getFullSbsPath()
    {
    	    return MainConsts.MEDIA_3D_SAVE_PATH+fullSbsFilename;
    }

    public int getTimeOffsetMs()
    {
    	    return timeOffsetMs;
    }
}
