package com.richardmcdougall.bb;

/**
 * Copyright 2011, Felix Palmer
 *
 * Licensed under the MIT license:
 * http://creativecommons.org/licenses/MIT/
 */

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.media.audiofx.Visualizer;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import com.richardmcdougall.bb.BarGraphRenderer;

import java.util.HashSet;
import java.util.Set;

/**
 * A class that draws visualizations of data received from a
 * {@link Visualizer.OnDataCaptureListener#onWaveFormDataCapture } and
 * {@link Visualizer.OnDataCaptureListener#onFftDataCapture }
 */
public class VisualizerView extends View {

    private static final String TAG = "VisualizerView";

    private byte[] mBytes;
    private byte[] mFFTBytes;
    private Rect mRect = new Rect();
    private Visualizer mVisualizer;
    private int mAudioSessionId;

    private Set<Renderer> mRenderers;

    private Paint mFlashPaint = new Paint();
    private Paint mFadePaint = new Paint();

    private MainActivity mActivity = null;

    public VisualizerView(Context context, AttributeSet attrs, int defStyle)
    {
        super(context, attrs);
        if (!isInEditMode()) {
            System.out.println("Starting VisualizerView");
            init();
        }
    }

    public VisualizerView(Context context, AttributeSet attrs)
    {
        this(context, attrs, 0);
    }

    public VisualizerView(Context context)
    {
        this(context, null, 0);
    }

    private void init() {
        mBytes = null;
        mFFTBytes = null;

        mFlashPaint.setColor(Color.argb(122, 255, 255, 255));
        mFadePaint.setColor(Color.argb(200, 255, 255, 255)); // Adjust alpha to
        // change how
        // quickly the
        // image fades
        mFadePaint.setXfermode(new PorterDuffXfermode(Mode.MULTIPLY));

        mRenderers = new HashSet<Renderer>();
    }


    /**
     * Links the visualizer to a player
     *
     * @param player - MediaPlayer instance to link to
     */
    public void link(int audioSessionId)
    {
        System.out.println("Linking VisualizerView");
        if (mVisualizer != null && audioSessionId != mAudioSessionId) {
            mVisualizer.setEnabled(false);
            mVisualizer.release();
            mVisualizer = null;
        }

        Log.i(TAG, "session=" + audioSessionId);
        mAudioSessionId = audioSessionId;

        if (mVisualizer == null) {

            // Create the Visualizer object and attach it to our media player.
            try {
                mVisualizer = new Visualizer(audioSessionId);
            } catch (Exception e) {
                Log.e(TAG, "Error enabling visualizer!", e);
                System.out.println("Error enabling visualizer:" + e.getMessage());
                return;
            }
            mVisualizer.setEnabled(false);
            mVisualizer.setCaptureSize(Visualizer.getCaptureSizeRange()[1]);

            // Pass through Visualizer data to VisualizerView
            Visualizer.OnDataCaptureListener captureListener = new Visualizer.OnDataCaptureListener()
            {
                @Override
                public void onWaveFormDataCapture(Visualizer visualizer, byte[] bytes,
                                                  int samplingRate)
                {
                    updateVisualizer(bytes);
                }

                @Override
                public void onFftDataCapture(Visualizer visualizer, byte[] bytes,
                                             int samplingRate)
                {
                    updateVisualizerFFT(bytes);
                }
            };

            mVisualizer.setDataCaptureListener(captureListener,
                    (int) (Visualizer.getMaxCaptureRate() * 0.75), true, true);

        }
        mVisualizer.setEnabled(true);
    }

    public void unlink() {
        if (mVisualizer != null) {
            mVisualizer.setEnabled(false);
            mVisualizer.release();
            mVisualizer = null;
        }
    }

    public void addRenderer(Renderer renderer)
    {
        if (renderer != null)
        {
            mRenderers.add(renderer);
        }
    }

    public void clearRenderers()
    {
        mRenderers.clear();
    }

    /**
     * Call to release the resources used by VisualizerView. Like with the
     * MediaPlayer it is good practice to call this method
     */
    public void release()
    {
        mVisualizer.release();
    }

    /**
     * Pass data to the visualizer. Typically this will be obtained from the
     * Android Visualizer.OnDataCaptureListener call back. See
     * {@link Visualizer.OnDataCaptureListener#onWaveFormDataCapture }
     *
     * @param bytes
     */
    public void updateVisualizer(byte[] bytes) {
        mBytes = bytes;
        invalidate();
        //if (mActivity != null) {
        //     synchronized (mActivity.mBoardFFT) {
        //        mActivity.mBoardFFT = bytes.clone();
        //    }
        //}

    }

    /**
     * Pass FFT data to the visualizer. Typically this will be obtained from the
     * Android Visualizer.OnDataCaptureListener call back. See
     * {@link Visualizer.OnDataCaptureListener#onFftDataCapture }
     *
     * @param bytes
     */
    public void updateVisualizerFFT(byte[] bytes) {
        mFFTBytes = bytes;
        invalidate();
    }

    public void updateVisualizerFFTnoInvalididate(byte[] bytes) {
        mFFTBytes = bytes;
    }

    boolean mFlash = false;

    /**
     * Call this to make the visualizer flash. Useful for flashing at the start
     * of a song/loop etc...
     */
    public void flash() {
        mFlash = true;
        invalidate();
    }

    Bitmap mCanvasBitmap;
    Canvas mCanvas;

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (!isInEditMode()) {

            // Create canvas once we're ready to draw
            mRect.set(0, 0, getWidth(), getHeight());

            if (mCanvasBitmap == null) {
                mCanvasBitmap = Bitmap.createBitmap(canvas.getWidth(), canvas.getHeight(),
                        Config.ARGB_8888);
            }
            if (mCanvas == null) {
                mCanvas = new Canvas(mCanvasBitmap);
            }

            if (mBytes != null) {
                // Render all audio renderers
                AudioData audioData = new AudioData(mBytes);
                for (Renderer r : mRenderers) {
                    r.render(mCanvas, audioData, mRect);
                }
            }

            if (mFFTBytes != null) {
                // Render all FFT renderers
                FFTData fftData = new FFTData(mFFTBytes);
                for (Renderer r : mRenderers) {
                    r.render(mCanvas, fftData, mRect);
                }
            }

            // Fade out old contents
            mCanvas.drawPaint(mFadePaint);

            if (mFlash) {
                mFlash = false;
                mCanvas.drawPaint(mFlashPaint);
            }

            canvas.drawBitmap(mCanvasBitmap, new Matrix(), null);
        }
    }

    // Methods for adding renderers to visualizer
    public void addBurnerBoardRenderer(MainActivity activity)
    {
        BurnerBoardRenderer burnerBoardRenderer = new BurnerBoardRenderer(16, activity);
        addRenderer(burnerBoardRenderer);
    }

    // Methods for adding renderers to visualizer
    public void addBarGraphRendererBottom()
    {
        Paint paint = new Paint();
        paint.setStrokeWidth(50f);
        paint.setAntiAlias(true);
        paint.setColor(Color.argb(200, 56, 138, 252));
        BarGraphRenderer barGraphRendererBottom = new BarGraphRenderer(16, paint, false);
        addRenderer(barGraphRendererBottom);
    }

    public void addBarGraphRendererTop() {
        Paint paint2 = new Paint();
        paint2.setStrokeWidth(12f);
        paint2.setAntiAlias(true);
        paint2.setColor(Color.argb(200, 181, 111, 233));
        BarGraphRenderer barGraphRendererTop = new BarGraphRenderer(4, paint2, true);
        addRenderer(barGraphRendererTop);
    }

    /*
    public void addCircleBarRenderer()
    {
        Paint paint = new Paint();
        paint.setStrokeWidth(8f);
        paint.setAntiAlias(true);
        paint.setXfermode(new PorterDuffXfermode(Mode.LIGHTEN));
        paint.setColor(Color.argb(255, 222, 92, 143));
        CircleBarRenderer circleBarRenderer = new CircleBarRenderer(paint, 32, true);
        addRenderer(circleBarRenderer);
    }

    public void addCircleRenderer()
    {
        Paint paint = new Paint();
        paint.setStrokeWidth(3f);
        paint.setAntiAlias(true);
        paint.setColor(Color.argb(255, 222, 92, 143));
        CircleRenderer circleRenderer = new CircleRenderer(paint, true);
        addRenderer(circleRenderer);
    }

    public void addLineRenderer()
    {
        Paint linePaint = new Paint();
        linePaint.setStrokeWidth(1f);
        linePaint.setAntiAlias(true);
        linePaint.setColor(Color.argb(88, 0, 128, 255));

        Paint lineFlashPaint = new Paint();
        lineFlashPaint.setStrokeWidth(5f);
        lineFlashPaint.setAntiAlias(true);
        lineFlashPaint.setColor(Color.argb(188, 255, 255, 255));
        LineRenderer lineRenderer = new LineRenderer(linePaint, lineFlashPaint, true);
        addRenderer(lineRenderer);
    }
    */

}