package com.cityfreqs.audiodsp;

import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;

public class AndroidMicControl implements AudioProcessor {
	
	//TODO
	// more controls for various mic input gain control etc	
	private final AndroidMicHandler handler;
	private double gain; // 1 == no change, >1 == change
	private int overlap;
	private float newValue;
	
	public AndroidMicControl(AndroidMicHandler handler) {
		this.handler = handler;
		// default this, till we implement properly
		gain = 1.5;
	}
	
	public void setGain(double gain) {
		this.gain = gain;
	}
	
	public double getGain() {
		return gain;
	}
	
	@Override
	public boolean process(AudioEvent audioEvent) {
		float[] audioFloatBuffer = audioEvent.getFloatBuffer();
		for (overlap = audioEvent.getOverlap(); overlap < audioFloatBuffer.length ; overlap++) {
			newValue = (float)(audioFloatBuffer[overlap] * gain);
			if(newValue > 1.0f) {
				newValue = 1.0f;
			} else if(newValue < -1.0f) {
				newValue = -1.0f;
			}
			audioFloatBuffer[overlap] = newValue;
		}
		
		handler.handleAudio(audioEvent);
		return true;
	}

	@Override
	public void processingFinished() {
		// TODO Auto-generated method stub
		
	}

}
