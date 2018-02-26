package com.humrousz.sequence;

import java.io.InputStream;

/**
 * @author zhangzhiquan
 * @date 2018/2/8
 */

abstract public class BaseSequenceFactory {
    /**
     * create Sequence
     * @param inputStream
     * @return
     */
     abstract public BaseAnimationSequence createSequence(InputStream inputStream);
}
