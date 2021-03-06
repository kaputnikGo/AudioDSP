package com.cityfreqs.cfp_recorder;

import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;
import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.io.PipedAudioStream;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;
import be.tarsos.dsp.io.TarsosDSPAudioInputStream;
import be.tarsos.dsp.io.android.AndroidAudioInputStream;

public class AndroidDispatcherFactory {

	public static AudioDispatcher fromDefaultMicrophone(final int sampleRate, final int audioBufferSize, final int bufferOverlap) {
		int minAudioBufferSize = AudioRecord.getMinBufferSize(sampleRate,
				android.media.AudioFormat.CHANNEL_IN_MONO,
				android.media.AudioFormat.ENCODING_PCM_16BIT);
		
		int minAudioBufferSizeInSamples =  minAudioBufferSize / 2;
		if (minAudioBufferSizeInSamples <= audioBufferSize ) {
			AudioRecord audioInputStream = new AudioRecord(
				MediaRecorder.AudioSource.MIC, 
				sampleRate,
				android.media.AudioFormat.CHANNEL_IN_MONO,
				android.media.AudioFormat.ENCODING_PCM_16BIT,
				audioBufferSize * 2);

			TarsosDSPAudioFormat format = new TarsosDSPAudioFormat(sampleRate, 16, 1, true, false);
			
			TarsosDSPAudioInputStream audioStream = new AndroidAudioInputStream(audioInputStream, format);
			//start recording ! Opens the stream.
			audioInputStream.startRecording();
			return new AudioDispatcher(audioStream, audioBufferSize, bufferOverlap);
		} 
		else {
			throw new IllegalArgumentException("Buffer size too small should be at least " + (minAudioBufferSize * 2));
		}
	}

	public static AudioDispatcher fromUsbMicrophone(final int sampleRate, final int audioBufferSize, final int bufferOverlap) {
		int minAudioBufferSize = AudioRecord.getMinBufferSize(sampleRate,
				android.media.AudioFormat.CHANNEL_IN_DEFAULT,
				android.media.AudioFormat.ENCODING_PCM_16BIT);
		
		int minAudioBufferSizeInSamples =  minAudioBufferSize / 2;
		if (minAudioBufferSizeInSamples <= audioBufferSize ) {
			AudioRecord audioInputStream = new AudioRecord(
				MediaRecorder.AudioSource.DEFAULT,
				//  .DEFAULT or .MIC or .VOICE_RECOGNITION or .CAMCORDER  
				// -- can depend on USB device implementation
				
				sampleRate,
				android.media.AudioFormat.CHANNEL_IN_DEFAULT,
				android.media.AudioFormat.ENCODING_PCM_16BIT,
				audioBufferSize * 2);

			TarsosDSPAudioFormat format = new TarsosDSPAudioFormat(sampleRate, 16, 1, true, false);
			
			TarsosDSPAudioInputStream audioStream = new AndroidAudioInputStream(audioInputStream, format);
			//start recording ! Opens the stream.
			audioInputStream.startRecording();
			//TODO
			//DEBUGGING
			Log.d("RECORDER", "Source: " + audioInputStream.getAudioSource());
			return new AudioDispatcher(audioStream, audioBufferSize, bufferOverlap);
		} 
		else {
			throw new IllegalArgumentException("Buffer size too small should be at least " + (minAudioBufferSize * 2));
		}
	}
	/**
	 * Create a stream from a piped sub process and use that to create a new
	 * {@link AudioDispatcher} The sub-process writes a WAV-header and
	 * PCM-samples to standard out. The header is ignored and the PCM samples
	 * are are captured and interpreted. Examples of executables that can
	 * convert audio in any format and write to stdout are ffmpeg and avconv.
	 *
	 * @param source
	 *            The file or stream to capture.
	 * @param targetSampleRate
	 *            The target sample rate.
	 * @param audioBufferSize
	 *            The number of samples used in the buffer.
	 * @param bufferOverlap
	 * 			  The number of samples to overlap the current and previous buffer.
	 * @return A new audioprocessor.
	 */
	public static AudioDispatcher fromPipe(final String source,final int targetSampleRate, final int audioBufferSize,final int bufferOverlap){
		PipedAudioStream f = new PipedAudioStream(source);
		TarsosDSPAudioInputStream audioStream = f.getMonoStream(targetSampleRate);
		return new AudioDispatcher(audioStream, audioBufferSize, bufferOverlap);
	}
}
