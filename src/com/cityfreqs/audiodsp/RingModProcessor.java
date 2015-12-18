package com.cityfreqs.audiodsp;

import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;

public class RingModProcessor implements AudioProcessor {
	// attempt to frequency shift the incoming audio event
	//protected static final int SAMPLE_RATE = 16 * 1024;
	private byte[] input;
	private byte[] carrier;
	private byte[] output;
	double period;
	double angle;
	
	
	public RingModProcessor(int sampleRate, double modFreq) {
		// create the carrier sine wave
		period = (double)sampleRate / modFreq;
	}
	
	@Override
	public boolean process(AudioEvent audioEvent) {
		// for frequency shift:
		// f - input signal
		// g - carrier
		// h - output
		// h[i] = f[i] * g[i], for all i
		
		input = audioEvent.getByteBuffer();
		output = new byte[input.length];
				
		// needs to be a 10000hZ sine wave
		carrier = new byte[input.length];
				
		for (int i = 0; i < output.length; i++) {
			angle = 2.0 * Math.PI * i / period;
	        carrier[i] = (byte)(Math.sin(angle) * 127f);
			output[i] = (byte) (input[i] * carrier[i]);
		}
		
		return true;
	}

	@Override
	public void processingFinished() {
		// TODO Auto-generated method stub
		
	}	
}