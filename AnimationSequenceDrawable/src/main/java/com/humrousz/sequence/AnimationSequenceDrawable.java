package com.humrousz.sequence;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.os.SystemClock;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.util.Log;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * @author zhangzhiquan
 * @date 2018/2/8
 */

public class AnimationSequenceDrawable extends Drawable implements Animatable, Runnable {
    private static final String TAG = "BaseAnimationSequence";
    /**
     * These constants are chosen to imitate common browser behavior for WebP/GIF.
     * If other decoders are added, this behavior should be moved into the WebP/GIF decoders.
     * <p>
     * Note that 0 delay is undefined behavior in the GIF standard.
     */
    private static final long MIN_DELAY_MS = 20;
    private static final long DEFAULT_DELAY_MS = 100;
    private static final Object S_LOCK = new Object();
    private static HandlerThread sDecodingThread;
    private static Handler sDecodingThreadHandler;

    private static void initializeDecodingThread() {
        synchronized (S_LOCK) {
            if (sDecodingThread != null) {
                return;
            }
            sDecodingThread = new HandlerThread("BaseAnimationSequence decoding thread",
                    Process.THREAD_PRIORITY_BACKGROUND);
            sDecodingThread.start();
            sDecodingThreadHandler = new Handler(sDecodingThread.getLooper());
        }
    }

    public interface OnFinishedListener {
        /**
         * Called when a FrameSequenceDrawable has finished looping.
         * <p>
         * Note that this is will not be called if the drawable is explicitly
         * stopped, or marked invisible.
         * @param drawable
         */
         void onFinished(AnimationSequenceDrawable drawable);
    }

    public interface BitmapProvider {
        /**
         * Called by FrameSequenceDrawable to aquire an 8888 Bitmap with minimum dimensions.
         * @param minWidth
         * @param minHeight
         * @return
         */
         Bitmap acquireBitmap(int minWidth, int minHeight);

        /**
         * Called by FrameSequenceDrawable to release a Bitmap it no longer needs. The Bitmap
         * will no longer be used at all by the drawable, so it is safe to reuse elsewhere.
         * <p>
         * This method may be called by FrameSequenceDrawable on any thread.
         * @param bitmap
         */
         void releaseBitmap(Bitmap bitmap);
    }

    private static BitmapProvider sAllocatingBitmapProvider = new BitmapProvider() {
        @Override
        public Bitmap acquireBitmap(int minWidth, int minHeight) {
            return Bitmap.createBitmap(minWidth, minHeight, Bitmap.Config.ARGB_8888);
        }

        @Override
        public void releaseBitmap(Bitmap bitmap) {
        }
    };

    /**
     * Register a callback to be invoked when a FrameSequenceDrawable finishes looping.
     *
     * @see #setLoopBehavior(int)
     */
    public void setOnFinishedListener(OnFinishedListener onFinishedListener) {
        mOnFinishedListener = onFinishedListener;
    }

    /**
     * Loop a finite number of times, which can be set using setLoopCount. Default to loop once.
     */
    public static final int LOOP_FINITE = 1;
    /**
     * Loop continuously. The OnFinishedListener will never be called.
     */
    public static final int LOOP_INF = 2;
    /**
     * Use loop count stored in source data, or LOOP_ONCE if not present.
     */
    public static final int LOOP_DEFAULT = 3;

    /**
     * Define looping behavior of frame sequence.
     * <p>
     * Must be one of LOOP_ONCE, LOOP_INF, LOOP_DEFAULT, or LOOP_FINITE.
     */

    @IntDef({LOOP_FINITE,LOOP_INF,LOOP_DEFAULT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface LoopBehavior {}
    public void setLoopBehavior(@LoopBehavior int loopBehavior) {
        mLoopBehavior = loopBehavior;
    }

    /**
     * Set the number of loops in LOOP_FINITE mode. The number must be a postive integer.
     */
    public void setLoopCount(int loopCount) {
        mLoopCount = loopCount;
    }
    private final BaseAnimationSequence mAnimationSequence;
    private final Paint mPaint;
    private BitmapShader mFrontBitmapShader;
    private BitmapShader mBackBitmapShader;
    private final Rect mSrcRect;
    private boolean mCircleMaskEnabled;
    /**
     * Protects the fields below
     */
    private final Object mLock = new Object();
    private final BitmapProvider mBitmapProvider;
    private boolean mDestroyed = false;
    private Bitmap mFrontBitmap;
    private Bitmap mBackBitmap;
    private static final int STATE_SCHEDULED = 1;
    private static final int STATE_DECODING = 2;
    private static final int STATE_WAITING_TO_SWAP = 3;
    private static final int STATE_READY_TO_SWAP = 4;
    private int mState;
    private int mCurrentLoop;
    private int mLoopBehavior = LOOP_DEFAULT;
    private int mLoopCount = 1;
    private long mLastSwap;
    private long mNextSwap;
    private int mNextFrameToDecode;
    private OnFinishedListener mOnFinishedListener;
    private RectF mTempRectF = new RectF();

    /**
     * Runs on decoding thread, only modifies mBackBitmap's pixels
     */
    private Runnable mDecodeRunnable = new Runnable() {
        @Override
        public void run() {
            int nextFrame;
            Bitmap bitmap;
            synchronized (mLock) {
                if (mDestroyed) {
                    return;
                }
                nextFrame = mNextFrameToDecode;
                if (nextFrame < 0) {
                    return;
                }
                bitmap = mBackBitmap;
                mState = STATE_DECODING;
            }
            int lastFrame = nextFrame - 2;
            boolean exceptionDuringDecode = false;
            long invalidateTimeMs = 0;
            try {
                invalidateTimeMs = mAnimationSequence.getFrame(nextFrame, bitmap, lastFrame);
            } catch (Exception e) {
                // Exception during decode: continue, but delay next frame indefinitely.
                Log.e(TAG, "exception during decode: " + e);
                exceptionDuringDecode = true;
            }
            if (invalidateTimeMs < MIN_DELAY_MS) {
                invalidateTimeMs = DEFAULT_DELAY_MS;
            }
            boolean schedule = false;
            Bitmap bitmapToRelease = null;
            synchronized (mLock) {
                if (mDestroyed) {
                    bitmapToRelease = mBackBitmap;
                    mBackBitmap = null;
                } else if (mNextFrameToDecode >= 0 && mState == STATE_DECODING) {
                    schedule = true;
                    mNextSwap = exceptionDuringDecode ? Long.MAX_VALUE : invalidateTimeMs + mLastSwap;
                    mState = STATE_WAITING_TO_SWAP;
                }
            }
            if (schedule) {
                scheduleSelf(AnimationSequenceDrawable.this, mNextSwap);
            }
            if (bitmapToRelease != null) {
                // destroy the bitmap here, since there's no safe way to get back to
                // drawable thread - drawable is likely detached, so schedule is noop.
                mBitmapProvider.releaseBitmap(bitmapToRelease);
            }
        }
    };
    private Runnable mFinishedCallbackRunnable = new Runnable() {
        @Override
        public void run() {
            synchronized (mLock) {
                mNextFrameToDecode = -1;
                mState = 0;
            }
            if (mOnFinishedListener != null) {
                mOnFinishedListener.onFinished(AnimationSequenceDrawable.this);
            }
        }
    };

    private static Bitmap acquireAndValidateBitmap(BitmapProvider bitmapProvider,
                                                   int minWidth, int minHeight) {
        Bitmap bitmap = bitmapProvider.acquireBitmap(minWidth, minHeight);
        if (bitmap.getWidth() < minWidth
                || bitmap.getHeight() < minHeight
                || bitmap.getConfig() != Bitmap.Config.ARGB_8888) {
            throw new IllegalArgumentException("Invalid bitmap provided");
        }
        return bitmap;
    }

    public AnimationSequenceDrawable(BaseAnimationSequence sequence){
        this(sequence,sAllocatingBitmapProvider);
    }

    public AnimationSequenceDrawable(BaseAnimationSequence sequence, BitmapProvider bitmapProvider){
        final int width = sequence.getWidth();
        final int height = sequence.getHeight();
        mBitmapProvider = bitmapProvider;
        mFrontBitmap = acquireAndValidateBitmap(bitmapProvider, width, height);
        mBackBitmap = acquireAndValidateBitmap(bitmapProvider, width, height);
        mSrcRect = new Rect(0, 0, width, height);
        mPaint = new Paint();
        mPaint.setFilterBitmap(true);
        mFrontBitmapShader
                = new BitmapShader(mFrontBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        mBackBitmapShader
                = new BitmapShader(mBackBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        mLastSwap = 0;
        mNextFrameToDecode = -1;
        mAnimationSequence = sequence;
        mAnimationSequence.getFrame(0, mFrontBitmap, -1);
        initializeDecodingThread();
    }

    /**
     * Pass true to mask the shape of the animated drawing content to a circle.
     * <p>
     * <p> The masking circle will be the largest circle contained in the Drawable's bounds.
     * Masking is done with BitmapShader, incurring minimal additional draw cost.
     */
    public final void setCircleMaskEnabled(boolean circleMaskEnabled) {
        if (mCircleMaskEnabled != circleMaskEnabled) {
            mCircleMaskEnabled = circleMaskEnabled;
            // Anti alias only necessary when using circular mask
            mPaint.setAntiAlias(circleMaskEnabled);
            invalidateSelf();
        }
    }

    public final boolean getCircleMaskEnabled() {
        return mCircleMaskEnabled;
    }

    private void checkDestroyedLocked() {
        if (mDestroyed) {
            throw new IllegalStateException("Cannot perform operation on recycled drawable");
        }
    }

    public boolean isDestroyed() {
        synchronized (mLock) {
            return mDestroyed;
        }
    }

    /**
     * Marks the drawable as permanently recycled (and thus unusable), and releases any owned
     * Bitmaps drawable to its BitmapProvider, if attached.
     * <p>
     * If no BitmapProvider is attached to the drawable, recycle() is called on the Bitmaps.
     */
    public void destroy() {
        if (mBitmapProvider == null) {
            throw new IllegalStateException("BitmapProvider must be non-null");
        }
        Bitmap bitmapToReleaseA;
        Bitmap bitmapToReleaseB = null;
        synchronized (mLock) {
            checkDestroyedLocked();
            bitmapToReleaseA = mFrontBitmap;
            mFrontBitmap = null;
            if (mState != STATE_DECODING) {
                bitmapToReleaseB = mBackBitmap;
                mBackBitmap = null;
            }
            mDestroyed = true;
        }
        // For simplicity and safety, we don't destroy the state object here
        mBitmapProvider.releaseBitmap(bitmapToReleaseA);
        if (bitmapToReleaseB != null) {
            mBitmapProvider.releaseBitmap(bitmapToReleaseB);
        }
    }

    @Override
    public void start() {
        if (!isRunning()) {
            synchronized (mLock) {
                checkDestroyedLocked();
                // already scheduled
                if (mState == STATE_SCHEDULED){
                    return;
                }
                mCurrentLoop = 0;
                scheduleDecodeLocked();
            }
        }
    }

    @Override
    public void stop() {
        if (isRunning()) {
            unscheduleSelf(this);
        }
    }

    @Override
    public boolean isRunning() {
        synchronized (mLock) {
            return mNextFrameToDecode > -1 && !mDestroyed;
        }
    }
    @Override
    public void unscheduleSelf(Runnable what) {
        synchronized (mLock) {
            mNextFrameToDecode = -1;
            mState = 0;
        }
        super.unscheduleSelf(what);
    }

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        boolean changed = super.setVisible(visible, restart);
        if (!visible) {
            stop();
        } else if (restart || changed) {
            stop();
            start();
        } else if(!isRunning() && visible){
            start();
        }
        return changed;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        synchronized (mLock) {
            checkDestroyedLocked();
            if (mState == STATE_WAITING_TO_SWAP) {
                // may have failed to schedule mark ready runnable,
                // so go ahead and swap if swapping is due
                if (mNextSwap - SystemClock.uptimeMillis() <= 0) {
                    mState = STATE_READY_TO_SWAP;
                }
            }
            if (isRunning() && mState == STATE_READY_TO_SWAP) {
                // Because draw has occurred, the view system is guaranteed to no longer hold a
                // reference to the old mFrontBitmap, so we now use it to produce the next frame
                Bitmap tmp = mBackBitmap;
                mBackBitmap = mFrontBitmap;
                mFrontBitmap = tmp;
                BitmapShader tmpShader = mBackBitmapShader;
                mBackBitmapShader = mFrontBitmapShader;
                mFrontBitmapShader = tmpShader;
                mLastSwap = SystemClock.uptimeMillis();
                boolean continueLooping = true;
                if (mNextFrameToDecode == mAnimationSequence.getFrameCount() - 1) {
                    mCurrentLoop++;
                    boolean stopLooping = (mLoopBehavior == LOOP_FINITE && mCurrentLoop == mLoopCount) ||
                            (mLoopBehavior == LOOP_DEFAULT && mCurrentLoop == mAnimationSequence.getDefaultLoopCount());
                    if (stopLooping) {
                        continueLooping = false;
                    }
                }
                if (continueLooping) {
                    scheduleDecodeLocked();
                } else {
                    scheduleSelf(mFinishedCallbackRunnable, 0);
                }
            }
        }
        if (mCircleMaskEnabled) {
            final Rect bounds = getBounds();
            final int bitmapWidth = getIntrinsicWidth();
            final int bitmapHeight = getIntrinsicHeight();
            final float scaleX = 1.0f * bounds.width() / bitmapWidth;
            final float scaleY = 1.0f * bounds.height() / bitmapHeight;
            canvas.save();
            // scale and translate to account for bounds, so we can operate in intrinsic
            // width/height (so it's valid to use an unscaled bitmap shader)
            canvas.translate(bounds.left, bounds.top);
            canvas.scale(scaleX, scaleY);
            final float unscaledCircleDiameter = Math.min(bounds.width(), bounds.height());
            final float scaledDiameterX = unscaledCircleDiameter / scaleX;
            final float scaledDiameterY = unscaledCircleDiameter / scaleY;
            // Want to draw a circle, but we have to compensate for canvas scale
            mTempRectF.set(
                    (bitmapWidth - scaledDiameterX) / 2.0f,
                    (bitmapHeight - scaledDiameterY) / 2.0f,
                    (bitmapWidth + scaledDiameterX) / 2.0f,
                    (bitmapHeight + scaledDiameterY) / 2.0f);
            mPaint.setShader(mFrontBitmapShader);
            canvas.drawOval(mTempRectF, mPaint);
            canvas.restore();
        } else {
            mPaint.setShader(null);
            canvas.drawBitmap(mFrontBitmap, mSrcRect, getBounds(), mPaint);
        }

    }

    private void scheduleDecodeLocked() {
        mState = STATE_SCHEDULED;
        mNextFrameToDecode = (mNextFrameToDecode + 1) % mAnimationSequence.getFrameCount();
        sDecodingThreadHandler.post(mDecodeRunnable);
    }

    /**
     * drawing properties
     */

    @Override
    public void setFilterBitmap(boolean filter) {
        mPaint.setFilterBitmap(filter);
    }

    @Override
    public void setAlpha(int alpha) {
        mPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        mPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getIntrinsicWidth() {
        return mAnimationSequence.getWidth();
    }

    @Override
    public int getIntrinsicHeight() {
        return mAnimationSequence.getHeight();
    }

    @Override
    public int getOpacity() {
        return mAnimationSequence.isOpaque() ?  PixelFormat.OPAQUE : PixelFormat.TRANSPARENT;
    }

    @Override
    public void run() {
        // set ready to swap as necessary
        boolean invalidate = false;
        synchronized (mLock) {
            if (mNextFrameToDecode >= 0 && mState == STATE_WAITING_TO_SWAP) {
                mState = STATE_READY_TO_SWAP;
                invalidate = true;
            }
        }
        if (invalidate) {
            invalidateSelf();
        }
    }
}
