package com.humrousz.sequence;

import android.graphics.Bitmap;


/**
 * @author zhangzhiquan
 * @date 2018/2/8
 */

abstract public class BaseAnimationSequence {
    private final int mWidth;
    private final int mHeight;
    private final int mFrameCount;
    private final int mDefaultLoopCount;
    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    public int getFrameCount() {
        return mFrameCount;
    }

    public int getDefaultLoopCount() {
        return mDefaultLoopCount;
    }

    public BaseAnimationSequence(int width, int height, int frameCount, int defaultLoopCount){
        mWidth = width;
        mHeight = height;
        mFrameCount = frameCount;
        mDefaultLoopCount = defaultLoopCount;
    }

    /**
     * getFrame
     * @param frameNr
     * @param output
     * @param previousFrameNr
     * @return
     */
    abstract public long getFrame(int frameNr, Bitmap output, int previousFrameNr);

    /**
     * isOpaque
     * @return
     */
    abstract public boolean isOpaque();


}
