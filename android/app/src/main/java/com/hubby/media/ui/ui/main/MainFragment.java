package com.hubby.media.ui.ui.main;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;

import com.hubby.audiorecord.AudioRecorder;
import com.hubby.audiorecord.AudioTracker;
import com.hubby.audiorecord.exception.PermissionLessException;
import com.hubby.media.R;

import java.io.FileNotFoundException;


public class MainFragment extends Fragment implements View.OnClickListener, AudioTracker.ProcessListener {

    private static final String TAG = "MainFragment";
    private MainViewModel mViewModel;

    private AudioRecorder mAudioRecorder;

    private AudioTracker mAudioTracker;

    private Handler mMainHandler;

    public static MainFragment newInstance() {
        return new MainFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mViewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        // TODO: Use the ViewModel
        mAudioRecorder = new AudioRecorder(requireActivity(), 44100,
                AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT,
                null, null);
        if (!checkPermission(AudioRecorder.PERMISSION_REQUIRED)) {
            ActivityCompat.requestPermissions(requireActivity(), AudioRecorder.PERMISSION_REQUIRED, 100);
        }
        mAudioTracker = new AudioTracker(44100, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        mMainHandler = new Handler();
    }

    private boolean checkPermission(String... permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(requireActivity(), permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    ProgressBar mProgressBar;
    @SuppressLint("MissingInflatedId")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_main, container, false);
        Button startRecord = root.findViewById(R.id.start_record);
        Button stopRecord = root.findViewById(R.id.stop_record);
        Button startPlay = root.findViewById(R.id.start_play);
        Button stopPlay = root.findViewById(R.id.stop_play);
        mProgressBar =root.findViewById(R.id.play_process);
        startRecord.setOnClickListener(this);
        stopRecord.setOnClickListener(this);
        startPlay.setOnClickListener(this);
        stopPlay.setOnClickListener(this);
        return root;
    }

    @Override
    public void onClick(View v) {
        int viewId = v.getId();
        if (viewId == R.id.start_record) {
            try {
                mAudioRecorder.startRecord();
            } catch (PermissionLessException e) {
                Log.e(TAG, "startRecord error! errorInfo = " + e);
            }
        } else if (viewId == R.id.stop_record) {
            mAudioRecorder.stopRecord();
        } else if (viewId == R.id.start_play) {
            String tempFilePath = mAudioRecorder.getTempFilePath();
            try {
                mAudioTracker.startPlay(tempFilePath,this,mMainHandler);
            } catch (FileNotFoundException e) {
                throw new RuntimeException("file not found:"+tempFilePath, e);
            }
        } else if (viewId == R.id.stop_play) {
            mAudioTracker.stop();
        }
    }

    @Override
    public void onInit(int totalSecond) {
        mProgressBar.setMax(totalSecond);
    }

    @Override
    public void onPlayTime(int currentPlayTime) {
        mProgressBar.setProgress(currentPlayTime);
    }
}