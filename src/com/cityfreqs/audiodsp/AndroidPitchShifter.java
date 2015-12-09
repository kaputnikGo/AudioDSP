package com.cityfreqs.audiodsp;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.util.fft.FFT;

public class AndroidPitchShifter implements AudioProcessor {
	private static final String TAG = "AudioDSP-APS";
	private final FFT fft;
	private final int size;
	private final float[] currentMagnitudes;
	private final float[] currentPhase;
	private final float[] currentFrequencies;
	private final float[] outputAccumulator;
	private final float[] summedPhase;
	
	private float[] previousPhase;
	private double pitchShiftRatio = 0.5; // default
	private final double sampleRate;
	private final AudioDispatcher dispatcher;
	private long overSampling;
	private int overlap;
	private int stepSize;
	private double expected;
	private int sizeDiv2;
	
	// processing loop vars:
	private float window;
	private float freqPerBin;
	private float phase;
	private double tmp;
	private long qpd;
	private int index;
	private float magn;
	
	private float[] newMagnitudes;
	private float[] newFrequencies;
	private float[] audioIn;
	private float[] fftData;
	private float[] newFFTData;
	
	
	public AndroidPitchShifter(AudioDispatcher dispatcher, double factor, double sampleRate, int bufferSize, int overlap) {
		// ref: http://downloads.dspdimension.com/smbPitchShift.cpp
		// ref: http://blogs.zynaptiq.com/bernsee/pitch-shifting-using-the-ft/
		
		// possibly: a reduction of the FFT size as well as a larger step size.
		
		pitchShiftRatio = factor;
		size = bufferSize;
		this.sampleRate = sampleRate;
		this.dispatcher = dispatcher;
		this.overlap = overlap; // 3072
		overSampling = size / (size - overlap); // == 4
		stepSize = size / overlap;
		//expected = 2.0 * Math.PI * (double)(size - this.overlap) / (double)size;
		expected = 2.0 * Math.PI * (double)stepSize / (double)size;
		fft = new FFT(size);
		
		sizeDiv2 = size / 2;			
		currentMagnitudes = new float[sizeDiv2];
		currentFrequencies = new float[sizeDiv2];
		currentPhase = new float[sizeDiv2];
		previousPhase = new float[sizeDiv2];
		summedPhase = new float[sizeDiv2];
		outputAccumulator = new float[size * 2];
		
		newFrequencies = new float[sizeDiv2];
		audioIn = new float[size];
		fftData = new float[size];
		newFFTData = new float[size];
	}
	
	public void setPitchShiftFactor(double newPitchShiftFactor) {
		this.pitchShiftRatio = newPitchShiftFactor;
	}
	
	@Override
	public boolean process(AudioEvent audioEvent) {		
	// need this in process()
		newMagnitudes = new float[sizeDiv2];
	// analysis
		// need a buffer to fill first 
		// then start processing, to account for latency?
		// audioEvent data encoded in floats from -1.0 to 1.0.
		
		audioIn = audioEvent.getFloatBuffer().clone();
			
		for (int i = 0; i < size; i++ ) {
			window = (float)(-0.5f * Math.cos(2.f * Math.PI * (double)i / (double)size) + 0.5f);
			fftData[i] = audioIn[i] * window;
			//fftData[i + size] = audioIn[i] * window;
		}
					
		// arraycopy(Object src, int srcPos, Object dst, int dstPos, int length)
		//System.arraycopy(fftData, 0, fftData, 0, sizeDiv2);
		/*
		* 
		* 
		*/
		/*
		audioEvent.setFloatBuffer(fftData);
		audioEvent.setOverlap(0);
		dispatcher.setStepSizeAndOverlap(size, 0);

		return true;
		*/
		
		/*
		 * 
		 * 
		 * 
		 */
		
		
		//fourier transform audio
		fft.forwardTransform(fftData);
		// calc magnitudes and phase
		fft.powerAndPhaseFromFFT(fftData, currentMagnitudes, currentPhase);
		// distance in Hz between fft bins
		freqPerBin = (float)(sampleRate / (float)size);
//		
		for (int i = 0; i < sizeDiv2; i++) {
			phase = currentPhase[i];
			// compute phase diff
			tmp = phase - previousPhase[i];
			previousPhase[i] = phase;
			// sub expected diff
			tmp -= (double)i * expected;
			// map delta phase into +/- Pi interval
			qpd = (long)(tmp / Math.PI);
			if (qpd >= 0)
				qpd += qpd&1;
			else
				qpd -= qpd&1;
			tmp -= Math.PI * (double)qpd;
			// get deviation
			tmp = overSampling * tmp / (2.0 * Math.PI);
			// compute k-th partials
			tmp = (double)i * freqPerBin + tmp * freqPerBin;
			// store magnitude and true freq
			currentFrequencies[i] = (float)tmp;
		}
		
	// processing (pitch shifting)
		for (int i = 0; i < sizeDiv2; i++) {
			index = (int)(i * pitchShiftRatio);
			if (index < sizeDiv2) {
				newMagnitudes[index] += currentMagnitudes[i];
				newFrequencies[index] = (float)(currentFrequencies[i] * pitchShiftRatio);
			}
		}
		
	// synthesis
		// here that the stutter is caused?
		for (int i = 0; i < sizeDiv2; i++) {
			magn = newMagnitudes[i];
			tmp = newFrequencies[i];
			// subtract mid freq
			tmp -= (double)i * freqPerBin;
			// get bin deviation from freq deviation
			tmp /= freqPerBin;
			// overSampling
			tmp = 2.0 * Math.PI * tmp / overSampling;
			// add overlap phase
			tmp += (double)i * expected;
			// accum delta phase to get bin phase
			summedPhase[i] += tmp;
			phase = summedPhase[i];
			// get real and imaginary part, re-interleave
			newFFTData[2 * i] = (float)(magn * Math.cos(phase));
			newFFTData[2 * i + 1] = (float)(magn * Math.sin(phase));
		}
		
		// zero negative freqs
		for (int i = sizeDiv2 + 2; i <size; i++) {
			newFFTData[i] = 0.f;
		}		
		
		// inverse transform
		fft.backwardsTransform(newFFTData);
		
		// windowing, add output to accumulator
		for (int i = 0; i < size; i++) {
			window = (float)(-0.5f * Math.cos(2.0 * Math.PI * (double)i / (double)size) + 0.5f);
			// why 4000?
			outputAccumulator[i] += 4000 * window * newFFTData[i] / (float)(size * overSampling);
			outputAccumulator[i] += 2 * window * (newFFTData[i] * 2);// / (float)(sizeDiv2 * overSampling);
			//outputAccumulator[i * 2] += 2 * window * (newFFTData[i] * 2);// / (float)(sizeDiv2 * overSampling);
		}
			
		// memmove allows byte copies, overlapping
		// memmove ( void * destination, const void * source, size_t num );
		// memmove(gOutputAccum, gOutputAccum+stepSize, fftFrameSize*sizeof(float));
		
		// it appears to be getting only half the output audio - hence the stutter
		// outputAccumulator is [size * 2] and needs to be outputted at [size], so half the original
		
		// without this arraycopy, audio recieved loops indefinitely, still stutters		
		// arraycopy(Object src, int srcPos, Object dst, int dstPos, int length)
		//System.arraycopy(outputAccumulator, sizeDiv2, outputAccumulator, 0, size);
		System.arraycopy(outputAccumulator, sizeDiv2, outputAccumulator, 0, size);

		audioEvent.setFloatBuffer(outputAccumulator);
		audioEvent.setOverlap(0);
		dispatcher.setStepSizeAndOverlap(size, 0);

		return true;
		
	}
	
	@Override
	public void processingFinished() {
		//
	}

}