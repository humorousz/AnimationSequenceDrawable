# AnimationSequenceDrawable
## 介绍
根据[Google FrameSequenceDrawable](https://github.com/humorousz/FrameSequenceDrawable)的思想，抽象了Sequence并且使用了Facebook的Fresco中的animated-webp解析Webp文件，因为抽象了Sequence所以可以根据很少的替换来实现AnimationSequenceDrawable可以同时满足webp和gif图片的需求
[一个如何使用的小例子](https://github.com/humorousz/AnimationSequence)
## 如何引入到工程
-  Add the JitPack repository to your build file
``` gradle
	allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}
```

- Add the dependency
 ``` gradle
	dependencies {
	        compile 'com.github.humorousz:AnimationSequenceDrawable:1.0.1-SNAPSHOT'
	}

 ```
## 如何使用
#### 使用webp
- xml
``` xml
 <com.humrousz.sequence.AnimationImageView
        android:id="@+id/fresco_sequence_image"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:layout_above="@+id/group"
        app:loopCount="1"
        app:loopBehavior="loop_default|loop_finite|loop_inf"
        android:scaleType="centerCrop"
        android:src="@drawable/webpRes"/>
```
- java
``` java
public void setImage(){
    AnimationImageView mFrescoImage;
    mFrescoImage = findViewById(R.id.fresco_sequence_image);
    //设置重复次数
    mFrescoImage.setLoopCount(1);
    //重复行为默认 根据webp图片循环次数决定
    mFrescoImage.setLoopDefault();
    //重复行为无限
    mFrescoImage.setLoopInf();
    //重复行为为指定  跟setLoopCount有关
    mFrescoImage.setLoopFinite();
    //设置Assets下的图片
    mFrescoImage.setImageResourceFromAssets("newyear.webp");
    //设置图片通过drawable
    mFrescoImage.setImageResource(R.drawable.newyear);
    Uri uri = Uri.parse("file:"+Environment.getExternalStorageDirectory().toString()+"/animation");
    //通过添加"file:"协议，可以展示指定路径的图片，如例子中的本地资源
    mFrescoImage.setImageURI(uri);
}
```
##### 当然你也可以不使用我这里的AnimationImageView，AnimationImageView是我参考其它的代码后修改封装的类，直接使用BaseAnimationSequence+ImageView也是可以的，使用方法如下
``` java
 InputStream in = null;
try {
    in = getResources().getAssets().open("rmb.webp");
    //因为需要支持webp和gif所以创建Sequence我们使用了工厂模式，并且Drawable依赖的是抽象的BaseAnimationSequence
    //所以后续如果有新的解析引擎可以直接实现BaseSequenceFactory即可
    BaseSequenceFactory factory = FrescoSequence.getSequenceFactory(FrescoSequence.WEBP);
    final AnimationSequenceDrawable drawable = new AnimationSequenceDrawable(factory.createSequence(in));
    drawable.setLoopCount(1);
    drawable.setLoopBehavior(AnimationSequenceDrawable.LOOP_FINITE);
    drawable.setOnFinishedListener(new AnimationSequenceDrawable.OnFinishedListener() {
        @Override
        public void onFinished(AnimationSequenceDrawable frameSequenceDrawable) {

        }
    });
    mFrescoImage.setImageDrawable(drawable);
} catch (IOException e) {
    e.printStackTrace();
}
```
#### 使用Gif
##### 使用Gif和Webp大体上的代码都是一样的，但是因为我们使用的帧序列并不是一样的，默认的解析工厂是webp的，所以需要我们在使用gif的时候设置一下factory,如果在xml中使用设置srcType=“gif”即可
- xml
``` xml
<com.humrousz.sequence.AnimationImageView
        android:id="@+id/gif_image"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:srcType="gif"
        android:src="@drawable/lion"/>
```
- java
``` java
public void setImage(){
    AnimationImageView mGifImage;
    //注意，这个很重要，使用gif时，一定要先设置这个工厂，它是负责将输入的inputStream按照gif的格式进行解析的工厂
    //默认的是webp所以使用webp时不需要特殊设置
    mGifImage.setSequenceFactory(FrescoSequence.getSequenceFactory(FrescoSequence.GIF));
    mGifImage = findViewById(R.id.fresco_sequence_image);
    //设置重复次数
    mGifImage.setLoopCount(1);
    //重复行为默认 根据webp图片循环次数决定
    mGifImage.setLoopDefault();
    //重复行为无限
    mGifImage.setLoopInf();
    //重复行为为指定  跟setLoopCount有关
    mGifImage.setLoopFinite();
    //设置Assets下的图片
    mGifImage.setImageResourceFromAssets("newyear.gif");
    //设置图片通过drawable
    mGifImage.setImageResource(R.drawable.newyear);
    Uri uri = Uri.parse("file:"+Environment.getExternalStorageDirectory().toString()+"/animation");
    //通过添加"file:"协议，可以展示指定路径的图片，如例子中的本地资源
    mGifImage.setImageURI(uri);
}
```
##### 同样你也可以不使用我这里的AnimationImageView，AnimationImageView是我参考其它的代码后修改封装的类，直接使用BaseAnimationSequence+ImageView也是可以的，使用方法如下
``` java
InputStream in = null;
try {
    in = getResources().getAssets().open("lion.gif");
    BaseSequenceFactory factory = FrescoSequence.getSequenceFactory(FrescoSequence.GIF);
    final AnimationSequenceDrawable drawable = new AnimationSequenceDrawable(factory.createSequence(in));
    drawable.setLoopCount(1);
    drawable.setLoopBehavior(AnimationSequenceDrawable.LOOP_FINITE);
    drawable.setOnFinishedListener(new AnimationSequenceDrawable.OnFinishedListener() {
        @Override
        public void onFinished(AnimationSequenceDrawable frameSequenceDrawable) {

        }
    });
    mGifImage.setImageDrawable(drawable);
} catch (IOException e) {
    e.printStackTrace();
}
```


## 依赖的三方库
``` gradle
compile 'com.facebook.fresco:animated-webp:0.12.0'
compile 'com.facebook.fresco:animated-gif:0.12.0'
```
