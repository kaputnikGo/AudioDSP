package com.cityfreqs.audiodsp;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;

import android.content.Context;
import android.os.Environment;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;
import be.tarsos.dsp.writer.WaveHeader;

public class AndroidWriteProcessor implements AudioProcessor {
	// ready this for android file saving
	
	private static final String TAG = "AudioDSP-recorder";
    RandomAccessFile output;
    TarsosDSPAudioFormat audioFormat;
    private int audioLen = 0;
    private  static final int HEADER_LENGTH = 44; //byte
    
    private File ourIntDirectory; 
    private String filename;
    private File outputFile;
  
    private boolean ext_capable = false;
    private File ourExtDirectory;
    private static final String OUR_DIRECTORY = "AudioDSP";
    private static final String FILE_EXTENSION = ".wav";
    
    public boolean RECORDING;
    private boolean ready;
    
    public AndroidWriteProcessor(Context context, TarsosDSPAudioFormat audioFormat, String filename) {	
    	this.audioFormat = audioFormat;
    	
    	ourIntDirectory = context.getFilesDir();
    	// need to add correct file ext here (.wav ?)
    	this.filename = filename + FILE_EXTENSION;
    	
    	// prepare the default file save location here
    	if (isExternalStorageWritable()) {
    		if (createOurDirectory()) {
    			log("Ext storage ready, prep file...");
    			prepareOutputFile();
    		}
    	}
    	else {
    		// prepare the internal
    		log("Int storage only, prep file...");
    		prepareOutputFile();
    	}
    	// need to create audioFormat...
    }
       
    public AndroidWriteProcessor(TarsosDSPAudioFormat audioFormat, RandomAccessFile output) {
        // depends on valid, externally created output file
    	this.output = output;
        this.audioFormat = audioFormat;
        
        RECORDING = false;        
        ready = readyRecording();
    }   
    
    
 /*
  *  public methods  
  */
    public void setFileName(String filename) {
    	this.filename = filename;
    }
    
    public boolean readyRecording() {
        try {
            output.write(new byte[HEADER_LENGTH]);
            log("Recording ready.");
            return true;
        } 
        catch (IOException e) {
            e.printStackTrace();
            log("Recording ready fail.");
            return false;
        }
    }
    
    public void startRecording() {
    	if (ready) {
    		RECORDING = true;
    		log("RECORDING...");
    	}
    }
    public void stopRecording() {
    	if (RECORDING) {
    		processingFinished();
    		log("...STOPPED.");
    		// does this mean process() is still running?
    		// will it need calling again at prepare?
    	}
    }
    
    public String getFreeSpace(Context context) {
    	if (outputFile != null) {
    		return "Free: " + android.text.format.Formatter.formatShortFileSize(
    				context, outputFile.getFreeSpace());
    	}
    	else
    		return "Free: ERROR";
    }

    
/*********************************************************************************
 * file writing methods
 */
    private File getStorageFile() {
    	// get ext if possible
    	if (ourExtDirectory != null) {
    		return ourExtDirectory;
    	}
    	else if (ourIntDirectory != null) {
    		return ourIntDirectory;
    	}
    	else {
    		log("no storage directories found.");
    		return null;
    	}
    }
      
    private boolean prepareOutputFile() {
    	// shouldn't get this, but...
    	if (filename == "" || filename == null) {
    		log("No filename set error.");
    		return false;
    	}
    	// need to build the filename AND path
    	File location = getStorageFile();
    	if (location == null) {
    		log("Error getting storage directory");
    		return false;
    	}
    	// file save will overwrite unless new name is used...
    	try {
    		outputFile = new File(location, filename);
    		if (!outputFile.exists()) {
    			outputFile.createNewFile();
    		}    		
    		output = new RandomAccessFile(outputFile, "rw");
            RECORDING = false;        
            ready = readyRecording();
    		return true;
    	}
    	catch (FileNotFoundException ex) {
    		ex.printStackTrace();
    		log("File not found error.");
    	} 
    	catch (IOException e) {
			e.printStackTrace();
			log("File IO error.");
		}
    	return false;
    }
    
    // included if TarsosDSP method not suitable
    public void altWriteToFile(byte[] audioArray) {
    	// shouldn't get this, but...
    	if (filename == "" || filename == null) {
    		log("No filename set error.");
    		return;
    	}
    	// need to build the filename AND path
    	File location = getStorageFile();
    	if (location == null) {
    		log("Error getting storage directory");
    		return;
    	}
    	// file save will overwrite unless new name is used...
    	try {
    		outputFile = new File(location, filename);
    		if (!outputFile.exists()) {
    			outputFile.createNewFile();
    		}
    		OutputStream out = null;
    		out = new BufferedOutputStream(new FileOutputStream(outputFile, false)); // append == false
    		out.write(audioArray);
    		if (out != null) {
    			out.close();
    		}
    	}
    	catch (FileNotFoundException ex) {
    		ex.printStackTrace();
    		log("File not found error.");
    	} 
    	catch (IOException e) {
			e.printStackTrace();
			log("File save IO error.");
		}
    }
    
/*********************************************************************************
 * inherited methods
 */
    
    // do not call UI thread writes here...
    @Override
    public boolean process(AudioEvent audioEvent) {
        if (RECORDING) {
	    	try {
		    	audioLen += audioEvent.getByteBuffer().length;
		        //write audio to the output
		        output.write(audioEvent.getByteBuffer());
	        } 
	        catch (IOException e) {
	            e.printStackTrace();
	            //log("recording fail.");
	        }
	        return true;
        }
        // bypass any file writing, 
        // allow audio to continue thru path
        return true;
    }

    @Override
    public void processingFinished() {    	
    	// need to catch if app closes and this is called...
    	if (RECORDING) {    	
	    	//write header and data to the result output
		    WaveHeader waveHeader=new WaveHeader(WaveHeader.FORMAT_PCM,
		            (short)audioFormat.getChannels(),
		            (int)audioFormat.getSampleRate(),
		            (short)16,
		            audioLen); //16 is for pcm, Read WaveHeader class for more details
		        
		    ByteArrayOutputStream header = new ByteArrayOutputStream();
		    try {
		        waveHeader.write(header);
		        output.seek(0);
		        output.write(header.toByteArray());
		        output.close();
		        //log("Recording write finished.");
		    }
		    catch (IOException e){
		        e.printStackTrace();
		        //log("Recording write fail.");
		    }
		    RECORDING = false;
    	}
    }
    
/*********************************************************************************
 * utilities     
 */
    private boolean isExternalStorageWritable() {
    	// is available for read and write
    	if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
    		log("Ext storage has read/write.");
    		ext_capable = true;
    		return true;
    	}
    	else {
    		log("Ext storage not read/write.");
    		return false;
    	}
    }
    public boolean isExternalStorageReadable() {
    	// maybe is only readable?
    	if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(Environment.getExternalStorageState())) {
    		log("Ext storage only readable.");
    		return true;
    	}
    	else {
    		log("Ext storage not readable.");
    		return false;
    	}
    }
    
    public File getExtAudioStorageDir() {
    	if (ext_capable) {
    		return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
    	}
    	return null;
    }
    
    private boolean createOurDirectory() {
    	if (ext_capable) {
    		ourExtDirectory = new File(Environment.getExternalStoragePublicDirectory(
    						Environment.DIRECTORY_MUSIC), OUR_DIRECTORY);
    		if (ourExtDirectory != null) {
    		//if (ourExtDirectory.mkdir()) { // <-dunno, this returned false, create works though.
    			return true;
    		}
    		else {
    			log("Create our ext directory error.");
    			return false;
    		}
    	}
    	log("Ext storage not available.");
    	return false;
    }
    
    private void log(String message) {
    	MainActivity.logger(TAG, message);
    }
}
