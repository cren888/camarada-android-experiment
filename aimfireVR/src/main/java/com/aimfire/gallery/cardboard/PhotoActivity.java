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
import com.aimfire.camarada.BuildConfig;
import com.aimfire.camarada.R;
import com.aimfire.main.MainConsts;
import com.aimfire.gallery.MediaScanner;
import com.aimfire.utilities.FileUtils;

import com.google.vr.sdk.base.GvrActivity;
import com.google.vr.sdk.base.GvrView;
import com.google.vr.sdk.base.Eye;
import com.google.vr.sdk.base.HeadTransform;
import com.google.vr.sdk.base.Viewport;

//import com.google.vrtoolkit.cardboard.CardboardActivity;
//import com.google.vrtoolkit.cardboard.CardboardView;
//import com.google.vrtoolkit.cardboard.Eye;
//import com.google.vrtoolkit.cardboard.HeadTransform;
//import com.google.vrtoolkit.cardboard.Viewport;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.net.Uri;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.v4.view.MotionEventCompat;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;

import javax.microedition.khronos.egl.EGLConfig;

/**
 * a Cardboard photo viewer application. Heavily modified from Google cardboard
 * TreasureHunt example
 */
public class PhotoActivity extends GvrActivity implements GvrView.StereoRenderer
{
    private static final String TAG = "PhotoActivity";

    public static final float TOTAL_NEGATIVE_ADJUSTMENT = -0.05f;
    public static final float TOTAL_POSITIVE_ADJUSTMENT = 0.10f;

    // number of coordinates per vertex in this array
    private static final int COORDS_PER_VERTEX = 2;
    private float picCoords[] = { -1f,  1f,   // top left
            -1f, -1f,   // bottom left
            1f, -1f,   // bottom right
            1f,  1f }; //top right

    private static final float ZOOM_1X = 1.0f;
    private static final float sZoom[] = {ZOOM_1X*0.75f, ZOOM_1X, ZOOM_1X*1.5f/*, ZOOM_1X*2.0f*/};

    private Vibrator mVibrator;

    private GvrView mCardboardView;
    private CardboardOverlayView mOverlayView;
    
    private HeadGestureDetector mHgd;
    private long mLastTimeMs = 0; //last time frame was rendered

    //private float[] mPicRotation;
    //private float[] mPicFrustum;

    private int mSurfaceWidth;
    private int mSurfaceHeight;
    private float mDimRatio;

    private ShortBuffer mPicElements;
    private FloatBuffer mPicVertices;

    private int mPicProgram;
    private int mPicPositionParam;
    private int mDimRatioParam;
    private int mZoomParam;
    private int mParallaxParam;
    private int[] mTextureCurr = new int[2];
    private int[] mTexturePrev = new int[2];
    private int[] mTextureNext = new int[2];
    private Bitmap mBmpL = null;
    private Bitmap mBmpR = null;
    private int[] mBitmapSizeCurr;
    private int[] mBitmapSizePrev;
    private int[] mBitmapSizeNext;

    private ArrayList<String> mAssetList;
    private boolean mIsMyMedia;
    private int mAssetInd = -1;
    private boolean mAssetChangedLeft = false;
    private boolean mAssetChangedRight = false;
    private boolean mAssetColor;

    private int mImgZoomInd = sZoom.length-1; //default: biggest zoom
    private float mImgParallaxAdj = 0.0f; //default: auto - no adjustment

    private short drawOrder[] = { 0, 1, 2, 0, 2, 3}; //Order to draw vertices
    private final int vertexStride = COORDS_PER_VERTEX * 4; //Bytes per vertex

    private final String vertexShaderCode =
   		"uniform float u_zoom;"+
   		"uniform float u_dimRatio;" +
   		"uniform float u_parallax;" +
   		"attribute vec2 a_position;" +
        "varying vec2 v_texcoord;" +
        "void main()" +
        "{" +
        "    gl_Position = vec4(a_position, 0.0, 1.0);" +
        "    if(u_dimRatio > 1.0)" +
        "    {" +
                 //image wider than surface (for each eye)
        "        v_texcoord[0] = 0.5 + a_position[0]/u_zoom*0.5 + u_parallax;" + 
        "        v_texcoord[1] = 0.5 - a_position[1]/u_zoom*u_dimRatio*0.5;" +
        "    }" +
        "    else" +
        "    {" +
                 //image taller than surface (for each eye)
        "        v_texcoord[0] = 0.5 + a_position[0]/u_zoom/u_dimRatio*0.5 + u_parallax;" + 
        "        v_texcoord[1] = 0.5 - a_position[1]/u_zoom*0.5;" + 
        "    }" +
        "}"; 

    private final String fragmentShaderCode =
   		"precision mediump float;" +
        "uniform sampler2D u_texture;" +
        "varying vec2 v_texcoord;" +
    		"void main() {" +
    		"  gl_FragColor = texture2D(u_texture, v_texcoord);" + 
    		"}";

    /**
     * Converts a raw text file, saved as a resource, into an OpenGL ES shader.
     *
     * @param type The type of shader we will be creating.
     * @param resId The resource ID of the raw text file about to be turned into a shader.
     * @return The shader object handler.
     */
	@SuppressWarnings("unused")
	private int loadGLShader(int type, int resId) 
    {
        String code = readRawTextFile(resId);
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, code);
        GLES20.glCompileShader(shader);

        // Get the compilation status.
        final int[] compileStatus = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);

        // If the compilation failed, delete the shader.
        if (compileStatus[0] == 0) 
        {
            if(BuildConfig.DEBUG) Log.e(TAG, "Error compiling shader: " + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            shader = 0;
        }

        if (shader == 0) 
        {
            throw new RuntimeException("Error creating shader.");
        }

        return shader;
    }

    /**
     * Checks if we've had an error inside of OpenGL ES, and if so what that error is.
     *
     * @param label Label to report in case of error.
     */
    private static void checkGLError(String label) 
    {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) 
        {
            if(BuildConfig.DEBUG) Log.e(TAG, label + ": glError " + error);
            throw new RuntimeException(label + ": glError " + error);
        }
    }

    private int loadGLShader(int type, String code) 
    {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, code);
        GLES20.glCompileShader(shader);

        // Get the compilation status.
        final int[] compileStatus = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);

        // If the compilation failed, delete the shader.
        if (compileStatus[0] == 0)
        {
            if(BuildConfig.DEBUG) Log.e(TAG, "Error compiling shader: " + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            shader = 0;
        }

        if (shader == 0)
        {
            throw new RuntimeException("Error creating shader.");
        }

        return shader;
    }

    public Bitmap openAsset(Context context, final String filePath)
    {

        AssetManager assetManager = getAssets();

        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;   // No pre-scaling

        InputStream istr;
        try {
            istr = assetManager.open(filePath);
            return BitmapFactory.decodeStream(istr);
        } catch (IOException e) {
            // handle exception
        	    return null;
        }
    }

    /**
     * generate 2 textures
     * @return
     */
    public int[] genTextures()
    {
        final int[] textureHandle = new int[2];
        GLES20.glGenTextures(2, textureHandle, 0);
        checkGLError("genTextures");

   	    return textureHandle;
    }

    public void loadTexture(Bitmap bitmap, int textureHandle)
    {
        if (textureHandle != 0)
        {
            // Bind to the texture in OpenGL
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle);
            GlUtil.checkGlError("loadTexture: 1");

            // Set filtering
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GlUtil.checkGlError("loadTexture: 2");
            
            // opengl es 2.0 doesn't support clamp_to_border. so we manually added a
            // border when we process the images. and set wrap mode to clamp_to_edge
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GlUtil.checkGlError("loadTexture: 3");

            // Load the bitmap into the bound texture.
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
            GlUtil.checkGlError("loadTexture: 4");

            // Recycle the bitmap, since its data has been loaded into OpenGL.
            //if(bitmap != null)
                //bitmap.recycle();
        }
        else
        {
            throw new RuntimeException("Error loading texture.");
        }
    }

    /**
     * load full width sbs images into textures. 
     * @param index
     */
    private int[] loadSbsImage(int index)
    {
        String sbsPath = mAssetList.get(index);

        /*
         * tried BitmapRegionDecoder but it returns null on the right bitmap
         * below. seems like a bug in android, c.f. "https://code.google.com
         * /p/android/issues/detail?id=80316", that manifest when the rect
         * is on the boundary. so, abandoned
         */
//		BitmapRegionDecoder decoder = null;
//      try {
//			decoder = BitmapRegionDecoder.newInstance(imgPath, false);
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
//        
//      BitmapFactory.Options options = new BitmapFactory.Options();
//  		int width = decoder.getWidth()/2;
//  		int height = decoder.getHeight();

//      mBmpL = decoder.decodeRegion(
//      		new Rect(0, 0, width, height), options);
//      mBmpR = decoder.decodeRegion(
//      		new Rect(width, 0, width, height), options);
//      decoder.recycle();

	    int width = -1;
	    int height = -1;

        try {
            Bitmap sbsBitmap = BitmapFactory.decodeFile(sbsPath);
            if(!mAssetColor)
            {
       	        sbsBitmap = toGrayscale(sbsBitmap);
            }
  		    width = sbsBitmap.getWidth()/2;
  		    height = sbsBitmap.getHeight();
    
            mBmpL = Bitmap.createBitmap(sbsBitmap, 0, 0, width, height);
            mBmpR = Bitmap.createBitmap(sbsBitmap, width, 0, width, height);

            sbsBitmap.recycle();
        } catch (Exception e) {
            e.printStackTrace();
        }

        /*
         * left/right picture dimensions should be identical
         */
        return (new int[] {width, height});
    }

    public Bitmap toGrayscale(Bitmap bmpOriginal)
    {        
        int width, height;
        height = bmpOriginal.getHeight();
        width = bmpOriginal.getWidth();    

        Bitmap bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        Canvas c = new Canvas(bmpGrayscale);
        Paint paint = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);
        ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
        paint.setColorFilter(f);
        c.drawBitmap(bmpOriginal, 0, 0, paint);
        bmpOriginal.recycle();
        return bmpGrayscale;
    }

    private void initTextures()
    {
        /*
         * load 3 textures here, current, previous, and next. we tried loading
         * one picture at a time, in onCardboardTrigger(), when it's needed. but
         * that doesn't work well, as loading takes time, and before it finishes, 
         * onDrawEye is called and texture may not be fully loaded, resulting in
         * partially loaded texture taken by opengl. so instead, we load three
         * textures at the beginning, and update them as user advances forward or 
         * backward
         */
        mTextureCurr = genTextures();
        mTextureNext = genTextures();
        mTexturePrev = genTextures();

        int numOfAssets = mAssetList.size();
        
        /*
         * sanity check
         */
        if(numOfAssets == 0)
        {
       	    return;
        }

        /*
         * load aligned sbs images into bitmaps, then load bitmaps into opengl textures 
         */
        mBitmapSizeCurr = loadSbsImage(mAssetInd);
        
        if((mBmpL != null) && (mBmpL != null))
        {
            loadTexture(mBmpL, mTextureCurr[0]);
            loadTexture(mBmpR, mTextureCurr[1]);
        }
        

        /*
         * load the next set of textures. in case we have only one 
         * pair of images, just reuse the bitmaps from previous step
         */
        int nextInd = (mAssetInd+1)%numOfAssets;
        if(nextInd != mAssetInd)
        {
       	    // shouldn't be necessary but out of paranoia
            if(mBmpL != null)
       		{
       	        mBmpL.recycle();
       	        mBmpL = null;
       		}

            if(mBmpR != null)
       		{
       	        mBmpR.recycle();
       	        mBmpR = null;
       		}

            mBitmapSizeNext = loadSbsImage(nextInd);
        }
        else
        {
            mBitmapSizeNext = mBitmapSizeCurr;
        }
        loadTexture(mBmpL, mTextureNext[0]);
        loadTexture(mBmpR, mTextureNext[1]);

        /*
         * load the prev set of textures. in case we have only one 
         * or two pairs of images, just reuse bitmaps
         */
        int prevInd = (mAssetInd-1+numOfAssets)%numOfAssets;
        if(prevInd != nextInd)
        {
       	    // shouldn't be necessary but out of paranoia
       	    mBmpL.recycle(); mBmpL = null;
       	    mBmpR.recycle(); mBmpR = null;

            mBitmapSizePrev = loadSbsImage(prevInd);
        }
        else
        {
            mBitmapSizePrev = mBitmapSizeNext;
        }
        loadTexture(mBmpL, mTexturePrev[0]);
        loadTexture(mBmpR, mTexturePrev[1]);

   	    // shouldn't be necessary but out of paranoia
   	    mBmpL.recycle(); mBmpL = null;
   	    mBmpR.recycle(); mBmpR = null;
    }

    OnTouchListener otl = new OnTouchListener() {
        public boolean onTouch(View v, MotionEvent event) 
        {
            int action = MotionEventCompat.getActionMasked(event);
            
            switch(action) 
            {
            case (MotionEvent.ACTION_DOWN) :
                onCardboardTrigger();
                return true;
            default : 
                return false;
            }      
        }
    };

    /**
     * Sets the view to our CardboardView and initializes the transformation matrices we will use
     * to render our scene.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo);

        loadDisplayPrefs();

        /*
         * show error message in case no photo to be displayed
         */
        mOverlayView = (CardboardOverlayView) findViewById(R.id.overlay);

        Intent intent = getIntent();
        mIsMyMedia = intent.getBooleanExtra(MainConsts.EXTRA_MSG, false);
        mAssetColor = intent.getBooleanExtra(MainConsts.EXTRA_COLOR, true);
        Uri uri = intent.getData();

        if (uri != null) 
	    {
            String filePath = getFilePathFromUri(uri);

            if(filePath == null) 
            {
	            Toast.makeText(this, "Please open downloaded file from a file manager/explorer.", Toast.LENGTH_LONG).show();
	            finish();
	            return;
            }

            File f = new File(filePath);
            String fileName = f.getName();

            if(!filePath.endsWith("jpg"))
   	        {
   	            /*
   	             * something's wrong. no point in continuing
   	             */
	            Toast.makeText(this, R.string.error_no_media,
	        		    Toast.LENGTH_LONG).show();
   	            finish();
   	            return;
            }

            if(!filePath.contains(MainConsts.MEDIA_3D_ROOT_PATH))
            {
           	    /*
           	     * in case we got here directly without going thru MainActivity
           	     * first, it's possible we don't have storage initialized yet.
           	     */
                if (!FileUtils.initStorage())
                {
       		        Toast.makeText(this, R.string.error_accessing_storage, Toast.LENGTH_LONG).show();
               	    finish();
               	    return;
                }

   	            /*
   	             * we are launched from external apps by host or file type. move the
       	         * file to our "shared with me" dir. if rename fails, we will have
       	         * to do the actual copy (rather than rename, as we may be copying
       	         * the file from internal to external storage; or we don't have
       	         * write permission on the source directory)
   	             */
       	        String newFilePath = MainConsts.MEDIA_3D_SHARED_PATH + fileName;

           	    boolean success = false;
       	        try {
           	        File from = (new File(filePath));
           	        File to = (new File(newFilePath));
           	        success = from.renameTo(to);
                } catch (Exception e) {
                    e.printStackTrace();
                }
        	        
       	        if(!success)
       	        {
   	                FileUtils.copyFile(filePath, newFilePath);
       	        }

   	            filePath = newFilePath;

                /*
                 * make MediaScanner aware of the new file
                 */
                MediaScanner.addItemMediaList(filePath);
            }

   	        mAssetList = MediaScanner.getNonEmptyPhotoList(
                mIsMyMedia?MainConsts.MEDIA_3D_SAVE_DIR:MainConsts.MEDIA_3D_SHARED_DIR);

            if((mAssetList != null) && (mAssetList.size()>0))
            {
                mAssetInd = mAssetList.indexOf(filePath);
                if (mAssetInd == -1)
                {
                    if (BuildConfig.DEBUG) Log.e(TAG, "onCreate: specified photo not found, path=" + filePath);
                    mAssetInd = 0;
                }
                if (BuildConfig.DEBUG) Log.d(TAG, "onCreate: mMovieList size=" + mAssetList.size() + ", index=" + mAssetInd);
            }
            else
            {
                if (BuildConfig.DEBUG) Log.e(TAG, "onCreate: no photo found!");
                mOverlayView.show3DToast("no photo found!", 5000);
            }
        }
        else
        {
            if (BuildConfig.DEBUG) Log.d(TAG, "onCreate: no file uri specified in intent, are we " +
                    "directly envoked by daydream?");

            mAssetList = MediaScanner.getNonEmptyPhotoList(
                    mIsMyMedia?MainConsts.MEDIA_3D_SAVE_DIR:MainConsts.MEDIA_3D_SHARED_DIR);

            if((mAssetList != null) && (mAssetList.size()>0))
            {
                mAssetInd = 0;
                if (BuildConfig.DEBUG) Log.d(TAG, "onCreate: mAssetList size=" + mAssetList.size() + ", index=" + mAssetInd);
            }
            else
            {
                if (BuildConfig.DEBUG) Log.e(TAG, "onCreate: no photo found!");
                mOverlayView.show3DToast("no photo found!", 5000);
            }
        }

        /*
         * initialize cardboard related stuff
         */
        mCardboardView = (GvrView) findViewById(R.id.cardboard_view);
        mCardboardView.setRenderer(this);
        mCardboardView.setOnTouchListener(otl);

        mCardboardView.setTransitionViewEnabled(true);

        // Enable Cardboard-trigger feedback with Daydream headsets. This is a simple way of supporting
        // Daydream controller input for basic interactions using the existing Cardboard trigger API.
        mCardboardView.enableCardboardTriggerEmulation();


        setGvrView(mCardboardView);

        //debug
        //ScreenParams sp = mCardboardView.getScreenParams();
        //if(BuildConfig.DEBUG) Log.i(TAG, "ScreenParams width=" + sp.getWidth() + ", height=" + sp.getHeight());

        //mPicRotation = new float[16];
        //mPicFrustum = new float[16];
        mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        mHgd = new HeadGestureDetector(this);
    }

    @Override
    protected void onDestroy() 
    {
        if(BuildConfig.DEBUG) Log.d(TAG, "onDestroy");
        super.onDestroy();
    }

    @Override
    public void onRendererShutdown() 
    {
        if(BuildConfig.DEBUG) Log.i(TAG, "onRendererShutdown");
    }

    @Override
    public void onSurfaceChanged(int width, int height) 
    {
        if(BuildConfig.DEBUG) Log.i(TAG, "onSurfaceChanged, width=" + width + ", height=" + height);
        
        mSurfaceWidth = width;
        mSurfaceHeight = height;
    }

    @Override
    public void onSurfaceCreated(EGLConfig config) 
    {
        if(BuildConfig.DEBUG) Log.i(TAG, "onSurfaceCreated");

        /*
         *  Dark background so text shows up well.
         */
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 0.5f); 

        ByteBuffer bbElements = ByteBuffer.allocateDirect(drawOrder.length * 2);
        bbElements.order(ByteOrder.nativeOrder());
        mPicElements = bbElements.asShortBuffer();
        mPicElements.put(drawOrder);
        mPicElements.position(0);

        int vertexShader = loadGLShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode);
        int fragmentShader = loadGLShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);

        mPicProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(mPicProgram, vertexShader);
        GLES20.glAttachShader(mPicProgram, fragmentShader);
        GLES20.glLinkProgram(mPicProgram);
        GLES20.glUseProgram(mPicProgram);

        checkGLError("Pic program");

        mDimRatioParam = GLES20.glGetUniformLocation(mPicProgram, "u_dimRatio");
        mZoomParam = GLES20.glGetUniformLocation(mPicProgram, "u_zoom");
        mParallaxParam = GLES20.glGetUniformLocation(mPicProgram, "u_parallax");

        mPicPositionParam = GLES20.glGetAttribLocation(mPicProgram, "a_position");

        GLES20.glEnableVertexAttribArray(mPicPositionParam);
        checkGLError("Pic program params");

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        checkGLError("onSurfaceCreated");

        /*
         * initializes a few textures (current, previous and next). we have to do this
         * here (as opposed to onCreate) as gl context is only available here
         */
        initTextures();

        /*
         * so onDrawEye will know to draw
         */
        mAssetChangedLeft = mAssetChangedRight = true;
    }

    private void loadDisplayPrefs()
    {
        SharedPreferences settings =
                getSharedPreferences(getString(R.string.settings_file), Context.MODE_PRIVATE);

        if (settings != null)
        {
            String zLvl = settings.getString(MainConsts.ZOOM_LEVEL_PREFS_KEY, null);
            if(zLvl.equals(MainConsts.ZOOM_LEVEL_1_0x))
            {
                mImgZoomInd = 0;
            }
            else if(zLvl.equals(MainConsts.ZOOM_LEVEL_1_5x))
            {
                mImgZoomInd = 1;
            }
            else
            {
                mImgZoomInd = 2;
            }

            String adj = settings.getString(MainConsts.PARALLAX_ADJUSTMENT_PREFS_KEY, MainConsts.PARALLAX_ADJUSTMENT_AUTO);
            if(adj.equals(MainConsts.PARALLAX_ADJUSTMENT_NEGATIVE))
            {
                mImgParallaxAdj = TOTAL_NEGATIVE_ADJUSTMENT/sZoom[mImgZoomInd];
            }
            else if(adj.equals(MainConsts.PARALLAX_ADJUSTMENT_POSITIVE))
            {
                mImgParallaxAdj = TOTAL_POSITIVE_ADJUSTMENT/sZoom[mImgZoomInd];
            }
            else
            {
                mImgParallaxAdj = 0.0f;
            }
        }
    }

    //private void convertAnaglyph()
    //{
        //new AnaglyphGen(this, assetPairs).start();
    //}

    private String getFilePathFromUri(Uri uri) 
    {
        String filePath = null;
        if ("content".equals(uri.getScheme())) 
        {
       	    return null;

/*
            String[] filePathColumn = { MediaColumns.DATA };
            ContentResolver contentResolver = getContentResolver();

            Cursor cursor = contentResolver.query(uri, filePathColumn, null,
                    null, null);

            cursor.moveToFirst();

            //int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
            int columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
            filePath = cursor.getString(columnIndex);
            cursor.close();
*/
        } 
        else if ("file".equals(uri.getScheme())) 
        {
            filePath = new File(uri.getPath()).getAbsolutePath();
        }
        return filePath;
    }

    /**
     * Converts a raw text file into a string.
     *
     * @param resId The resource ID of the raw text file about to be turned into a shader.
     * @return The context of the text file, or null in case of error.
     */
    private String readRawTextFile(int resId) 
    {
        InputStream inputStream = getResources().openRawResource(resId);
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) 
            {
                sb.append(line).append("\n");
            }
            reader.close();
            return sb.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Prepares OpenGL ES before we draw a frame.
     *
     * @param headTransform The head transformation in the new frame.
     */
    Long lastNanotime = 0L;
    @Override
    public void onNewFrame(HeadTransform headTransform) 
    {
   	    if(mAssetInd == -1)
   	    {
    	    /*
    	     * we are still showing instruction, return without doing anything
    	     */
    	    return;
   	    }

   	    long currTimeMs = System.currentTimeMillis();
   	    long dt = currTimeMs - mLastTimeMs;
   	    if (dt < 33)
   	    {
			try {
				Thread.sleep(33 - dt);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
   	    }
   	    mLastTimeMs = currTimeMs;
    	    
        /*
         * aspect ratio of bitmap/surface aspect ratio
         */
        mDimRatio = ((float)mBitmapSizeCurr[0]/(float)mBitmapSizeCurr[1])/
        		((float)mSurfaceWidth/(float)mSurfaceHeight); 
    
        checkGLError("onReadyToDraw");
    }

    /**
     * Draws a frame for an eye.
     *
     * @param eye The eye to render. Includes all required transformations.
     */
    @Override
    public void onDrawEye(Eye eye) 
    {
        if(mAssetInd == -1)
   	    {
    	    // we are still showing instruction, return without doing anything
    	    return;
  	    }

   	    if(!mAssetChangedLeft && !mAssetChangedRight)
   	    {
    	    // nothing changed, do nothing and return
       	    return;
   	    }

        if(eye.getType() == Eye.Type.LEFT) 
       	    mAssetChangedLeft = false;
        else if(eye.getType() == Eye.Type.RIGHT) 
       	    mAssetChangedRight = false;

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        checkGLError("mColorParam");

        GLES20.glUseProgram(mPicProgram);

        GLES20.glUniform1f(mDimRatioParam, mDimRatio);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        if(eye.getType() == Eye.Type.LEFT)
        {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureCurr[0]);
        }
        else
        {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureCurr[1]);
        }

        // set the zoom level
        GLES20.glUniform1f(mZoomParam, sZoom[mImgZoomInd]);

        /*
         * if user prefers negative parallax, shift window on left frame leftward and right frame
         * rightward. if user prefers positive parallax, do the opposite
         */
        if(eye.getType() == Eye.Type.LEFT)
        {
            GLES20.glUniform1f(mParallaxParam, mImgParallaxAdj/2.0f);
        }
        else
        {
            GLES20.glUniform1f(mParallaxParam, -mImgParallaxAdj/2.0f);
        }

        // Set the position of the picture
        //float zoomCoords[] = new float[picCoords.length];
        //for(int i=0; i<picCoords.length; i++)
        	    //zoomCoords[i] = picCoords[i] * zoom[zoomInd];
        
        //ByteBuffer bblVertices = ByteBuffer.allocateDirect(zoomCoords.length * 4);
        ByteBuffer bblVertices = ByteBuffer.allocateDirect(picCoords.length * 4);
        bblVertices.order(ByteOrder.nativeOrder());
        mPicVertices = bblVertices.asFloatBuffer();
        //mPicVertices.put(zoomCoords);
        mPicVertices.put(picCoords);
        mPicVertices.position(0);

        GLES20.glVertexAttribPointer(mPicPositionParam, COORDS_PER_VERTEX, GLES20.GL_FLOAT,
                false, vertexStride, mPicVertices);

        GLES20.glDrawElements(
                GLES20.GL_TRIANGLES,  /* mode */
                6,                  /* count */
                GLES20.GL_UNSIGNED_SHORT,  /* type */
                mPicElements            /* element array buffer offset */
            );
    }

    @Override
    public void onFinishFrame(Viewport viewport) 
    {
    }

	@SuppressWarnings("unused")
	private void goBackwardTextures(int numOfAssets)
    {
        /*
         * move backwards 
         */
   	    int tmp0 = mTextureNext[0];
   	    int tmp1 = mTextureNext[1];

   	    mTextureNext[0] = mTextureCurr[0];
   	    mTextureNext[1] = mTextureCurr[1];
   	    mBitmapSizeNext = mBitmapSizeCurr;

   	    mTextureCurr[0] = mTexturePrev[0];
   	    mTextureCurr[1] = mTexturePrev[1];
   	    mBitmapSizeCurr = mBitmapSizePrev;

   	    mTexturePrev[0] = tmp0;
   	    mTexturePrev[1] = tmp1;

   	    /*
   	     * if we have less than 4 pictures, all we need to do is
   	     * shuffling the texture id's around.
   	     */
   	    if(numOfAssets <= 3)
   	    {
    	    return;
   	    }

        int prevInd = (mAssetInd - 1 + numOfAssets) % numOfAssets;

        mBitmapSizePrev = loadSbsImage(prevInd);
        loadTexture(mBmpL, mTexturePrev[0]);
        loadTexture(mBmpR, mTexturePrev[1]);

   	    // shouldn't be necessary but out of paranoia
   	    mBmpL.recycle(); mBmpL = null;
   	    mBmpR.recycle(); mBmpR = null;

        mAssetChangedLeft = mAssetChangedRight = true;
    }

    private void goForwardTextures(int numOfAssets)
    {
        /*
         * advance to load next picture as texture
         */
   	    int tmp0 = mTexturePrev[0];
   	    int tmp1 = mTexturePrev[1];

   	    mTexturePrev[0] = mTextureCurr[0];
   	    mTexturePrev[1] = mTextureCurr[1];
   	    mBitmapSizePrev = mBitmapSizeCurr;
   	    mTextureCurr[0] = mTextureNext[0];
   	    mTextureCurr[1] = mTextureNext[1];
   	    mBitmapSizeCurr = mBitmapSizeNext;

   	    mTextureNext[0] = tmp0;
   	    mTextureNext[1] = tmp1;

   	    /*
   	     * if we have less than 4 pictures, all we need to do is
   	     * shuffling the texture id's around.
   	     */
   	    if(numOfAssets <= 3)
   	    {
    	    return;
   	    }

        int nextInd = (mAssetInd + 1) % numOfAssets;
        mBitmapSizeNext = loadSbsImage(nextInd);
        loadTexture(mBmpL, mTextureNext[0]);
        loadTexture(mBmpR, mTextureNext[1]);

   	    // shouldn't be necessary but out of paranoia
   	    mBmpL.recycle(); mBmpL = null;
   	    mBmpR.recycle(); mBmpR = null;

        mAssetChangedLeft = mAssetChangedRight = true;
    }

    /**
     * Called when the Cardboard trigger is pulled.
     */
    @Override
    public void onCardboardTrigger() 
    {
        if(BuildConfig.DEBUG) Log.i(TAG, "onCardboardTrigger");

        if(mAssetInd == -1)
        {
       	    return;
        }

        // Always give user feedback.
        mVibrator.vibrate(50);

        final int numOfAssets = mAssetList.size();

        /*
         * sanity check
         */
        if(numOfAssets == 0)
        {
       	    return;
        }
        mAssetInd = (mAssetInd + 1) % numOfAssets;

        mCardboardView.queueEvent(new Runnable() 
        {
            @Override public void run() 
            {
                /*
                 * change the textures in renderer's context. doing it here 
                 * directly won't work because loading textures must be done
                 * when we have EGL context (which is only in the renderer) 
                 */
                goForwardTextures(numOfAssets);
            }
        });
    }
}
