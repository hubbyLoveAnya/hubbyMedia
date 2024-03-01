package com.hubby.audiorecord;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.util.Log;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class AudioTracker {
    private static final String TAG = "AudioTracker";
    private final int mRateInHz;
    private final int mChannelConfig;
    private final int mAudioFormat;

    private AudioTrackRunnable mPlayRunnable;

    public AudioTracker(int hz, int channelConfig, int audioFormat) {
        this.mRateInHz = hz;
        this.mChannelConfig = channelConfig;
        this.mAudioFormat = audioFormat;
    }

    static class AudioTrackRunnable implements Runnable, AudioTrack.OnPlaybackPositionUpdateListener {
        private static final int ONE_SECOND = 1000;
        private final AudioAttributes audioAttributes;

        private final AudioFormat audioFormat;

        private String fileName;

        private AudioTrack audioTrack;

        private ProcessListener processListener;

        private Handler processHandler;

        private final int minBufferSize;
        private final AtomicBoolean isPlaying = new AtomicBoolean(false);

        AudioTrackRunnable(int hz, int channelConfig, int encoding) {
            this.minBufferSize = AudioTrack.getMinBufferSize(hz, channelConfig, encoding);
            this.audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            this.audioFormat = new AudioFormat.Builder()
                    .setSampleRate(hz)
                    .setEncoding(encoding)
                    .setChannelMask(channelConfig)
                    .build();
        }

        @Override
        public void run() {
            FileInputStream destInput = null;
            try {
                destInput = new FileInputStream(fileName);
                byte[] frameBuffer = new byte[minBufferSize];
                long fileSize = destInput.getChannel().size();
                if (fileSize > 0) {
                    if (processListener != null) {
                        processHandler.post(() -> {
                            //数据量Byte= 采样率×(采样位数/8)×声道数×时间（s)
                            //时间 = 数据大小/采样率/（采样位数/8）/声道数
                            int pcmTimeCount = (int) ((fileSize * 1.0f) / (16.0f / 8.0f) / (2.0f) / 44100);
                            processListener.onInit(pcmTimeCount);
                        });
                    }
                }
                audioTrack = new AudioTrack(audioAttributes, audioFormat, minBufferSize,
                        AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE);
                audioTrack.setPositionNotificationPeriod(ONE_SECOND);
                audioTrack.setPlaybackPositionUpdateListener(this);
                audioTrack.play();
                isPlaying.set(true);
                int length;
                while (isPlaying.get() && (length = destInput.read(frameBuffer)) > 0) {
                    audioTrack.write(frameBuffer, 0, length);
                }
                isPlaying.set(false);
                audioTrack.stop();
                audioTrack.release();
                Log.d(TAG, "play finish");
                if (null != processListener) {
                    processHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            processListener.onPlayTime(0);
                        }
                    });
                }
            } catch (IOException e) {
                Log.e(TAG, "read Failed!", e);
                isPlaying.set(false);
            } finally {
                try {
                    if (destInput != null) {
                        destInput.close();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "close erorr", e);
                }
            }
        }

        @Override
        public void onMarkerReached(AudioTrack track) {

        }

        @Override
        public void onPeriodicNotification(AudioTrack track) {
            int playFrame = audioTrack.getPlaybackHeadPosition();
            int rate = audioTrack.getPlaybackRate();
            //计算播放到了多少s
            float currentPlayTime = playFrame * 1.0f / rate;
            if (null != processListener) {
                processHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        processListener.onPlayTime(Math.round(currentPlayTime));
                    }
                });
            }
        }

        public void stop() {
            Log.d(TAG, "stop play Runnable");
            if (null != audioTrack) {
                isPlaying.set(false);
                audioTrack.stop();
            }
        }
    }

    public interface ProcessListener {
        void onInit(int totalSecond);

        void onPlayTime(int currentPlayTime);
    }

    public void startPlay(String file, ProcessListener processListener, Handler handler) throws FileNotFoundException {
        File destFile = new File(file);
        if (!destFile.exists()) {
            throw new FileNotFoundException("file not exists:" + file);
        }
        if (processListener != null && handler == null) {
            throw new IllegalArgumentException("processListener must has handler!");
        }
        if (null == mPlayRunnable) {
            mPlayRunnable = new AudioTrackRunnable(mRateInHz, mChannelConfig, mAudioFormat);
        }
        if (mPlayRunnable.isPlaying.get()) {
            Log.e(TAG, "isPlaying...");
            return;
        }
        mPlayRunnable.fileName = file;
        mPlayRunnable.processListener = processListener;
        mPlayRunnable.processHandler = handler;
        new Thread(mPlayRunnable).start();
    }

    public void stop() {
        if (null != mPlayRunnable) {
            if (mPlayRunnable.isPlaying.get()) {
                mPlayRunnable.stop();
            }
        }
    }
}
