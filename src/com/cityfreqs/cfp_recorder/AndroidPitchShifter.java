package com.cityfreqs.cfp_recorder;

import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.util.fft.FFT;

public class AndroidPitchShifter implements AudioProcessor {
	private static final String TAG = "CFP_Recorder-APS";
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
	private long overSampling;
	private int stepSize;
	private double expected;
	private int sizeDiv2;
	
	// processing loop vars:
	private double window;
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
	
	
	public AndroidPitchShifter(double factor, double sampleRate, int bufferSize, int overlap) {
		// ref: http://downloads.dspdimension.com/smbPitchShift.cpp
		// ref: http://blogs.zynaptiq.com/bernsee/pitch-shifting-using-the-ft/

		//TODO
		// pitchShifter staccato effect
		// read: http://www.ni.com/white-paper/4844/en/
		
		pitchShiftRatio = factor;
		size = bufferSize; // 4096
		this.sampleRate = sampleRate;
		
		//int overSampling = bufferSize - bufferSize / 4; // 75% overlap (3072)		
		overSampling = size / (size - overlap); // 4
		stepSize = (int)(size / overSampling); // 1024
		
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
		newMagnitudes = new float[sizeDiv2];
	// analysis
		// this may not be the most efficient:
		//audioIn = audioEvent.getFloatBuffer().clone();
		
		System.arraycopy(audioEvent.getFloatBuffer(), 0, audioIn, 0, size);
	
		for (int i = 0; i < size; i++) {
			// this creates a square wave...
			window = .5 * Math.cos(2. * Math.PI * (double)i / (double)size) + .5;
			fftData[i] = (float)(audioIn[i] * window);
		}
		
		
		/*
		 * 
		 * 
		 */
		
/*		
		// set up a loop here to account for silence at beginning
		
		for (int i = 0; i < size; i++) {
			// this creates a square wave...
			window = .5 * Math.cos(2.f * Math.PI * (double)i / (double)size) + .5;
			//fftData[i] = (float)(audioIn[i] * window);
			// outputAccumulator[size * 2]
			//outputAccumulator[i] += 4000.f * window * fftData[i] / (sizeDiv2 * overSampling);
			//outputAccumulator[2 * i] = (float)(audioIn[i] * window);
			//outputAccumulator[2 * i + 1] = 0.f;
			//outputAccumulator[i] = (float)(audioIn[i] * window);
			outputAccumulator[i] += 4000.*window*audioIn[i]/(size*overSampling);
		}
		
		// arraycopy(Object src, int srcPos, Object dest, int destPos, int length)	
		System.arraycopy(outputAccumulator, stepSize, outputAccumulator, 0, size);
		

		audioEvent.setFloatBuffer(outputAccumulator);
		//audioEvent.setOverlap(0);
		//dispatcher.setStepSizeAndOverlap(size, 0);

		return true;
*/		
		/*
		 * 
		 * 
		 */
		
		
		//fourier transform audio
		fft.forwardTransform(fftData);
		// calc magnitudes and phase
		fft.powerAndPhaseFromFFT(fftData, currentMagnitudes, currentPhase);
		// distance in Hz between fft bins
		freqPerBin = (float)(sampleRate / (float)size);
		
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
		for (int i = sizeDiv2 + 2; i < size; i++) {
			newFFTData[i] = 0.f;
		}		
		
		// inverse transform
		fft.backwardsTransform(newFFTData);
		
		// windowing, add output to accumulator
		// should have real, imaginary interleaved...
		// we string our overlapping frames together at the same choice of stride to get back our pitch shifted signal.
		
		/*
		 After de-interlacing the [re, im] array, windowing and rescaling we put the data into the output queue
		 to make sure we have as much output data as we have input data. 
		 The global I/O delay is inFifoLatency samples 
		 (which means that the start of your output will contain that many samples of silence!) – 
		 this has to be taken into account when we write the data out to a file.		
		*/
		
		for (int i = 0; i < size; i++) {
			// this also creates a square wave
			window = -.5 * Math.cos(2.f * Math.PI * (double)i / (double)size) + .5;
			// why 4000? factor for audible?
			// outputAccumulator[size * 2]
			
			// eg: output[i] += 4000 * 0.9 * 0.4 / (2048 * 4)
			// 1440 / 8192 = 0.175...
			
			//outputAccumulator[i] += (float) (4000.f * window * newFFTData[i] / (sizeDiv2 * overSampling);	
			outputAccumulator[i] += (float) (newFFTData[i] * window);
		}
		
		// arraycopy(Object src, int srcPos, Object dest, int destPos, int length)	
		// this shifts all elements to the left by stepSize (1024), sizeDiv2(2048), etc,
		// reducing it only shortens the audio block size, stutter still remains
		System.arraycopy(outputAccumulator, stepSize, outputAccumulator, 0, size);
		audioEvent.setFloatBuffer(outputAccumulator);
		
		return true;
	}
	
	@Override
	public void processingFinished() {
		//
	}

}