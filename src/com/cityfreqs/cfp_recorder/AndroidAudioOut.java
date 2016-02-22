package com.cityfreqs.cfp_recorder;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;

public class AndroidAudioOut implements AudioProcessor {
    public static final int DEFAULT_STREAM_TYPE = AudioManager.STREAM_MUSIC;
    private static final String TAG = "CFP_R-output";
    private final AudioTrack audioTrack;
    private int audioReturn;
    private int overlap;
    private int stepSize;
    private int bufferSize;

    public AndroidAudioOut(TarsosDSPAudioFormat audioFormat, int bufferSize, int streamType) {
    	//TODO
    	// make this a graceful exit for the app - not a crash to desktop
    	if (audioFormat.getChannels() != 1) {
            throw new IllegalArgumentException("TarsosDSP only supports mono audio channel count: " 
            		+ audioFormat.getChannels());
        }
    	this.bufferSize = bufferSize;
        audioTrack = new AudioTrack(
        		streamType,
        		(int)audioFormat.getSampleRate(),
        		audioFormat.getChannels(),
        		AudioFormat.ENCODING_PCM_16BIT,
        		bufferSize,
        		AudioTrack.MODE_STREAM);
        
        audioTrack.play();
    }


    public AndroidAudioOut(TarsosDSPAudioFormat audioFormat) {
    	// not this on android...
        this(audioFormat, 4096, DEFAULT_STREAM_TYPE);
    }

    @Override
    public boolean process(AudioEvent audioEvent) {
        overlap = audioEvent.getOverlap();
        stepSize = bufferSize - overlap;   	
        // write(byte[] audioData, int offsetInBytes, int sizeInBytes) :: says needs ENCODING_PCM_8BIT
        audioReturn = audioTrack.write(audioEvent.getByteBuffer(), overlap * 2, stepSize * 2);
        if (audioReturn < 0) {
            Log.e(TAG, "AudioTrack.write returned error code " + audioReturn);
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