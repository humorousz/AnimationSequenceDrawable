package com.humrousz.sequence;

import android.graphics.Bitmap;
import android.support.annotation.IntDef;

import com.facebook.animated.gif.GifImage;
import com.facebook.animated.webp.WebPImage;
import com.facebook.imagepipeline.animated.base.AnimatedImage;
import com.facebook.imagepipeline.animated.base.AnimatedImageFrame;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * @author zhangzhiquan
 * @date 2018/2/8
 */

public class FrescoSequence extends BaseAnimationSequence {

    public static final int WEBP = 1;
    public static final int GIF = 2;

    @IntDef({WEBP, GIF})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ImageType {}

    private AnimatedImage mWebpImage;

    public FrescoSequence(AnimatedImage image){
        this(image.getWidth(),image.getHeight(),image.getFrameCount(),image.getLoopCount());
        mWebpImage = image;
    }

    private FrescoSequence(int width, int height, int frameCount, int defaultLoopCount) {
        super(width, height, frameCount, defaultLoopCount);
    }

    @Override
    public long getFrame(int frameNr, Bitmap output, int previousFrameNr) {
        AnimatedImageFrame frame =  mWebpImage.getFrame(frameNr);
        frame.renderFrame(mWebpImage.getWidth(),mWebpImage.getHeight(),output);
        int lastFrame = (frameNr + mWebpImage.getFrameCount() - 1) % mWebpImage.getFrameCount();
        return mWebpImage.getFrame(lastFrame).getDurationMs();
    }

    @Override
    public boolean isOpaque() {
        return false;
    }

    public static FrescoSequence decodeStream(InputStream in,@ImageType int type){
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buff = new byte[1024];
        int rc;
        try {
            while ((rc = in.read(buff,0,buff.length)) > 0){
                out.write(buff,0,rc);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        byte[] bytes = out.toByteArray();
        switch (type){
            case GIF :
                return decodeGifPByteArray(bytes);
            default:
                return decodeWebPByteArray(bytes);
        }
    }



    public static FrescoSequence decodeWebPByteArray(byte[] data){
        return new FrescoSequence(WebPImage.create(data));
    }

    public static FrescoSequence decodeGifPByteArray(byte[] data){
        return new FrescoSequence(GifImage.create(data));
    }

    public static class FrescoWebpSequenceFactory extends BaseSequenceFactory {
        @Override
        public BaseAnimationSequence createSequence(InputStream inputStream) {
            return decodeStream(inputStream,WEBP);
        }
    }

    public static class FrescoGifSequenceFactory extends BaseSequenceFactory {
        @Override
        public BaseAnimationSequence createSequence(InputStream inputStream) {
            return decodeStream(inputStream,GIF);
        }
    }

    public static BaseSequenceFactory getSequenceFactory(int srcType ){
        if(srcType == GIF) {
            return new FrescoGifSequenceFactory();
        }else {
            return new FrescoWebpSequenceFactory();
        }
    }


}
