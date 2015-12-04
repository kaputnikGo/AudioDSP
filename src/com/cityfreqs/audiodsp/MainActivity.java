package com.cityfreqs.audiodsp;

import java.util.HashMap;
import java.util.Iterator;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.filters.HighPass;
import be.tarsos.dsp.filters.IIRFilter;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;
import be.tarsos.dsp.io.android.AndroidAudioPlayer;
import be.tarsos.dsp.io.android.AudioDispatcherFactory;
import be.tarsos.dsp.pitch.PitchDetectionHandler;
import be.tarsos.dsp.pitch.PitchDetectionResult;
import be.tarsos.dsp.pitch.PitchProcessor;
import be.tarsos.dsp.pitch.PitchProcessor.PitchEstimationAlgorithm;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder.AudioSource;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

public class MainActivity extends Activity implements OnSeekBarChangeListener {
	// NOTES:
	// https://github.com/JorenSix/TarsosDSP
	// http://0110.be/releases/TarsosDSP/TarsosDSP-2.3/TarsosDSP-2.3-Documentation/
	
	private static final String TAG = "AudioDSP";
	private static final boolean DEBUG = true;
	
	private Thread audioThread;
	private AudioDispatcher dispatcher;
	private IIRFilter hipassFilter;
	private PitchDetectionHandler pdh;
	private AudioProcessor pitchProcessor;
	private AudioProcessor thresholdGate;
	private TarsosDSPAudioFormat tarsosAudioFormat;
	private AndroidAudioOut androidAudioOut;
	private AudioManager audioManager;
	
	private AudioVisualiserView audioVisualiserView;
	private static TextView debugText;
	private SeekBar hiFreqSeekBar;
	private TextView hifreqText;
	private TextView pitchText;
	private TextView gateText;
	private int thresh;
	
	private static final float THRESHOLD_DB = -70; // decibels
	
	private int sampleRate;
	private int bufferSize;
	private float hiFrequency;
	private static final int FREQ_MIN = 0;
	private static final int FREQ_MAX = 20000;
	private static final int FREQ_STEP = 500;
	
	private static final int EMU_BUFFER = 640;
	private static final int DEF_BUFFER = 1024;
	private static final int MAX_BUFFER = 2048;
	private static final int SAM5_BUFFER = 7168;
	private int bufferOverlap;
	
	private static final int RATE_44 = 44100;
	private static final int RATE_22 = 22050;
	private static final int RATE_16 = 16000;
	private static final int RATE_11 = 11025;
	private static final int RATE_8 = 8000;
	private static final int[] SAMPLE_RATES = new int[] { RATE_44, RATE_22, RATE_16, RATE_11, RATE_8 };		
	// USB
	private DeviceContainer deviceContainer;
	private UsbManager usbManager;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		audioVisualiserView = (AudioVisualiserView) findViewById(R.id.audio_visualiser_view);
		
		debugText = (TextView) findViewById(R.id.debug_text);
		debugText.setMovementMethod(new ScrollingMovementMethod());
		
		hiFreqSeekBar = (SeekBar) findViewById(R.id.hi_freq_seek);
		hiFreqSeekBar.setMax((FREQ_MAX - FREQ_MIN) / FREQ_STEP);
		hiFreqSeekBar.setProgress(10000);
		hiFreqSeekBar.setOnSeekBarChangeListener(this);
		hifreqText = (TextView) findViewById(R.id.hifreq_text);
		
		pitchText = (TextView) findViewById(R.id.pitch_text);
		gateText = (TextView) findViewById(R.id.gate_text);
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		if (prepareAudio()) {
			dispatcherEnable();
		}
		else {
			logger(TAG, "prepare audio fail.");
		}
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		if (audioThread != null && audioThread.isAlive()) {
			audioThread.interrupt();
		}
		if (dispatcher != null) {
			dispatcher.stop();
			dispatcher = null;
		}
	}

/*
* Interface controls   
*/	
	@Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
		hiFrequency = FREQ_MIN + (progress * FREQ_STEP);
		hifreqText.setText(Float.toString(hiFrequency));
		hipassFilter.setFrequency(hiFrequency);
    }
    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
    	
    }
    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
    	logger(TAG, "hi cut: " + hiFrequency);
 
    }
    
	private void updatePitch(float pitch) {
		pitchText.setText("pitch in Hz: " + pitch);
		debugText.setGravity(Gravity.NO_GRAVITY); 
	}
	
	private void updateGate(double level) {
		thresh = (int)level; 
		gateText.setText("Threshold gate: " + thresh);
	}	

/*
* options settings 
*/	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	

/*
* Audio processing   
*/	
	private boolean prepareAudio() {
		logger(TAG, "prepareAudio...");				
		if (determineAudioType()) {
			// wait for it...
		}
		else {
			logger(TAG, "Error determining audio type.");
		}
		// defaults
		sampleRate = RATE_44;
		bufferSize = SAM5_BUFFER;
		logger(TAG, "bufferSize: " + bufferSize);
		
		// set to zero
		hiFrequency = 0;
		bufferOverlap = 0;
		thresh = 0;
		return true;
	}
	
	private void dispatcherEnable() {
		dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(sampleRate, bufferSize, bufferOverlap);
		
		if (hipassFilterProcess()) {
			dispatcher.addAudioProcessor(hipassFilter);
			logger(TAG, "hipassFilter added.");
		}
		else {
			logger(TAG, "highpassFilter failed to load.");
		}
		
		if (pitchInProcess()) {
			dispatcher.addAudioProcessor(pitchProcessor);
			logger(TAG, "pitchProcessor added.");
		}
		else {
			logger(TAG, "pitch processor failed to load.");
		}
		
		if (thresholdGateProcess()) {
			dispatcher.addAudioProcessor(thresholdGate);
			logger(TAG, "threshold gate added.");
		}
		else {
			logger(TAG, "threshold gate failed to load.");
		}
		
		if (androidAudioOutput()) {
			dispatcher.addAudioProcessor(androidAudioOut);
			logger(TAG, "android output added.");
		}
		else {
			logger(TAG, "android output failed to load.");
		}

		audioThread = new Thread(dispatcher, "Audio Dispatcher");
		audioThread.start();
		logger(TAG, "audioThread started.");
		
	}
	
	private boolean hipassFilterProcess() {
		float frequency = 10000.0f;		
		hipassFilter = new HighPass(frequency, sampleRate);
		
		if (hipassFilter != null) 
			return true;		
		else 
			return false;		
	}

	private boolean pitchInProcess() {		
		pdh = new PitchDetectionHandler() {
			@Override
			public void handlePitch(PitchDetectionResult result, AudioEvent audioEvent) {
				final float pitchInHz = result.getPitch();

				final byte[] byteBuffer = audioEvent.getByteBuffer();
			
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						updatePitch(pitchInHz);
						audioVisualiserView.updateVisualiser(byteBuffer);
					}
				});
			}
		};
		pitchProcessor = new PitchProcessor(PitchEstimationAlgorithm.FFT_YIN, sampleRate, bufferSize, pdh);
		
		if (pitchProcessor != null) 
			return true;
		else 
			return false;
	}
	
	private boolean thresholdGateProcess() {
		thresholdGate = new AudioProcessor() {
			@Override 
			public boolean process(AudioEvent audioEvent) {
				float[] buffer = audioEvent.getFloatBuffer();
				final double level = soundPressureLevel(buffer);
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						if (level > THRESHOLD_DB) {
							// no gating yet...
							logger(TAG, "threshold crossed.");
						}
						updateGate(level);
					}
				});
				return true;
			}
			
			@Override
			public void processingFinished() {
				// nothing
			}			
		};
		if (thresholdGate != null) 
			return true;
		else 
			return false;
	}
	
	private boolean androidAudioOutput() {
		// need to check headphone is plugged in
		audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);		
		
		// String audioManager.getProperty(android.media.property.SUPPORT_SPEAKER_NEAR_ULTRASOUND);
		// : android.media.property.SUPPORT_MIC_NEAR_ULTRASOUND
		// : android.media.property.SUPPORT_SPEAKER_NEAR_ULTRASOUND
		
		if (audioManager.isSpeakerphoneOn()) {
			// do not allow output at the moment
			logger(TAG, "headphones needed!");
			return false;
		}
		else {
			logger(TAG, "headphones in use.");
		}
		
		tarsosAudioFormat = new TarsosDSPAudioFormat(
    			RATE_44, 
    			AudioFormat.ENCODING_PCM_16BIT, 
    			AudioFormat.CHANNEL_OUT_DEFAULT, 
    			false, 
    			false);
    	
    	// android channel_out_mono == 4
    	
		androidAudioOut = new AndroidAudioOut(
				tarsosAudioFormat, 
				bufferSize, 
				AudioManager.STREAM_MUSIC);
		
		if (androidAudioOut != null) 
			return true;
		else 
			return false;
	}
	
/*
* USB device   
*/	
	// http://developer.android.com/guide/topics/connectivity/usb/host.html
	// SDR device driver: http://sdr.osmocom.org/trac/
	
	private boolean getIntentUsbDevice(Intent intent) {
		// the device_filter.xml has a hardcoded usb device declaration
		// update it to the SDR when it gets here...
		deviceContainer = new DeviceContainer();
		deviceContainer.setUsbDevice((UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE));
		return (deviceContainer.hasDevice() != false);
	}
	
	private void populateUsbDevices() {
		// the device_filter.xml has a hardcoded usb device declaration
		// update it to the SDR when it gets here...
		usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
		HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
		Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();

		int i = 0;
		DeviceContainer[] devices = new DeviceContainer[deviceList.size()];
		
		while(deviceIterator.hasNext()) {			
			UsbDevice device = deviceIterator.next();
			devices[i] = new DeviceContainer(device);
			i++;
		}
	}

	
/*
* Utilities   
*/ 		
	private double soundPressureLevel(final float[] buffer) {
		double power = 0.0D;
		for (float element : buffer) {
			power += element * element;
		}
		double value = Math.pow(power, 0.5) / buffer.length;
		return 20.0 * Math.log10(value);
	}
	
	private boolean determineAudioType() {
		// getAudioRecord object, query it, release it
		AudioRecord candidate = getAudioRecord();
		if (candidate != null) {
			candidate.release();
			candidate = null;
			return true;
		}
		else {
			logger(TAG, "audioRecord candidate is null.");
			return false;
		}
	}	
	// only for querying a device, 
	// TarsosDSP sets its own AudioRecord object
	private AudioRecord getAudioRecord() {
	    for (int rate : SAMPLE_RATES) {
	        for (short audioFormat : new short[] { 
	        		AudioFormat.ENCODING_PCM_8BIT, 
	        		AudioFormat.ENCODING_PCM_16BIT }) {
	        	
	            for (short channelConfig : new short[] { 
	            		AudioFormat.CHANNEL_IN_MONO, 
	            		AudioFormat.CHANNEL_IN_STEREO }) {
	                try {
	                    logger(TAG, "Try rate " + rate + "Hz, bits: " + audioFormat + ", channel: "+ channelConfig);
	                    
	                    int bufferSize = AudioRecord.getMinBufferSize(rate, channelConfig, audioFormat);
	                    if (bufferSize != AudioRecord.ERROR_BAD_VALUE) {
	                        // check if we can instantiate and have a success
	                        AudioRecord recorder = new AudioRecord(
	                        		AudioSource.DEFAULT, 
	                        		rate, 
	                        		channelConfig, 
	                        		audioFormat, 
	                        		bufferSize);

	                        if (recorder.getState() == AudioRecord.STATE_INITIALIZED) {
	                        	logger(TAG, "found, rate: " + rate + ", buff: " + bufferSize);
	                            return recorder;
	                        }
	                    }
	                } catch (Exception e) {
	                    logger(TAG, "Rate: " + rate + "Exception, keep trying, e:" + e.toString());
	                }
	            }
	        }
	    }
	    return null;
	}

    public static void logger(String tag, String message) {
    	if (DEBUG) {
    		debugText.append("\n" + tag + ": " + message);
    		Log.d(tag, message);
    	}    	
    }
}
