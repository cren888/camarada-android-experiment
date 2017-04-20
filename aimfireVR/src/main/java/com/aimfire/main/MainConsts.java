package com.aimfire.main;

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

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import android.os.Environment;

public class MainConsts
{
    public static final String SETTINGS_FILE = "STUB";

    public static final String DEMO_MODE_PREFS_KEY = "a";
    public static final String SHOW_INTRO_PREFS_KEY = "b";
    public static final String LAUNCH_COUNT_PREFS_KEY = "c";
    public static final String SHOW_HINT_2D_PREFS_KEY = "d";
    public static final String SHOW_HINT_3D_PREFS_KEY = "e";
    public static final String SHOW_SURVEY_PREFS_KEY = "f";
    public static final String DISPLAY_SWAP_PREFS_KEY = "g";
    public static final String DISPLAY_COLOR_PREFS_KEY = "h";
    public static final String DISPLAY_MODE_PREFS_KEY = "i";
	public static final String DRIVE_ACCOUNT_NAME = "j";
	public static final String DRIVE_PERSON_NAME = "k";
	public static final String DRIVE_PERSON_PHOTO_URL = "l";
	public static final String LATEST_VERSION_CODE_KEY = "m";
    public static final String DONT_SHOW_INVITE_PREFS_KEY = "n";
    public static final String DUAL_MODE_ATTEMPTED_PREFS_KEY = "o";
    public static final String CAMERA_POSITION_PREFS_KEY = "p";
    public static final String ZOOM_LEVEL_PREFS_KEY = "q";
    public static final String PARALLAX_ADJUSTMENT_PREFS_KEY = "r";
    public static final String VIDEO_RESOLUTION_PREFS_KEY = "s";

    public static final String CAMERA_POSITION_AUTO = "0";
    public static final String CAMERA_POSITION_LEFT = "1";
    public static final String CAMERA_POSITION_RIGHT = "2";

    public static final String CCONTENT_STORAGE_LEFT = "0";
    public static final String CCONTENT_STORAGE_RIGHT = "1";
    public static final String CCONTENT_STORAGE_BOTH = "2";

    public static final String VIDEO_RESOLUTION_1080P = "0";
    public static final String VIDEO_RESOLUTION_720P = "1";
    public static final String VIDEO_RESOLUTION_480P = "2";

    public static final String ZOOM_LEVEL_2_0X = "0";
    public static final String ZOOM_LEVEL_1_5x = "1";
    public static final String ZOOM_LEVEL_1_0x = "2";

    public static final String PARALLAX_ADJUSTMENT_AUTO = "0";
    public static final String PARALLAX_ADJUSTMENT_POSITIVE = "1";
    public static final String PARALLAX_ADJUSTMENT_NEGATIVE = "2";

    public static final int PROMPT_SURVEY_LAUNCH_COUNT = 2;

    /*
     * on-device directory structure:
     * 
     * ROOT is where processed files will reside (full-width SBS jpg/mpg)
     * RAW is a sub-directory under ROOT for storing unprocessed files (left/right)
     * EXPORT is a sub-directory under ROOT for storing exported files (anaglyphs, 
     * half-width SBS jpg/mpg)
     */
    public static final String MEDIA_3D_ROOT = "Camarada";
    public static final String MEDIA_3D_EXPORT = "export";
    
    /*TODO: add a leading dot to the directory names upon release*/
    public static final String MEDIA_3D_RAW = "cache";
    public static final String MEDIA_3D_SAVE = "save";
    public static final String MEDIA_3D_THUMB = "thumb";
    public static final String MEDIA_3D_SHARED = "shared";
    public static final String MEDIA_3D_DEBUG = "debug";

    /*
     * This location works best if we want the created images to be shared
     * between applications and persist after our app has been uninstalled.
     */
    public static File MEDIA_3D_ROOT_DIR = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), MEDIA_3D_ROOT);
    public static File MEDIA_3D_RAW_DIR = new File(MEDIA_3D_ROOT_DIR, MEDIA_3D_RAW);
    public static File MEDIA_3D_EXPORT_DIR = new File(MEDIA_3D_ROOT_DIR, MEDIA_3D_EXPORT);
    public static File MEDIA_3D_SAVE_DIR = new File(MEDIA_3D_ROOT_DIR, MEDIA_3D_SAVE);
    public static File MEDIA_3D_THUMB_DIR = new File(MEDIA_3D_ROOT_DIR, MEDIA_3D_THUMB);
    public static File MEDIA_3D_SHARED_DIR = new File(MEDIA_3D_ROOT_DIR, MEDIA_3D_SHARED);
    public static File MEDIA_3D_DEBUG_DIR = new File(MEDIA_3D_ROOT_DIR, MEDIA_3D_DEBUG);

    @SuppressWarnings("serial")
	public static final Map<File, Boolean> MEDIA_3D_DIRS = new HashMap<File, Boolean>() {{
            put(MEDIA_3D_ROOT_DIR, false);
            put(MEDIA_3D_EXPORT_DIR, false);
            put(MEDIA_3D_RAW_DIR, true);
            put(MEDIA_3D_SAVE_DIR, true);
            put(MEDIA_3D_THUMB_DIR, true);
            put(MEDIA_3D_SHARED_DIR, true);
            put(MEDIA_3D_DEBUG_DIR, true);
    }};

    public static String MEDIA_3D_ROOT_PATH = (MEDIA_3D_ROOT_DIR.getPath() + File.separator);
    public static String MEDIA_3D_RAW_PATH = (MEDIA_3D_RAW_DIR.getPath() + File.separator);
    public static String MEDIA_3D_EXPORT_PATH = (MEDIA_3D_EXPORT_DIR.getPath() + File.separator);
    public static String MEDIA_3D_SAVE_PATH = (MEDIA_3D_SAVE_DIR.getPath() + File.separator);
    public static String MEDIA_3D_THUMB_PATH = (MEDIA_3D_THUMB_DIR.getPath() + File.separator);
    public static String MEDIA_3D_SHARED_PATH = (MEDIA_3D_SHARED_DIR.getPath() + File.separator);
    public static String MEDIA_3D_DEBUG_PATH = (MEDIA_3D_DEBUG_DIR.getPath() + File.separator);

    /*
     * google drive directory structure:
     */ 
    public static final String DRIVE_3D_ROOT = "Camarada";
    public static final String DRIVE_3D_SHARED = "shared";
    
    public static final int THUMBNAIL_SIZE = 256;

    // P2P command for audio sync
	public static final int CMD_AUDIO_SYNC = 0;

    // P2P commands to start/end demo activities
	public static final int CMD_DEMO_START = 1;
	public static final int CMD_DEMO_END = 2;
	public static final int CMD_DEMO_STOP = 3;
	public static final int CMD_DEMO_RESTART = 4;

	// P2P commands passed between demo activities
	public static final int CMD_DEMO_CAMERA_ACTION_START = 11;
	public static final int CMD_DEMO_CAMERA_ACTION_END = 12;
	public static final int CMD_DEMO_CAMERA_ACTION_SWITCH_PHOTO_VIDEO = 13;
	public static final int CMD_DEMO_CAMERA_ACTION_SWITCH_FRONT_BACK = 14;
	public static final int CMD_DEMO_CAMERA_REPORT_CAP_TIME = 15;
	public static final int CMD_DEMO_CAMERA_REPORT_PARAMETERS = 16;

    // P2P command for network sync result
    public static final int CMD_NETWORK_SYNC = 30;

    /*****************BEGIN*******************/
    /**           intent actions            **/
    /*****************************************/
	public static final String AIMFIRE_SERVICE_MESSAGE = "a"; //"aimfire-service-message";
	public static final String PHOTO_PROCESSOR_MESSAGE = "c"; //"photo-processor-message";
	public static final String MOVIE_PROCESSOR_MESSAGE = "d"; //"movie-processor-message";
	public static final String DRIVE_EVENT_SERVICE_MESSAGE = "e"; //"drive-event-message";
	public static final String FILE_UPLOADER_MESSAGE = "f"; //"file-uploader-message";
	public static final String FILE_DOWNLOADER_MESSAGE = "g"; //"file-downloader-message";
    public static final String MOVIE_ENCODER_MESSAGE = "h"; //"movie-encoder-message";


    /*****************BEGIN*******************/
    /**       intent extra - keys           **/
    /*****************************************/
	public static final String EXTRA_CMD = "a";
	public static final String EXTRA_NAME = "b";
	public static final String EXTRA_NAME_ACCOUNT = "b1";
    public static final String EXTRA_NAME2 = "b2";
	public static final String EXTRA_FILENAME = "c";
	public static final String EXTRA_WHAT = "d";
	public static final String EXTRA_PATH = "e0";
	public static final String EXTRA_COMFY = "e1";
	public static final String EXTRA_COLOR = "e2";
	public static final String EXTRA_SIZE = "f1";
    public static final String EXTRA_RESULT = "m";
    public static final String EXTRA_INITIATOR = "n";
    public static final String EXTRA_ISLEFT = "n1";
    public static final String EXTRA_OFFSET = "r";
    public static final String EXTRA_FACING = "s";
    public static final String EXTRA_SCALE = "t";
    public static final String EXTRA_STATUS = "u";
    public static final String EXTRA_MSG = "v";
    public static final String EXTRA_SYNCTIME = "w0";
    public static final String EXTRA_ID = "z";
    public static final String EXTRA_ID_RESOURCE = "z1";
    public static final String EXTRA_ID_DRIVE_FILE = "z2";
    public static final String EXTRA_ID_DRIVE_DIR = "z3";
    public static final String EXTRA_TAG_CNT = "z5";
    public static final String EXTRA_TAG = "z6";
    public static final String EXTRA_PATH_PREVIEW = "z7";
    public static final String EXTRA_INDEX = "aa";

    /*****************BEGIN*******************/
    /**         intent extra - values       **/
    /**        AIMFIRE_SERVICE_MESSAGE      **/
    /*****************************************/
	public static final int MSG_AIMFIRE_SERVICE_NULL = 0;
	public static final int MSG_AIMFIRE_SERVICE_STATUS = 1;
	public static final int MSG_AIMFIRE_SERVICE_ERROR = 2;

	// AIMFIRE_SERVICE_MESSAGE intent extra values related to P2P status
	public static final int MSG_AIMFIRE_SERVICE_P2P_CONNECTED = 11;
	public static final int MSG_AIMFIRE_SERVICE_P2P_DISCONNECTED = 12;
	public static final int MSG_AIMFIRE_SERVICE_P2P_CONNECTING = 13;
	public static final int MSG_AIMFIRE_SERVICE_P2P_FILE_RECEIVED = 14;
	public static final int MSG_AIMFIRE_SERVICE_P2P_COMMAND_RECEIVED = 15;
	public static final int MSG_AIMFIRE_SERVICE_P2P_FAILURE = 17;

	// AIMFIRE_SERVICE_MESSAGE intent extra values related to audio subsystem 
	public static final int MSG_AIMFIRE_SERVICE_AUDIO_EVENT_ERROR = 21;
	public static final int MSG_AIMFIRE_SERVICE_AUDIO_EVENT_SYNC_MEAS = 22;
	public static final int MSG_AIMFIRE_SERVICE_AUDIO_EVENT_SYNC_START = 23;
	public static final int MSG_AIMFIRE_SERVICE_AUDIO_EVENT_SYNC_END = 24;

	// AIMFIRE_SERVICE_MESSAGE intent extras, signaling cloud service ready
	public static final int MSG_AIMFIRE_SERVICE_CLOUD_SERVICE_READY = 31;
    /******************END********************/
    /**         intent extra values         **/
    /**       AIMFIRE_SERVICE_MESSAGE       **/
    /*****************************************/

    /*****************BEGIN*******************/
    /**          intent extra values        **/
    /**        PHOTO_PROCESSOR_MESSAGE      **/
    /*****************************************/
	public static final int MSG_PHOTO_PROCESSOR_NULL = 0;
	public static final int MSG_PHOTO_PROCESSOR_RESULT = 1;
	public static final int MSG_PHOTO_PROCESSOR_ERROR = 2;
    /******************END********************/
    /**          intent extra values        **/
    /**        PHOTO_PROCESSOR_MESSAGE      **/
    /*****************************************/
	
    /*****************BEGIN*******************/
    /**          intent extra values        **/
    /**        MOVIE_PROCESSOR_MESSAGE      **/
    /*****************************************/
	public static final int MSG_MOVIE_PROCESSOR_NULL = 0;
	public static final int MSG_MOVIE_PROCESSOR_RESULT = 1;
	public static final int MSG_MOVIE_PROCESSOR_ERROR = 2;
    /******************END********************/
    /**          intent extra values        **/
    /**        MOVIE_PROCESSOR_MESSAGE      **/
    /*****************************************/

    /*****************BEGIN*******************/
    /**          intent extra values        **/
    /**         MOVIE_ENCODER_MESSAGE       **/
    /*****************************************/
    public static final int MSG_MOVIE_ENCODER_COMPLETE = 0;
    public static final int MSG_MOVIE_ENCODER_ERROR = 1;
    /******************END********************/
    /**          intent extra values        **/
    /**         MOVIE_ENCODER_MESSAGE       **/
    /*****************************************/

    /*****************BEGIN*******************/
    /**          intent extra values        **/
    /**        FILE_DOWNLOADER_MESSAGE      **/
    /*****************************************/
	public static final int MSG_FILE_DOWNLOADER_NULL = 0;
	public static final int MSG_FILE_DOWNLOADER_COMPLETION = 1;
	public static final int MSG_FILE_DOWNLOADER_PROGRESS = 2;
	public static final int MSG_FILE_DOWNLOADER_SAMPLES_START = 3;
	public static final int MSG_FILE_DOWNLOADER_FEATURED_START = 4;
    /******************END********************/
    /**          intent extra values        **/
    /**        FILE_DOWNLOADER_MESSAGE      **/
    /*****************************************/

    /*****************BEGIN*******************/
    /**         p2p handler messages        **/
    /*****************************************/
	public static final int MSG_REPORT_SEND_PEER_INFO_RESULT = 100;
	public static final int MSG_REPORT_SEND_COMMAND_RESULT = 101;
	public static final int MSG_REPORT_SEND_STRING_RESULT = 103;
	public static final int MSG_REPORT_SEND_PEER_LIST_RESULT = 104;
	public static final int MSG_REPORT_SEND_FILE_RESULT = 106;
    /******************END********************/
    /**         p2p handler messages        **/
    /*****************************************/

    /*
     * desired recording width and height. all cameras should be natively 
     * landscape, meaning width > height. this may or may not be the same
     * as final mp4 output, which is determined by how the video is shot
     * (in portrait or landscape). There are mainly four video formats,
     * see android CDD document at "http://static.googleusercontent.com/
     * media/source.android.com/en//compatibility/android-cdd.pdf". out
     * of which we should support SD (HQ), 720p and 1080p.
     * 
     * TODO: make this configurable, and synchronized across two cameras
     */
    public static final int VIDEO_BITRATE_SD_HQ = 3000000;
    public static final int VIDEO_BITRATE_720P = 8000000;
    public static final int VIDEO_BITRATE_1080P = 18000000;

    public static final int VIDEO_QUALITY_SD_HQ = 0; //720 x 480
    public static final int VIDEO_QUALITY_720P = 1; //1280 x 720 
    public static final int VIDEO_QUALITY_1080P = 2; //1920 x 1080

    /*
     * limit recording to the value below (or less if user stops it manually).
     * set this value to -1 to allow indefinite recording
     */
    public static final int VIDEO_LENGTH_SECONDS = 10;
    
	public static final int[][] VIDEO_DIMENSIONS = {
			{720, 480, VIDEO_BITRATE_SD_HQ},     //SD High Quality
			{1280, 720, VIDEO_BITRATE_720P},     //720P
			{1920, 1080, VIDEO_BITRATE_1080P}};  //1080p
	

	/*
	 * file extensions
	 */
    public static final String PHOTO_EXTENSION = "jpg";
    public static final String MOVIE_EXTENSION_3D = "cvr";
    public static final String MOVIE_EXTENSION_2D = "mp4";
    public static final String PREVIEW_EXTENSION = "jpeg";
    public static final String LINK_EXTENSION = "lnk";

    /*
     * audio test result
     */
    public static final int AUDIO_TEST_RESULT_OK = 0;

    /*
     * firebase logging events/params
     */
    public static final String FIREBASE_CUSTOM_EVENT_ACTION_CAMERA = "ev_action_camera";
    public static final String FIREBASE_CUSTOM_EVENT_ACTION_HELP = "ev_action_help";
    public static final String FIREBASE_CUSTOM_EVENT_ACTION_TUTORIAL = "ev_action_tutorial";
    public static final String FIREBASE_CUSTOM_EVENT_ACTION_FEEDBACK = "ev_action_feedback";
    public static final String FIREBASE_CUSTOM_EVENT_ACTION_SWITCH_ACCOUNT = "ev_action_switch";
    public static final String FIREBASE_CUSTOM_EVENT_SYNC_START = "ev_sync_start";
    public static final String FIREBASE_CUSTOM_EVENT_SYNC_END = "ev_sync_complete";
    public static final String FIREBASE_CUSTOM_EVENT_SYNC_ERROR = "ev_sync_error";
    public static final String FIREBASE_CUSTOM_EVENT_SYNC_MEAS_ERROR = "ev_sync_meas_error";
    public static final String FIREBASE_CUSTOM_EVENT_SYNC_CONNECTING = "ev_sync_connecting";
    public static final String FIREBASE_CUSTOM_EVENT_SHARE_START = "ev_share_start";
    public static final String FIREBASE_CUSTOM_EVENT_SHARE_COMPLETE = "ev_share_complete";
    public static final String FIREBASE_CUSTOM_EVENT_SOLO_MOVIE_CAPTURE_START = "ev_solo_m_capture_start";
    public static final String FIREBASE_CUSTOM_EVENT_SOLO_MOVIE_CAPTURE_STOP = "ev_solo_m_capture_stop";
    public static final String FIREBASE_CUSTOM_EVENT_SYNC_MOVIE_CAPTURE_START = "ev_sync_m_capture_start";
    public static final String FIREBASE_CUSTOM_EVENT_SYNC_MOVIE_CAPTURE_STOP = "ev_sync_m_capture_stop";
    public static final String FIREBASE_CUSTOM_EVENT_SYNC_MOVIE_CAPTURE_ENCODED = "ev_sync_m_capture_encoded";
    public static final String FIREBASE_CUSTOM_EVENT_SYNC_MOVIE_CAPTURE_FILE_RECEIVED = "ev_sync_m_capture_file_received";
    public static final String FIREBASE_CUSTOM_EVENT_SYNC_MOVIE_CAPTURE_COMPLETE = "ev_sync_m_capture_complete";
    public static final String FIREBASE_CUSTOM_EVENT_SYNC_MOVIE_CAPTURE_ERROR = "ev_sync_m_capture_error";
    public static final String FIREBASE_CUSTOM_EVENT_SYNC_PHOTO_CAPTURE_START = "ev_sync_p_capture_start";
    public static final String FIREBASE_CUSTOM_EVENT_SYNC_PHOTO_CAPTURE_COMPLETE = "ev_sync_p_capture_complete";
    public static final String FIREBASE_CUSTOM_EVENT_SYNC_PHOTO_CAPTURE_ERROR = "ev_sync_p_capture_error";
    public static final String FIREBASE_CUSTOM_EVENT_INVITE = "ev_invite";
    public static final String FIREBASE_CUSTOM_EVENT_DOWNLOAD_FILE_STARTED = "ev_download_file_started";
    public static final String FIREBASE_CUSTOM_EVENT_DOWNLOAD_FILE_COMPLETE = "ev_download_file_complete";
    public static final String FIREBASE_CUSTOM_EVENT_DOWNLOAD_FILE_FAILURE = "ev_download_file_failure";
    public static final String FIREBASE_CUSTOM_EVENT_VIEW_MOVIE = "ev_cardboard_view_movie";
    public static final String FIREBASE_CUSTOM_EVENT_VIEW_PHOTO = "ev_cardboard_view_photo";
    public static final String FIREBASE_CUSTOM_EVENT_SHOW_SURVEY = "ev_survey";
    public static final String FIREBASE_CUSTOM_EVENT_SURVEY_RESULT = "ev_survey_result";
    public static final String FIREBASE_CUSTOM_EVENT_AUDIO_TEST_ERROR = "ev_audio_test_error";
    public static final String FIREBASE_CUSTOM_EVENT_P2P_ERROR = "ev_p2p_error";
    public static final String FIREBASE_CUSTOM_EVENT_HEADSET_CONNECTED = "ev_headset_connected";

    public static final String FIREBASE_CUSTOM_PARAM_SHARE_TYPE = "p_share_type";
}
