package com.cityfreqs.cfp_recorder;

public class AudioPcmQueue {
	// ref: http://stackoverflow.com/questions/15051889/playing-back-audio-with-a-delay
	private static final String TAG = "CFP_R-pcm";
	private float mBuf[] = null; //buffer
	private int mWrIdx = 0; //writeIndex
	private int mRdIdx = 0; //readIndex
	private int mCount = 0; // counter
	private int mBufSz = 0; // bufferSize
	
	//TODO
	// byte[] not short[]
	
	private Object mSync = new Object();
	
	public AudioPcmQueue() {
		// no
	}
	
	public AudioPcmQueue(int nBufSz) {
		try {
			mBuf = new float[nBufSz];
		}
		catch (Exception e) {
			MainActivity.logger(TAG,  "audioQueue allocation failed.");
			mBuf = null;
			mBufSz = 0;
		}
	}
	
	public int doWrite(final float pWrBuf[], final int nWrBufIdx, final int nLen) {
		int sampsWritten = 0;
		
		if (nLen > 0) {
			int toWrite;
			
			synchronized(mSync) {
				// write nothing if buffer full
				toWrite = (nLen <= (mBufSz - mCount)) ? nLen : 0;
			}
			
			// read the toWrite
			while (toWrite > 0) {
				// get number in buffer
				final int sampsToCopy = Math.min(toWrite,  (mBufSz - mWrIdx));
				// copy them
				System.arraycopy(pWrBuf, sampsWritten + nWrBufIdx, mBuf, mWrIdx, sampsToCopy);
				// circular buffering
				mWrIdx += sampsToCopy;
				if (mWrIdx >= mBufSz) {
					mWrIdx -= mBufSz;
				}
				// increment number written
				sampsWritten += sampsToCopy;
				toWrite -= sampsToCopy;
			}
			// increment count
			synchronized(mSync) {
				mCount = mCount + sampsWritten;
			}
		}
		return sampsWritten;
	}
	
	public int doRead(float pcmBuffer[], final int nRdBufIdx, final int nRdBufLen) {
		int sampsRead = 0;
		final int nSampsToRead = Math.min(nRdBufLen,  pcmBuffer.length - nRdBufIdx);
		
		if (nSampsToRead > 0) {
			int sampsToRead;
			
			synchronized(mSync) {
				// number to read from buffer
				sampsToRead = Math.min(mCount,  nSampsToRead);
			}
			
			while (sampsToRead > 0) {
				// get number in buffer
				final int sampsToCopy = Math.min(sampsToRead,  (mBufSz - mRdIdx));
				// copy them
				System.arraycopy(mBuf,  mRdIdx,  pcmBuffer,  sampsRead + nRdBufIdx, sampsToCopy);
				// circular buffering
				mRdIdx += sampsToCopy;
				if (mRdIdx >= mBufSz) {
					mRdIdx -= mBufSz;
				}
				// increment number read
				sampsRead += sampsToCopy;
				sampsToRead -= sampsToCopy;
			}
			// decrement count
			synchronized(mSync) {
				mCount = mCount - sampsRead;
			}
		}
		return sampsRead;
	}
	
}