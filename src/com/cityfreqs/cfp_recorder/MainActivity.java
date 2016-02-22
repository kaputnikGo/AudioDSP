package com.cityfreqs.cfp_recorder;

import java.util.HashMap;
import java.util.Iterator;

import com.cityfreqs.cfp_recorder.R;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.filters.HighPass;
import be.tarsos.dsp.filters.IIRFilter;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder.AudioSource;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

public class MainActivity extends Activity {
	// NOTES:
	// https://github.com/JorenSix/TarsosDSP
	// http://0110.be/releases/TarsosDSP/TarsosDSP-2.3/TarsosDSP-2.3-Documentation/
	
	// String audioManager.getProperty(android.media.property.SUPPORT_SPEAKER_NEAR_ULTRASOUND);
	// : android.media.property.SUPPORT_MIC_NEAR_ULTRASOUND
	// : android.media.property.SUPPORT_SPEAKER_NEAR_ULTRASOUND
	// freq range to seek: 18.5kHz - 20kHz
	
	// add a file write mechanism for candidate 18.5+ grabs
	// manual record as well as gate triggered
	
	// add hpf min to OFF as in the gate method
	
	private static final String TAG = "CFP_R";
	private static final String VERSION = "1.3.0.3";
	private static final boolean DEBUG = true;
	
	private WakeLock wakeLock;
	private PowerManager pm;
	private SharedPreferences sharedPrefs;
	
	private AudioManager audioManager;
	private Thread audioThread;
	private AudioDispatcher dispatcher;
	private IIRFilter hipassFilter;
	private AndroidMicHandler androidMicHandler;
	private AndroidMicControl androidMicControl;
	private ThresholdGate thresholdGate;
	private TarsosDSPAudioFormat tarsosAudioFormat;
	private AndroidAudioOut androidAudioOut;	
	private HeadsetIntentReceiver headsetReceiver;
	
	private AndroidWriteProcessor androidWriteProcessor;
	
	private AudioVisualiserView audioVisualiserView;	
	private static TextView debugText;
	private SeekBar hiFreqSeekBar;
	private TextView hifreqText;
	private TextView gateText;
	private SeekBar gateSeekBar;
	private TextView thresholdText;
	private TextView gainText;
	private TextView timerText;
	private Button recordButton;
	
	private float hiFreqGate;
	private float gate;
	
	private int sampleRate;
	private int bufferSize;
	private int bufferOverlap;
	private int encoding;
	private int channel;
	
	private static final int FREQ_STEP = 500;
	private static final float DEFAULT_THRESHOLD = -80; // decibels
	private static final int RATE_48 = 48000;
	private static final int RATE_44 = 44100;
	private static final int RATE_22 = 22050;
	private static final int RATE_16 = 16000;
	private static final int RATE_11 = 11025;
	private static final int RATE_8 = 8000;
	private static final int[] SAMPLE_RATES = new int[] { RATE_48, RATE_44, RATE_22, RATE_16, RATE_11, RATE_8 };
	private static final int[] POWERS_TWO = new int[] { 512, 1024, 2048, 4096, 8192, 16384 };
	
	private static final int DEFAULT_RATE = RATE_44;
	private static final int DEFAULT_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
	private static final int DEFAULT_CHANNEL = AudioFormat.CHANNEL_OUT_DEFAULT;
	private static final int SAM5_BUFFER = 7680;
	
	private static final int DEFAULT_FREQ = 37;
	private static final int DEFAULT_GATE = 20;
	
	private long startTime;
	private Handler timerHandler;
	private Runnable timerRunnable;
	
	// USB
	private DeviceContainer deviceContainer;
	private UsbManager usbManager;
	private boolean useUSB;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		headsetReceiver = new HeadsetIntentReceiver();
		useUSB = false;
		
		audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
		pm = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
		sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		
		debugText = (TextView) findViewById(R.id.debug_text);
		debugText.setMovementMethod(new ScrollingMovementMethod());
		debugText.setOnClickListener(new TextView.OnClickListener() {
			@Override
			public void onClick(View v) {
				debugText.setGravity(Gravity.NO_GRAVITY);
				debugText.setSoundEffectsEnabled(false); // no further click sounds
			}
			
		});

		thresholdText = (TextView) findViewById(R.id.threshold_text);
		gainText = (TextView) findViewById(R.id.gain_text);
		timerText = (TextView) findViewById(R.id.timer_text);
		audioVisualiserView = (AudioVisualiserView) findViewById(R.id.audio_visualiser_view);		
		
		hifreqText = (TextView) findViewById(R.id.hifreq_text);
		hiFreqSeekBar = (SeekBar) findViewById(R.id.hi_freq_seek);
		hiFreqSeekBar.setMax(42);
		hiFreqSeekBar.setProgress(DEFAULT_FREQ);
		hiFreqSeekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				hiFreqGate = progress * FREQ_STEP;
				updateHiFilter();				
			}
			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
				// 				
			}
			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				//logger(TAG, "hi cut: " + hiFrequency);				
			}
			
		});
		
		gateText = (TextView) findViewById(R.id.gate_text);
		gateText.setText("gate: " + Float.toString(DEFAULT_GATE - 100));
		gateSeekBar = (SeekBar) findViewById(R.id.gate_seek);
		gateSeekBar.setMax(100);
		gateSeekBar.setProgress(DEFAULT_GATE); // -80dB
		gateSeekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				// place within usable range -100 - 0 
				gate = progress - 100;
				updateGate();
				
			}
			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
				//				
			}
			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				//logger(TAG, "gained: " + gain);			
			}
			
		});
		
		// on screen timer
		timerHandler = new Handler();
		timerRunnable = new Runnable() {
			@Override
			public void run() {
				long millis = System.currentTimeMillis() - startTime;
				int seconds = (int)(millis / 1000);
				int minutes = seconds / 60;
				seconds = seconds % 60;
				timerText.setText(String.format("timer - %02d:%02d", minutes, seconds));
				timerHandler.postDelayed(this, 500);
			}
		};
		
		// change the look of this button to indicate state		
		recordButton = (Button) findViewById(R.id.record_button);
		recordButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                toggleRecording();
            }
        });
		
		
		// save settings to sharedPrefs
		SharedPreferences.Editor editor = sharedPrefs.edit();
		editor.putInt("freq", DEFAULT_FREQ);
		editor.putInt("gate", DEFAULT_GATE);
		editor.commit();
	}
	
	@Override
	protected void onResume() {
		super.onResume();
	    IntentFilter filter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
	    registerReceiver(headsetReceiver, filter);
		
		if (wakeLock != null && wakeLock.isHeld()) {
			// do nothing, we are recording...			
		}
		else {
			if (prepareAudio()) {
				dispatcherLoadProcesses();
			}
			else {
				logger(TAG, "prepare audio fail.");
			}
		}
		// need to remind progress bars of max value first..
		hiFreqSeekBar.setMax(42);
		hiFreqSeekBar.setProgress(sharedPrefs.getInt("freq", DEFAULT_FREQ));
		gateSeekBar.setMax(100);
		gateSeekBar.setProgress(sharedPrefs.getInt("gate", DEFAULT_GATE));
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		unregisterReceiver(headsetReceiver);
		if (wakeLock != null && wakeLock.isHeld()) {
			//TODO
			// do nothing, we are recording...
			// this is a fudge until we get background services recording enabled.			
		}		
		else {		
			if (audioThread != null && audioThread.isAlive()) {
				audioThread.interrupt();
			}
			if (dispatcher != null) {
				dispatcher.stop();
				dispatcher = null;
			}			
		}
		timerHandler.removeCallbacks(timerRunnable);
		
		// save settings to sharedPrefs
		SharedPreferences.Editor editor = sharedPrefs.edit();
		editor.putInt("freq", hiFreqSeekBar.getProgress());
		editor.putInt("gate", gateSeekBar.getProgress());
		editor.commit();
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
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
	private void updateHiFilter() {
		hifreqText.setText("hpf: " + Float.toString(hiFreqGate));
		// changed == 0 - 21,000 : step 500		
		if (hipassFilter != null) {
			hipassFilter.setFrequency(hiFreqGate);
		}
	}
	private void updateGate() {
		if (thresholdGate != null) {
			if (gate <= -100.0) {
				// switch gate off, save cycles
				thresholdGate.setEnabled(false);
				gateText.setText("gate: OFF");
			}
			else {
				thresholdGate.setEnabled(true);
				thresholdGate.setThreshold(gate);
				gateText.setText("gate: " + Float.toString(gate));
			}
			
		}
	}
	private void updateThreshold(double level) {
		if (gate <= -100.0) {
			thresholdText.setText("SPL dB: gate off");
		}
		else {
			thresholdText.setText("SPL dB: " + String.format("%.2f", level));
		}
	}
	
	@SuppressLint("Wakelock")
	private void toggleRecording() {
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG); 
	
		if (androidWriteProcessor != null) {
			if (androidWriteProcessor.RECORDING) {
				androidWriteProcessor.stopRecording();
				timerHandler.removeCallbacks(timerRunnable);
				timerText.setText("timer - 00:00");
				recordButton.setText("RECORD");
				recordButton.setBackgroundColor(Color.LTGRAY);
				if (wakeLock.isHeld()) {
				    wakeLock.release(); // this
				    wakeLock = null;
				}
			}
			else {
				androidWriteProcessor.startRecording();
				startTime = System.currentTimeMillis();
				timerHandler.postDelayed(timerRunnable, 0);
				recordButton.setText("RECORDING...");
				recordButton.setBackgroundColor(Color.RED);
				logger(TAG, androidWriteProcessor.getFreeSpace(this));
				wakeLock.acquire();
			}
		}
	}
	
	private void toggleHeadset(boolean allow) {
		// first:
		// allow for USB i/o
		if (useUSB) {
			// using USB, ensure volume on
			audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 
					audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) / 2, 
					AudioManager.FLAG_SHOW_UI);			
			return;
		}
		// then:
		// if no headset, mute the audio output else feedback
		if (allow) {
			// volume to on
			audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 
					audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) / 2, 
					AudioManager.FLAG_SHOW_UI);
		}
		else {
			// volume to 0
			audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 
					0, 
					AudioManager.FLAG_SHOW_UI);
		}
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
		//TODO
		// need to step in here with usb audio search.
		if (scanUsbDevices()) {
			logger(TAG, "found usb device(s), find audio type...");
			// have found suitable device, use its audio capabilities
			if (determineUsbAudioType()) {
				// have found, need to get TarsosDSP to use...
				// set dispatcher to usb
				dispatcher = AndroidDispatcherFactory.fromUsbMicrophone(sampleRate, bufferSize, 0);	
				useUSB = true;
				logger(TAG, "dispatcher set to USB audio.");
				return true;
			}
			else {
				logger(TAG, "Error: can't determine USB audio type.");
			}
		}
		else {
			logger(TAG, "USB Scanning found no device(s).");
		}
		
		logger(TAG, "Scanning for internal audio...");
		if (determineInternalAudioType()) {
			// looking for system audio, wait for it...
		}
		else {
			logger(TAG, "Error determining audio type, set defaults");
			// guaranteed default for Android is 44.1kHz, PCM_16BIT, CHANNEL_IN_MONO
			// android CHANNEL_OUT_MONO == 4 ; TarsosDSP needs int value 1
			sampleRate = DEFAULT_RATE;
			encoding = DEFAULT_ENCODING;
			channel = DEFAULT_CHANNEL;
			bufferSize = SAM5_BUFFER; // cannot determine a default...
		}
		
		bufferSize = getClosestPowers(bufferSize); // 4096
		bufferOverlap = bufferSize / 2;	// 2048
		logger(TAG, "sampleRate set: " + sampleRate);
		logger(TAG, "bufferSize set: " + bufferSize);		
		logger(TAG, "bufferOverlap set: " + bufferOverlap);
		
		// set from seekbars set by sharedPrefs
		hiFreqGate = hiFreqSeekBar.getProgress() * FREQ_STEP;
		gate = gateSeekBar.getProgress() - 100;
		
		// set dispatcher to internal audio
		dispatcher = AndroidDispatcherFactory.fromDefaultMicrophone(sampleRate, bufferSize, 0);	
		return true;
	}
	
	
	
	private void dispatcherLoadProcesses() {
//PROCESSES
		if (micInProcess()) {
			dispatcher.addAudioProcessor(androidMicControl);
			gainText.setText("gain: " + androidMicControl.getGain());
			logger(TAG, "androidMicControl added.");
		}
		else {
			logger(TAG, "androidMicControl failed to load.");
		}
		
		if (hipassFilterProcess()) {
			dispatcher.addAudioProcessor(hipassFilter);
			logger(TAG, "hipassFilter added.");
		}
		else {
			logger(TAG, "highpassFilter failed to load.");
		}
		
		if (thresholdGateProcess()) {
			dispatcher.addAudioProcessor(thresholdGate);
			logger(TAG, "threshold gate added.");
		}
		else {
			logger(TAG, "threshold gate failed to load.");
		}

//OUPUT			
		if (androidWriteProcess()) {
			dispatcher.addAudioProcessor(androidWriteProcessor);
			logger(TAG, "write processor added.");
		}
		else {
			logger(TAG, "write processor failed to load.");
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
	
	private boolean micInProcess() {		

		androidMicHandler = new AndroidMicHandler() {
			@Override
			public void handleAudio(AudioEvent audioEvent) {			
				final byte[] byteBuffer = audioEvent.getByteBuffer();
				
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						audioVisualiserView.updateVisualiser(byteBuffer);
						// get the SPL as well
						updateThreshold(thresholdGate.currentSPL());
					}
				});
			}
		};		
		androidMicControl = new AndroidMicControl(androidMicHandler);
		
		if (androidMicControl != null)
			return true;
		else
			return false;
	}

	private boolean thresholdGateProcess() {
		thresholdGate = new ThresholdGate(DEFAULT_THRESHOLD, true);
		if (thresholdGate != null) 
			return true;
		else 
			return false;
	}
	
	private boolean androidWriteProcess() {
		// this always (req. androidAudioOutput):
		tarsosAudioFormat = new TarsosDSPAudioFormat(
    			sampleRate, 
    			encoding, 
    			channel, 
    			true, // signed 
    			false); // bigEndian
		
		// then:
		androidWriteProcessor = new AndroidWriteProcessor(this, tarsosAudioFormat, "capture");
		if (androidWriteProcessor != null) {
			return true;
		}
		else
			return false;
	}
	
	private boolean androidAudioOutput() {		
		androidAudioOut = new AndroidAudioOut(
				tarsosAudioFormat, 
				bufferSize,
				AudioManager.STREAM_MUSIC);
		
		if (androidAudioOut != null) 
			return true;
		else 
			return false;
	}
	
	private class HeadsetIntentReceiver extends BroadcastReceiver {
	    @Override public void onReceive(Context context, Intent intent) {
	        if (intent.getAction().equals(Intent.ACTION_HEADSET_PLUG)) {
	            int state = intent.getIntExtra("state", -1);
	            switch (state) {
		            case 0:
		                logger(TAG, "Headset is unplugged, mute output");
		                toggleHeadset(false);
		                break;
		            case 1:
		                logger(TAG, "Headset is plugged");
		                toggleHeadset(true);
		                break;
		            default:
		                logger(TAG, "Headset state unknown");
	            }
	        }
	    }
	}

/*
* USB device   
*/	
	// https://source.android.com/devices/audio/usb.html
	// http://developer.android.com/guide/topics/connectivity/usb/host.html
	// SDR device driver: http://sdr.osmocom.org/trac/
	
	/*
	private boolean getIntentUsbDevice(Intent intent) {
		// the device_filter.xml has a hardcoded usb device declaration
		// update it to the SDR when it gets here...
		deviceContainer = new DeviceContainer();
		deviceContainer.setUsbDevice((UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE));
		return (deviceContainer.hasDevice() != false);
	}
	*/
	
	private boolean scanUsbDevices() {
		// search for any attached usb devices that we can read properties,
		// create a DeviceContainer for them.
		// add a listener service in case user unplugs usb and audio gets re-routed (fdbk)
		//TODO
		logger(TAG, "scanning usb for audio device(s)...");
		usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
		HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
		Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
		
		boolean found = false;
		//int i = 0;
		//deviceContainer = new DeviceContainer();
		
		while(deviceIterator.hasNext()) {			
			UsbDevice device = deviceIterator.next();
			deviceContainer = new DeviceContainer(device);
			found = true;
			logger(TAG, "USB: " + deviceContainer.toString());
			//i++;
		}
		return found;
	}
	
	BroadcastReceiver usbReceiver = new BroadcastReceiver() {
	    public void onReceive(Context context, Intent intent) {
	        String action = intent.getAction();
	        
		    if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
		    	UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
		        if (device != null) {
		            // prepare for re-routing of audio to handset
		            useUSB = false;
		            logger(TAG, "USB device is unplugged, mute output");
		            toggleHeadset(false);
		        }
		    }
		    else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
		    	UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
		        if (device != null) {
		            // prepare for re-routing of audio to USB
		            useUSB = true;
		            logger(TAG, "USB device is plugged in, allow output");
		            toggleHeadset(true);
		        }
		    }
	    }
	};

	
/*
* Utilities   
*/ 	
	/*
	private double soundPressureLevel(final float[] buffer) {
		double power = 0.0D;
		for (float element : buffer) {
			power += element * element;
		}
		double value = Math.pow(power, 0.5) / buffer.length;
		return 20.0 * Math.log10(value);
	}
	*/
	
	// only for querying a device, 
	// TarsosDSP sets its own AudioRecord object
	private boolean determineInternalAudioType() {
	    for (int rate : SAMPLE_RATES) {
	        for (short audioFormat : new short[] { 
	        		AudioFormat.ENCODING_PCM_16BIT,
	        		AudioFormat.ENCODING_PCM_8BIT }) {
	        	
	            for (short channelConfig : new short[] { 
	            		AudioFormat.CHANNEL_IN_DEFAULT,
	            		AudioFormat.CHANNEL_IN_MONO, 
	            		AudioFormat.CHANNEL_IN_STEREO }) {
	                try {
	                    logger(TAG, "Try rate " + rate + "Hz, bits: " + audioFormat + ", channel: "+ channelConfig);
	                    
	                    int buffSize = AudioRecord.getMinBufferSize(rate, channelConfig, audioFormat);
	                    if (buffSize != AudioRecord.ERROR_BAD_VALUE) {
	                        // check if we can instantiate and have a success
	                        AudioRecord recorder = new AudioRecord(
	                        		AudioSource.DEFAULT, 
	                        		rate, 
	                        		channelConfig, 
	                        		audioFormat, 
	                        		buffSize);

	                        if (recorder.getState() == AudioRecord.STATE_INITIALIZED) {
	                        	logger(TAG, "found, rate: " + rate + ", min-buff: " + buffSize);
	                        	// set our values
	                        	sampleRate = rate;
	                        	channel = channelConfig;
	                        	encoding = audioFormat;
	                        	bufferSize = buffSize;
	                        	recorder.release();
	                        	recorder = null;
	                            return true;
	                        }
	                    }
	                } 
	                catch (Exception e) {
	                    logger(TAG, "Rate: " + rate + "Exception, keep trying, e:" + e.toString());
	                }
	            }
	        }
	    }
	    logger(TAG, "determine audioRecord failure.");
	    return false;
	}
	
	private boolean determineUsbAudioType() {
		if (deviceContainer.hasDevice()) {
			for (int rate : SAMPLE_RATES) {
		        for (short audioFormat : new short[] { 
		        		AudioFormat.ENCODING_PCM_16BIT,
		        		AudioFormat.ENCODING_PCM_8BIT }) {
		        	
		            for (short channelConfig : new short[] { 
		            		AudioFormat.CHANNEL_IN_DEFAULT,  //1
		            		AudioFormat.CHANNEL_IN_MONO,  // 16
		            		AudioFormat.CHANNEL_IN_STEREO }) { // 12
		                try {
		                    logger(TAG, "USB - try rate " + rate + "Hz, bits: " + audioFormat + ", channel: "+ channelConfig);
		                    
		                    int buffSize = AudioRecord.getMinBufferSize(rate, channelConfig, audioFormat);
		                    if (buffSize != AudioRecord.ERROR_BAD_VALUE) {
		                        // check if we can instantiate and have a success
		                        AudioRecord recorder = new AudioRecord(
		                        		AudioSource.DEFAULT,  //DEFAULT, MIC, CAMCORDER
		                        		rate, 
		                        		channelConfig, 
		                        		audioFormat, 
		                        		buffSize);
	
		                        if (recorder.getState() == AudioRecord.STATE_INITIALIZED) {
		                        	logger(TAG, "USB - found:: rate: " + rate + ", min-buff: " + buffSize + ", channel: " + channelConfig);
		                        	logger(TAG, "USB - Audio source: " + recorder.getAudioSource());
		                        	// set our values
		                        	sampleRate = rate;
		                        	channel = channelConfig;
		                        	encoding = audioFormat;
		                        	bufferSize = buffSize;
		                        	recorder.release();
		                        	recorder = null;
		                            return true;
		                        }
		                    }
		                } 
		                catch (Exception e) {
		                    logger(TAG, "Rate: " + rate + "Exception, keep trying, e:" + e.toString());
		                }
		            }
		        }
		    }
		}
		else {
		    logger(TAG, "Error: USB device container has no device.");
		    return false;
		}
	    logger(TAG, "determine audioRecord failure.");
	    return false;
	}

	private int getClosestPowers(int reported) {
		// return the next highest power from the minimum reported
		// 512, 1024, 2048, 4096, 8192, 16384
		for (int power : POWERS_TWO) {
			if (reported <= power) {
				return power;
			}			
		}
		// didn't find power, return reported
		return reported;
	}

    public static void logger(String tag, String message) {
    	if (DEBUG) {
    		debugText.append("\n" + tag + ": " + message);
    		Log.d(tag, message);
    	} 
    	else {
    		Log.d(tag, "CFP Recorder ver: " + VERSION);
    	}
    }
}
