package dan.dit.gameMemo.dataExchange.file;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;
import dan.dit.gameMemo.R;
import dan.dit.gameMemo.dataExchange.DataExchangeActivity;
import dan.dit.gameMemo.dataExchange.ExchangeService;


public class FileReadDataExchangeActivity extends DataExchangeActivity {
	private File mDataFile;
	private InputStream mDataStream;
	private List<ExchangeMessage> mMessages;
	private int[] mContainedGames;
	private MessageImportService mService;
	private Button mStartImport;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		if (getIntent().getAction().equals(Intent.ACTION_VIEW)) {
			Uri uri = getIntent().getData();
			if (uri != null) {
				if (uri.getScheme().equals("file")) {
					String path = uri.getPath();
					if (path != null) {
						mDataFile = new File(path);
					}
				} else if (uri.getScheme().equals("content")) {
					try {
						mDataStream = getContentResolver().openInputStream(getIntent().getData());
					} catch (FileNotFoundException fnf) {
						//bad luck
					}
					
				}
			}
		}
		
		boolean hasValidData = (mDataFile != null && mDataFile.exists()) || mDataStream != null;
		if (hasValidData) {
			hasValidData = prepareData();
			getIntent().putExtra(DataExchangeActivity.EXTRA_ALL_GAMES, mContainedGames);
		}
		super.onCreate(savedInstanceState);
		if (hasValidData) {
			setContentView(R.layout.data_exchange_fileread);
			mStartImport = (Button) findViewById(R.id.share);
			mStartImport.setOnClickListener(new OnClickListener() {
				
				@Override
				public void onClick(View v) {
					startImport();
				}
			});
		} else {
			Log.d("FileRead", "No valid data found in intent " + getIntent());
			setResult(RESULT_CANCELED);
			finish();
		}
	}
	
	private boolean prepareData() {
		Set<Integer> containedGames = new HashSet<Integer>();
		//reads the messages from the file for later use, on any error returns false and aborts
		StringBuilder allData = new StringBuilder();
		BufferedReader reader = null;
		try {
			if (mDataFile != null) {
				reader = new BufferedReader(new FileReader(mDataFile));
			} else if (mDataStream != null) {
				reader = new BufferedReader(new InputStreamReader(mDataStream));
			}
		} catch (FileNotFoundException e) {
			return false;
		}
		if (reader == null) {
			return false; // should not be the case
		}
		String curr = null;
		try {
			while ((curr = reader.readLine()) != null) {
				allData.append(curr);
			}
		} catch (IOException e) {
			return false;
		}
		mMessages = new LinkedList<ExchangeMessage>();
		String data = allData.toString();
		Matcher matcher = FileWriteService.MESSAGE_HEADER_PATTERN.matcher(data);
		int messageStartIndex = -1; // inclusive
		int connectionId = -1;
		int messageId = -1;
		while (matcher.find()) {
			if (messageStartIndex != -1) {
				mMessages.add(new ExchangeMessage(connectionId, messageId, data.substring(messageStartIndex, matcher.start())));
			}
			messageStartIndex = matcher.end();
			try {
				connectionId = Integer.parseInt(matcher.group(1));
				messageId = Integer.parseInt(matcher.group(2));
			} catch (NumberFormatException nfe) {
				return false; // matched numbers, could only happen that matched number is too long if data is corrupt
			}
			containedGames.add(connectionId);
		}
		if (messageStartIndex != -1) {// special treatment of the last message
			mMessages.add(new ExchangeMessage(connectionId, messageId, data.substring(messageStartIndex))); 
		}
		mContainedGames = new int[containedGames.size()];
		int index = 0;
		for (int gameKey : containedGames) {
			mContainedGames[index++] = gameKey;
		}
		return mContainedGames.length > 0; // if there is no message at all this failed
	}
	
	@Override
	protected void onNewConnection(Object connectionObject) {
		super.onNewConnection(connectionObject);
		mService.startImport();
	}
	
	@Override
	protected void onConnectionTerminated(int successfullyExchanged) {
		super.onConnectionTerminated(successfullyExchanged);
		Toast.makeText(this, getResources().getQuantityString(R.plurals.import_success, successfullyExchanged, successfullyExchanged), Toast.LENGTH_LONG).show();
	}

	private void startImport() {
		mService = new MessageImportService(mHandler, mMessages);
		mStartImport.setEnabled(false);
	}
	
	@Override
	protected ExchangeService getExchangeService() {
		return mService;
	}

	@Override
	protected void setConnectionStatusText(int newState) {
		//not sent by service
	}

}
