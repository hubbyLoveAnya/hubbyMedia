package com.hubby.audiorecord;

import static android.media.AudioRecord.ERROR;
import static android.media.AudioRecord.ERROR_BAD_VALUE;
import static android.media.AudioRecord.ERROR_DEAD_OBJECT;
import static android.media.AudioRecord.ERROR_INVALID_OPERATION;
import static android.media.AudioRecord.RECORDSTATE_RECORDING;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.hubby.audiorecord.exception.PermissionLessException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

public class AudioRecorder {
    private static final String TAG = "AudioRecorder";

    public static final String[] PERMISSION_REQUIRED
            = new String[]{Manifest.permission.RECORD_AUDIO};

    private final Context context;
    private final int mMinBufferSize;
    private final int mRateInHz;
    private final int mChannelConfig;
    private final int mAudioFormat;
    private volatile AudioRecordRunnable mRecordRunnable;
    private final OnAudioFrameCaptureListener mCaptureListener;
    private String mTempFile;
    private final String TEMP_FILE;

    public interface OnAudioFrameCaptureListener {
        void onFrameCaptured(byte[] frameData);
    }

    public AudioRecorder(Context context, int sampleRateInHz, int channelConfig, int audioFormat, OnAudioFrameCaptureListener listener, String tempFile) {
        this.context = context;
        mRateInHz = sampleRateInHz;
        mChannelConfig = channelConfig;
        mAudioFormat = audioFormat;
        mMinBufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);
        this.mCaptureListener = listener;
        this.mTempFile = tempFile;
        if (TextUtils.isEmpty(tempFile)) {
            TEMP_FILE = context.getCacheDir() + File.separator + "hubby_temp.pcm";
        } else {
            TEMP_FILE = tempFile;
        }
    }

    private boolean checkPermission(String... permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public void startRecord() throws PermissionLessException {
        if (!checkPermission(PERMISSION_REQUIRED)) {
            String errorInfo = "Permission Required!!! need:" + Arrays.toString(PERMISSION_REQUIRED);
            throw new PermissionLessException(errorInfo);
        }
        if (mRateInHz < 44100 || mChannelConfig <= AudioFormat.CHANNEL_IN_LEFT
                || mAudioFormat == AudioFormat.ENCODING_INVALID) {
            String errorInfo = "param error! mRateInHz = " + mRateInHz + " mChannelConfig = "
                    + mChannelConfig + " mAudioFormat = " + mAudioFormat;
            throw new IllegalArgumentException(errorInfo);
        }
        if (null == mRecordRunnable) {
            if (TextUtils.isEmpty(mTempFile)) {
                mTempFile = TEMP_FILE;
            }
            mRecordRunnable = new AudioRecordRunnable(mTempFile, mRateInHz, mChannelConfig, mAudioFormat, mCaptureListener);
        }
        if (mRecordRunnable.isRecording.get()) {
            Log.e(TAG, "isRecording Now!!!");
            return;
        }
        // 每次新开启录音，都重新创建AudioRecord,因为退出时会release掉当前runnable中的AudioRecord
        mRecordRunnable.audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, mRateInHz, mChannelConfig,
                mAudioFormat, mMinBufferSize);
        new Thread(mRecordRunnable).start();
    }

    public void stopRecord() {
        if (mRecordRunnable != null) {
            mRecordRunnable.stop();
        }
    }

    public String getTempFilePath() {
        return TEMP_FILE;
    }

    static class AudioRecordRunnable implements Runnable {
        private final int minBufferSize;
        private final String tempFileName;
        private final AtomicBoolean isRecording = new AtomicBoolean(false);
        private AudioRecord audioRecord;
        private final OnAudioFrameCaptureListener captureListener;
        private final int rateInHz;

        AudioRecordRunnable(String tempFile, int rateInHz, int channelConfig, int audioFormat, OnAudioFrameCaptureListener listener) {
            this.tempFileName = tempFile;
            this.rateInHz = rateInHz;
            this.captureListener = listener;
            this.minBufferSize = AudioRecord.getMinBufferSize(rateInHz, channelConfig, audioFormat);
        }

        @Override
        public void run() {
            Log.d(TAG, "start record");
            audioRecord.startRecording();
            isRecording.set(true);
            byte[] data = new byte[minBufferSize];
            File tempPcm = null;
            File tempWav = null;
            FileOutputStream outputStream = null;
            FileOutputStream wavOs = null;
            RandomAccessFile wavRaf = null;
            if (!TextUtils.isEmpty(tempFileName)) {
                try {
                    tempPcm = new File(tempFileName);
                    tempWav = new File(tempFileName.substring(0, tempFileName.lastIndexOf(".")) + ".wav");
                    if (!tempWav.exists()) {
                        if (tempWav.createNewFile()) {
                            Log.d(TAG, "new tempWav path:" + tempWav.getAbsolutePath());
                        }
                    }
                    wavOs = new FileOutputStream(tempWav);
                    byte[] wavHeader = WavUtil.generateWavFileHeader(0, rateInHz, audioRecord.getChannelCount());
                    wavOs.write(wavHeader, 0, wavHeader.length);
                    if (!tempPcm.exists()) {
                        boolean newFile = tempPcm.createNewFile();
                        if (newFile) {
                            Log.d(TAG, "new tempFile path:" + tempFileName);
                        }
                    }
                    outputStream = new FileOutputStream(tempPcm);
                } catch (IOException e) {
                    Log.e(TAG, "create File error!", e);
                    isRecording.set(false);
                }
            }
            int read;
            while (isRecording.get()) {
                read = audioRecord.read(data, 0, minBufferSize);
                if (ERROR_INVALID_OPERATION != read
                        && ERROR_BAD_VALUE != read
                        && ERROR_DEAD_OBJECT != read
                        && ERROR != read) {
                    if (captureListener != null) {
                        captureListener.onFrameCaptured(data);
                    }
                    if (null != outputStream) {
                        try {
                            outputStream.write(data, 0, read);
                            wavOs.write(data, 0, read);
                        } catch (IOException e) {
                            isRecording.set(false);
                            break;
                        }
                    }
                }
            }
            try {
                if (tempPcm != null) {
                    wavRaf = new RandomAccessFile(tempWav, "rw");
                    byte[] header = WavUtil.generateWavFileHeader(tempPcm.length(), rateInHz, audioRecord.getChannelCount());
                    wavRaf.seek(0);
                    wavRaf.write(header);
                }
            } catch (IOException e) {
                Log.e(TAG, "write header error!", e);
            } finally {
                try {
                    if (wavRaf != null) {
                        wavRaf.close();
                    }
                    if (wavOs != null) {
                        wavOs.close();
                    }
                    if (outputStream != null) {
                        outputStream.close();
                    }
                } catch (IOException e) {
                    Log.e(TAG, "close failed", e);
                }
            }
            if (audioRecord.getRecordingState() == RECORDSTATE_RECORDING) {
                audioRecord.stop();
            }
            audioRecord.release();
            Log.d(TAG, "record finish");
        }

        public void stop() {
            if (isRecording.get()) {
                isRecording.set(false);
            }
        }
    }
}
