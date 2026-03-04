package org.sil.hearthis;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Objects;

import Script.BibleLocation;
import Script.BookInfo;
import Script.IScriptProvider;
import Script.ScriptLine;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.MediaRecorder.AudioEncoder;
import android.media.MediaRecorder.AudioSource;
import android.media.MediaRecorder.OutputFormat;
import android.os.Build;
import android.os.Bundle;


import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.os.BundleCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class RecordActivity extends AppCompatActivity implements View.OnClickListener, WavAudioRecorder.IMonitorListener, MediaPlayer.OnCompletionListener {

	private static final String TAG = "RecordActivity";
	int _activeLine;
	LinearLayout _linesView;
	int _lineCount;
	int _bookNum;
	int _chapNum;
	IScriptProvider _provider;

	static final String BOOK_NUM = "bookNumber";
	static final String CHAP_NUM = "chapterNumber";
	static final String ACTIVE_LINE = "activeLine";
	boolean usingSpeaker;
	boolean wasUsingSpeaker;
	MediaPlayer playButtonPlayer;

	// Back to instance variables to avoid resource contention, but using safe lifecycle management.
	private MediaRecorder recorder = null;
	private WavAudioRecorder waveRecorder = null;
	public static boolean useWaveRecorder = true;
	LevelMeterView levelMeter;

	NextButton nextButton;
	RecordButton recordButton;
	PlayButton playButton;
	Date startRecordingTime;
	volatile boolean starting = false;
    volatile boolean stopRequestedEarly = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		EdgeToEdge.enable(this);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			getWindow().getAttributes().layoutInDisplayCutoutMode =
					WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
		}
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_record);

		View root = findViewById(R.id.recordActivityRoot);
		if (root == null){
			root = findViewById(android.R.id.content);
		}

		ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
			Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());

			v.setPadding(insets.left, insets.top, insets.right, 0);

			if (_linesView != null && _linesView.getParent() instanceof ScrollView scrollView) {
                scrollView.setPadding(0, 0, 0, insets.bottom);
				scrollView.setClipToPadding(false);
			}

			return windowInsets;
		});

		Objects.requireNonNull(getSupportActionBar()).setTitle(R.string.record_title);
		ServiceLocator.getServiceLocator().init(this);

		Intent intent = getIntent();
		Bundle extras = intent.getExtras();
		BookInfo book = BundleCompat.getSerializable(extras, "bookInfo", BookInfo.class);
		if (book != null) {
			_chapNum = extras.getInt("chapter");
			_bookNum = book.BookNumber;
			_provider = book.getScriptProvider();
			_activeLine = extras.getInt("line", 0);
		} else if (savedInstanceState != null) {
			_chapNum = savedInstanceState.getInt(CHAP_NUM);
			_bookNum = savedInstanceState.getInt(BOOK_NUM);
			_activeLine = savedInstanceState.getInt(ACTIVE_LINE);
			_provider = ServiceLocator.getServiceLocator().init(this).getScriptProvider();
		} else {
			finish();
			return;
		}
		_lineCount = _provider.GetScriptLineCount(_bookNum, _chapNum);

		LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		_linesView = findViewById(R.id.textLineHolder);
		_linesView.removeAllViews();

		for (int i = 0; i < _lineCount; i++) {
			ScriptLine line = _provider.GetLine(_bookNum, _chapNum, i);
			TextView lineView = (TextView) inflater.inflate(R.layout.text_line, _linesView, false);
			lineView.setText(line.Text);
			_linesView.addView(lineView);
			setTextColor(i);
			lineView.setOnClickListener(this);
		}

		((LinesView) findViewById(R.id.zoomView)).updateScale();

		nextButton = findViewById(R.id.nextButton);
		nextButton.setOnClickListener(v -> nextButtonClicked());

		recordButton = findViewById(R.id.recordButton);
		recordButton.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                v.performClick();
            }
            recordButtonTouch(e);
            return true;
        });

		playButton = findViewById(R.id.playButton);
		playButton.setOnClickListener(v -> playButtonClicked());
		if (_lineCount > 0)
			setActiveLine(_activeLine);
		levelMeter = findViewById(R.id.levelMeter);
	}

	@Override
	protected void onResume() {
		super.onResume();
		startMonitoring();
		
		AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		wasUsingSpeaker = am.isSpeakerphoneOn();
		if (usingSpeaker) {
			am.setMode(AudioManager.MODE_IN_COMMUNICATION);
			am.setSpeakerphoneOn(true);
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		stopMonitoring();
		stopPlaying();
		
		BibleLocation location = new BibleLocation();
		location.bookNumber = _bookNum;
		location.chapterNumber = _chapNum;
		location.lineNumber = _activeLine;
		_provider.saveLocation(location);
		
		AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		if (usingSpeaker) {
			am.setSpeakerphoneOn(false);
			am.setMode(AudioManager.MODE_NORMAL);
		}
	}

	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
		savedInstanceState.putInt(CHAP_NUM, _chapNum);
		savedInstanceState.putInt(BOOK_NUM, _bookNum);
		savedInstanceState.putInt(ACTIVE_LINE, _activeLine);
		super.onSaveInstanceState(savedInstanceState);
	}

	void nextButtonClicked() {
		if (_activeLine >= _lineCount - 1) {
			return;
		}
		setActiveLine(_activeLine + 1);
	}

	void setTextColor(int lineNo) {
		TextView lineView = (TextView) _linesView.getChildAt(lineNo);
		int lineColor = ContextCompat.getColor(this, R.color.contextTextLine);
		if (lineNo == _activeLine) {
			lineColor = ContextCompat.getColor(this, R.color.activeTextLine);
		} else {
			String recordingFilePath = _provider.getRecordingFilePath(_bookNum, _chapNum, lineNo);
			if (new File(recordingFilePath).exists()) {
				lineColor = ContextCompat.getColor(this, R.color.recordedTextLine);
			} else if (_provider.hasRecording(_bookNum, _chapNum, lineNo)) {
				lineColor = ContextCompat.getColor(this, R.color.recordedElsewhereTextLine);
			}
		}
		lineView.setTextColor(lineColor);
	}

	void setActiveLine(int lineNo) {
		int oldLine = _activeLine;
		_activeLine = lineNo;
		setTextColor(oldLine);
		setTextColor(_activeLine);

		ScrollView scrollView = (ScrollView) _linesView.getParent();
		int[] tops = new int[_linesView.getChildCount() + 1];
		for (int i = 0; i < tops.length - 1; i++) {
			tops[i] = _linesView.getChildAt(i).getTop();
		}
		tops[tops.length - 1] = _linesView.getChildAt(tops.length - 2).getBottom();
		scrollView.scrollTo(0, getNewScrollPosition(scrollView.getScrollY(), scrollView.getHeight(), _activeLine, tops));
		_recordingFilePath = _provider.getRecordingFilePath(_bookNum, _chapNum, _activeLine);
		recordButton.setIsDefault(true);
		nextButton.setIsDefault(false);
		playButton.setIsDefault(false);
		updateDisplayState();
	}

	private void updateDisplayState() {
		playButton.setButtonState(new File(_recordingFilePath).exists() ? BtnState.Normal : BtnState.Inactive);
	}

	static int getNewScrollPosition(int scrollPos, int height, int newLine, int[] tops) {
		int newScrollPos = scrollPos;
		int bottom = tops[newLine + 1];
		int bottomNext = bottom;
		if (newLine < tops.length - 2) {
			bottomNext = tops[newLine + 2];
		}
		if (bottomNext > scrollPos + height) {
			newScrollPos = bottomNext - height;
		}
		int top = tops[newLine];
		int topPrev = top;
		if (newLine > 0) {
			topPrev = tops[newLine - 1];
		}
		if (newScrollPos > topPrev) {
			newScrollPos = topPrev;
			if (newScrollPos + height < bottom) {
				newScrollPos = bottom - height;
				if (newScrollPos > top) {
					newScrollPos = top;
				}
			}
		}
		return newScrollPos;
	}

	void recordButtonTouch(MotionEvent e) {
		if (!requestRecordAudioPermission())
			return;
		int maskedAction = e.getActionMasked();

		switch (maskedAction) {
			case MotionEvent.ACTION_DOWN: {
				startRecording();
				break;
			}
			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_CANCEL: {
				stopRecording();
				break;
			}
		}
	}

	String _recordingFilePath = "";

	void startMonitoring() {
		if (waveRecorder != null)
			waveRecorder.release();
		waveRecorder = new WavAudioRecorder(AudioSource.MIC, 44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
		waveRecorder.setMonitorListener(this);
		waveRecorder.startMonitoring();
	}

	private void stopMonitoring() {
		if (waveRecorder != null) {
			waveRecorder.stop();
			waveRecorder.release();
			waveRecorder = null;
		}
	}

	void startWaveRecorder() {
		if (waveRecorder != null)
			waveRecorder.release();
		waveRecorder = new WavAudioRecorder(AudioSource.MIC, 44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
		File oldRecording = new File(_recordingFilePath);
		if (oldRecording.exists()){
			if(!oldRecording.delete()){
				Log.e(TAG,"Failed to delete existing recording at " + _recordingFilePath);
			}
		}
		waveRecorder.setOutputFile(_recordingFilePath);
		waveRecorder.prepare();
		waveRecorder.setMonitorListener(this);
		waveRecorder.start();
		recordButton.setWaiting(false);
		startRecordingTime = new Date();
		starting = false;
        if (stopRequestedEarly) {
            runOnUiThread(() -> {
                if (stopRequestedEarly) {
                    stopRequestedEarly = false;
                    stopRecording();
                }
            });
        }
	}

	void startRecording() {
		stopPlaying();
		recordButton.setButtonState(BtnState.Pushed);
		recordButton.setWaiting(true);
        stopRequestedEarly = false;
		if (useWaveRecorder) {
			starting = true;
			new Thread(this::startWaveRecorder).start();
			return;
		}
		if (recorder != null) {
			recorder.release();
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			recorder = new MediaRecorder(this);
		} else {
			recorder = new MediaRecorder();
		}
		recorder.setAudioSource(AudioSource.MIC);
		recorder.setOutputFormat(OutputFormat.MPEG_4);
		recorder.setAudioEncoder(AudioEncoder.AAC);
		recorder.setAudioSamplingRate(44100);
		recorder.setAudioEncodingBitRate(44100);
		File file = new File(_recordingFilePath);
		File dir = file.getParentFile();
        assert dir != null;
        if (!dir.exists()){
			if(!dir.mkdirs()){
				Log.e(TAG,"Failed to make directory at " + _recordingFilePath);
			}
		}
		recorder.setOutputFile(file.getAbsolutePath());
		try {
			recorder.prepare();
			recorder.start();
			recordButton.setWaiting(false);
			startRecordingTime = new Date();
		} catch (IOException e) {
			Log.e(TAG, "Failed to prepare or start recording", e);
		}
	}

	private final int RECORD_ACTIVITY_RECORD_PERMISSION = 37;

	private boolean requestRecordAudioPermission() {
		if (ContextCompat.checkSelfPermission(this,
				Manifest.permission.RECORD_AUDIO)
				!= PackageManager.PERMISSION_GRANTED) {
			ActivityCompat.requestPermissions(this,
					new String[]{Manifest.permission.RECORD_AUDIO},
					RECORD_ACTIVITY_RECORD_PERMISSION);
			return false;
		} else {
			return true;
		}
	}

	@Override
	public void onRequestPermissionsResult(
			int requestCode,
			@NonNull String[] permissions,
			@NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (requestCode == RECORD_ACTIVITY_RECORD_PERMISSION) {
			if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
				Toast.makeText(this, R.string.no_use_without_record, Toast.LENGTH_LONG).show();
			}
		}
	}

	void stopRecording() {
		Date beginStop = new Date();
		if (starting) {
			stopRequestedEarly = true;
			return;
		}
		recordButton.setButtonState(BtnState.Normal);
		recordButton.setWaiting(false);
		if (useWaveRecorder && waveRecorder != null)  {
			waveRecorder.stop();
			startMonitoring();
		}
	   else if (recorder != null) {
			recorder.stop();
			recorder.reset();
			recorder.release();
			recorder = null;
		}
		
		if (startRecordingTime != null && beginStop.getTime() - startRecordingTime.getTime() < 500) {
			new AlertDialog.Builder(this)
					.setMessage(R.string.record_too_short)
					.setPositiveButton(android.R.string.ok, null)
					.setIcon(android.R.drawable.ic_dialog_alert)
					.show();
			File badFile = new File(_recordingFilePath);
			if (badFile.exists()){
				badFile.delete();
			}
			return;
		}
		recordButton.setIsDefault(false);
		playButton.setIsDefault(true);
		nextButton.setIsDefault(false);
		updateDisplayState();
		_provider.noteBlockRecorded(_bookNum, _chapNum, _activeLine);
	}

	void playButtonClicked() {
		stopPlaying();
		playButton.setPlaying(true);
		playButtonPlayer = new MediaPlayer();
		playButtonPlayer.setOnCompletionListener(this);
		stopMonitoring();
		try {
			File file = new File(_recordingFilePath);
			playButtonPlayer.setDataSource(file.getAbsolutePath());
            playButtonPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            playButtonPlayer.prepare();
			playButtonPlayer.start();
		} catch (Exception e) {
			Log.e(TAG, "Error playing recording", e);
		}		
	}

	private void stopPlaying() {
		if (playButtonPlayer != null) {
			try {
				if (playButtonPlayer.isPlaying()) {
					playButtonPlayer.stop();
				}
			} catch (IllegalStateException e) {
			}
			playButtonPlayer.release();
			playButtonPlayer = null;
		}
	}

	@Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_record, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
		final int itemId = item.getItemId();
		if(itemId == R.id.sync){
            Intent sync = new Intent(this, SyncActivity.class);
            startActivity(sync);
            return true;
        }
		else if (itemId == R.id.choose) {
			Intent choose = new Intent(this, ChooseBookActivity.class);
			startActivity(choose);
			return true;
		}
		else if (itemId == R.id.speakers) {
			usingSpeaker = !item.isChecked();
			item.setChecked(usingSpeaker);
			AudioManager am = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
			am.setMode(usingSpeaker ? AudioManager.MODE_IN_COMMUNICATION : AudioManager.MODE_NORMAL);
			am.setSpeakerphoneOn(usingSpeaker);
		}
        return false;
    }

	@Override
	public void onClick(View view) {
		int newLine = _linesView.indexOfChild(view);
		setActiveLine(newLine);
	}

	@Override
	public void maxLevel(int level) {
		double percentMax = (double)level / 32768;
		double db = 20 * Math.log10(percentMax);
		int displayLevel = Math.max(0, 100 + (int) Math.round(db * 100 / 40));
		levelMeter.setLevel(displayLevel);
	}

	@Override
	public void onCompletion(MediaPlayer mediaPlayer) {
		stopPlaying();
		playButton.setPlaying(false);
		playButton.setButtonState(BtnState.Normal);
		playButton.setIsDefault(false);
		recordButton.setIsDefault(false);
		nextButton.setIsDefault(true);
		startMonitoring();
	}
}
