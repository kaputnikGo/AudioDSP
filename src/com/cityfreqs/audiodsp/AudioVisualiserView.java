package com.cityfreqs.audiodsp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

public class AudioVisualiserView extends View {
	private static final String TAG = "CFP_Recorder-visual";
	private byte[] mBytes;
	private float[] mPoints;
	private Rect mRect = new Rect();
	private Paint mForePaint = new Paint();
	
	private static final int MULTIPLIER = 4; // 4
	private static final int RANGE = 256; // 128, 256
	
	public AudioVisualiserView(Context context) {
		super(context);
		init();
	}	
	public AudioVisualiserView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}
	public AudioVisualiserView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init();
	}
	
	private void init() {
		mBytes = null;
		mForePaint.setStrokeWidth(1f);
		mForePaint.setAntiAlias(true);
		mForePaint.setColor(Color.rgb(0, 128, 255));
	}
	
	public void updateVisualiser(byte[] bytes) {
		mBytes = bytes;
		invalidate();
	}
	
	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		if (mBytes == null) {
			MainActivity.logger(TAG, "no bytes to draw");
			return;
		}
		
		if (mPoints == null || mPoints.length < mBytes.length * MULTIPLIER) {
			mPoints = new float[mBytes.length * MULTIPLIER];
		}
		mRect.set(0, 0, getWidth(), getHeight());
		for (int i = 0; i < mBytes.length - 1; i += 2) {
			mPoints[i * MULTIPLIER] = mRect.width() * i / (mBytes.length - 1);
			mPoints[i * MULTIPLIER + 1] = mRect.height() / 2 + ((byte) (mBytes[i] + RANGE)) * (mRect.height() / 2) / RANGE;
			mPoints[i * MULTIPLIER + 2] = mRect.width() * (i + 1) / (mBytes.length - 1);
			mPoints[i * MULTIPLIER + 3] = mRect.height() / 2 + ((byte) (mBytes[i + 1] + RANGE)) * (mRect.height() / 2) / RANGE;
		}
		canvas.drawLines(mPoints, mForePaint);
	}
	
}