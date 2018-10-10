# mediaSamples
多媒体方向，视频、音频、图片处理。
主要使用[javacv库](https://github.com/bytedeco/javacv)(计算机视觉处理)

- 不使用mediaRecorder进行，使用javacv封装的FFMPEG实现逐帧录像，同时使用该框架的音频录制，最终将音频视频合成一个文件。
1. 添加相机预览功能。
2. 在相机预览画面中。
3. 增加自动对焦，手动选择区域对焦。
4. 增加每帧视频数据的录制。
5. 增加每帧音频数据的录制。
6. 增加视频旋转角度，后摄需要旋转90度（前摄旋转270度），录制的视频炫耀旋转90度，得到正确的视频方向。

