package com.cityfreqs.cfp_recorder;

import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;

public class ThresholdGate implements AudioProcessor {
	public static final double DEFAULT_THRESHOLD = -70.0; //db	
	private double threshold;
	private boolean silenceEnabled;
	private boolean isSilent;
	private double currentSPL = 0;

	public ThresholdGate(){
		this(DEFAULT_THRESHOLD, false);
	}
	
	//Create a new silence detector with a defined threshold.
	public ThresholdGate(double silenceThreshold, boolean silenceEnabled){
		this.threshold = silenceThreshold;
		this.silenceEnabled = silenceEnabled;
	}

	public double currentSPL() {
		return currentSPL;
	}
	
	public void setThreshold(double threshold) {
		this.threshold = threshold;
	}
	
	//Calculates the local (linear) energy of an audio buffer.
	private double localEnergy(final float[] buffer) {
		double power = 0.0D;
		for (float element : buffer) {
			power += element * element;
		}
		return power;
	}

	//Returns the dBSPL for a buffer.
	private double soundPressureLevel(final float[] buffer) {
		double value = Math.pow(localEnergy(buffer), 0.5);
		value = value / buffer.length;
		return linearToDecibel(value);
	}

	//Converts a linear to a dB value.
	private double linearToDecibel(final double value) {
		return 20.0 * Math.log10(value);
	}

	//Checks if the dBSPL level in the buffer falls below a certain threshold.
	private boolean isSilence(final float[] buffer) {
		currentSPL = soundPressureLevel(buffer);
		return currentSPL < threshold;
	}


	@Override
	public boolean process(AudioEvent audioEvent) {
		isSilent = isSilence(audioEvent.getFloatBuffer());
		if (silenceEnabled && isSilent) {
			audioEvent.clearFloatBuffer();
		}
		return true;
	}


	@Override
	public void processingFinished() {
		//
	}	
}