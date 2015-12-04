package com.cityfreqs.audiodsp;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;

public class AndroidAudioOut implements AudioProcessor {
    public static final int DEFAULT_STREAM_TYPE = AudioManager.STREAM_MUSIC;
    private static final String TAG = "AudioDSP-AndroidAudioOut";
    private final AudioTrack audioTrack;

    public AndroidAudioOut(TarsosDSPAudioFormat audioFormat, int bufferSizeInSamples, int streamType) {
    	if (audioFormat.getChannels() != 1) {
            throw new IllegalArgumentException("TarsosDSP only supports mono audio channel count: " 
            		+ audioFormat.getChannels());
        }

        int sampleRate = (int) audioFormat.getSampleRate();
        int bufferSizeInBytes = bufferSizeInSamples * audioFormat.getSampleSizeInBits() / 8;

        int minBufferSizeInBytes = AudioTrack.getMinBufferSize(
        		sampleRate, 
        		AudioFormat.CHANNEL_OUT_DEFAULT, 
        		AudioFormat.ENCODING_PCM_16BIT);
        
        if(minBufferSizeInBytes > bufferSizeInBytes){
            throw new IllegalArgumentException("The buffer size should be at least " 
            		+ (minBufferSizeInBytes / audioFormat.getSampleSizeInBits() / 8) 
            		+ " (samples) according to  AudioTrack.getMinBufferSize().");
        }

        audioTrack = new AudioTrack(
        		streamType, 
        		sampleRate, 
        		AudioFormat.CHANNEL_OUT_DEFAULT, 
        		AudioFormat.ENCODING_PCM_16BIT, 
        		bufferSizeInBytes,AudioTrack.MODE_STREAM);

        audioTrack.play();
    }


    public AndroidAudioOut(TarsosDSPAudioFormat audioFormat) {
        this(audioFormat, 4096, DEFAULT_STREAM_TYPE);
    }

    @Override
    public boolean process(AudioEvent audioEvent) {
        int overlapInSamples = audioEvent.getOverlap();
        int stepSizeInSamples = audioEvent.getBufferSize() - overlapInSamples;
        byte[] byteBuffer = audioEvent.getByteBuffer();

        //int ret = audioTrack.write(audioEvent.getFloatBuffer(),overlapInSamples,stepSizeInSamples,AudioTrack.WRITE_BLOCKING);
        int ret = audioTrack.write(byteBuffer,overlapInSamples * 2,stepSizeInSamples * 2);
        if (ret < 0) {
            Log.e(TAG, "AudioTrack.write returned error code " + ret);
        }
        return true;
    }

    @Override
    public void processingFinished() {
        audioTrack.flush();
        audioTrack.stop();
        audioTrack.release();
    }
	
}