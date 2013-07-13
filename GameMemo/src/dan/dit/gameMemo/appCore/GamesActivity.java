package dan.dit.gameMemo.appCore;

import java.util.Collection;
import java.util.List;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentTransaction;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import dan.dit.gameMemo.R;
import dan.dit.gameMemo.appCore.GameDetailFragment.DetailViewCallback;
import dan.dit.gameMemo.appCore.GamesOverviewListFragment.GameOverviewCallback;
import dan.dit.gameMemo.dataExchange.bluetooth.BluetoothDataExchangeActivity;
import dan.dit.gameMemo.dataExchange.file.FileWriteDataExchangeActivity;
import dan.dit.gameMemo.gameData.game.Game;
import dan.dit.gameMemo.gameData.game.Game.PlayerRenamedListener;
import dan.dit.gameMemo.gameData.game.GameKey;
import dan.dit.gameMemo.gameData.player.ChoosePlayerDialogFragment.ChoosePlayerDialogListener;
import dan.dit.gameMemo.gameData.player.Player;
import dan.dit.gameMemo.gameData.player.PlayerPool;
import dan.dit.gameMemo.gameData.player.RenamePlayerDialogFragment;
import dan.dit.gameMemo.storage.GameStorageHelper;
import dan.dit.gameMemo.util.ShowStacktraceUncaughtExceptionHandler;

public abstract class GamesActivity extends android.support.v4.app.FragmentActivity implements
		DetailViewCallback, GameOverviewCallback, ChoosePlayerDialogListener,
		PlayerRenamedListener {
	public static final String EXTRA_RESULT_WANT_REMATCH = "dan.dit.gameMemo.WANT_REMATCH";
	protected static final int GAME_SETUP_ACTIVITY = 1; // when user wants to start a new game and wants to continue an existing one or selected players
	private static final int GAME_DETAIL_ACTIVITY = 2; // when user selected a game, used to return the id of the game to highlight game (and game had no id previously for example)

	private Handler mHandler; // to send messages to this activities UI thread
	
	// member variables if this activity handles the details fragment
	private boolean mHandlesDetailFragment;
	private boolean mIsActivityInitialLaunch;
	protected GameDetailFragment mDetailsFragment; 
	protected GamesOverviewListFragment mOverviewFragment;
	private MenuItem mDeleteOption;
	private int mGameKey;
	
	@SuppressLint("NewApi")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Thread.setDefaultUncaughtExceptionHandler(new ShowStacktraceUncaughtExceptionHandler(this));
		mGameKey = getIntent().getExtras().getInt(GameKey.EXTRA_GAMEKEY);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			ActionBar bar = getActionBar();
			if (bar != null) {
				bar.setHomeButtonEnabled(true);
				bar.setDisplayHomeAsUpEnabled(true);
				bar.setIcon(GameKey.getGameIconId(mGameKey));
				bar.setTitle(GameKey.getGameName(mGameKey));
				bar.setDisplayShowTitleEnabled(true);
			}
		}
		mHandler = new Handler();
		setContentView(GameKey.getGamesMainLayout(mGameKey));
		mOverviewFragment = (GamesOverviewListFragment) getSupportFragmentManager().findFragmentById(R.id.game_list);
		checkIfHandlesDetailsFragment();
		if (savedInstanceState == null) {
			// initial setup
			mIsActivityInitialLaunch = true;
			//loadGameDetails(getIntent().getExtras()); // is this of any use?
		}
	}
	
	private void checkIfHandlesDetailsFragment() {
		View detailsFrame = findViewById(R.id.game_detail_frame);
		mHandlesDetailFragment =  detailsFrame != null && detailsFrame.getVisibility() == View.VISIBLE;
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(GameKey.getGamesMenuLayout(mGameKey), menu);
		mDeleteOption = menu.findItem(R.id.delete);
		return true;
	}
	
	@Override
	public void onStart() {
		super.onStart();
		checkIfHandlesDetailsFragment();
		if (mDetailsFragment == null) {
			// in case reference was lost 
			mDetailsFragment = (GameDetailFragment) getSupportFragmentManager().findFragmentById(R.id.game_detail_frame);
		}
		if (mHandlesDetailFragment) {
			if (mDetailsFragment != null && mDetailsFragment.getDisplayedGameId() != mOverviewFragment.getHighlightedGameId()) {
				setHighlightedGame(mDetailsFragment.getDisplayedGameId());
			}
		} else {
			if (mDetailsFragment != null) {
				// we still have a fragment we do not care about, close it
                FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                ft.remove(mDetailsFragment);
                ft.commit();
    			mDetailsFragment = null;
			}
		}
	}
	
	protected abstract void startGameSetup(long id);
	protected abstract void showStatistics();
	protected abstract void reactToGameSetupActivity(Bundle extras);

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.setup_game:
			startGameSetup(Game.NO_ID);
			return true;
		case R.id.show_statistics:
			showStatistics();
			return true;
		case R.id.open_data_exchanger_bluetooth:
			openDataExchangerBluetooth();
			return true;
		case R.id.share:
			openFileWriterExchanger();
			return true;
		case R.id.rename_player:
			renamePlayerDialog();
			return true;
		case R.id.delete:
			askAndPerformDelete();
			return true;
		case android.R.id.home:
			startGameChooserActivity();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private void startGameChooserActivity() {
		Intent i = new Intent(this, GameChooserActivity.class);
		i.putExtra(GameChooserActivity.EXTRA_LAST_GAME, mGameKey);
		i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		startActivity(i);
	}

	@Override
 	public void onStop() {
 		super.onStop();
 		closeContextMenu();
 		closeOptionsMenu();
 	}
 	
 	private void askAndPerformDelete() {
    	Collection<Long> checked = mOverviewFragment.getCheckedIds();
    	if (checked.size() == 0) {
    		showDeleteButton(false);
    		return; //nothing to delete
    	}
    	if (checked.contains(getSelectedGameId())) {
    		selectGame(Game.NO_ID);
    	}
    	// ask to delete all checked, tell this the fragment so we have no zombies
		showDeleteButton(false);
        Game.deleteConfirmed(this, checked, mGameKey, mOverviewFragment);   
 	}
 	
 	private void showDeleteButton(boolean show) {
 		if (mDeleteOption != null) {
 			mDeleteOption.setVisible(show);
 			mDeleteOption.setEnabled(show);
 		}
 	}
 	
 	@Override
 	public void onGameCheckedChange(Collection<Long> checkedIds) {
 		boolean hasChecked = checkedIds != null && checkedIds.size() > 0;
 		showDeleteButton(hasChecked);
 	}
	
 	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		final Bundle extras = data == null ? null : data.getExtras();
		switch (requestCode) {
		case GAME_SETUP_ACTIVITY:
			if (resultCode == RESULT_OK && extras != null) {
				mHandler.post(new Runnable() {
					@Override
					public void run() {
						reactToGameSetupActivity(extras);
					}
				});
			}
			break;
		case GAME_DETAIL_ACTIVITY:
			if (resultCode == RESULT_OK && extras != null) {
				mHandler.post(new Runnable() {
					@Override
					public void run() {
						long id = extras.getLong(GameStorageHelper.getCursorItemType(mGameKey), Game.NO_ID);
						setHighlightedGame(id);
						if (extras.getBoolean(EXTRA_RESULT_WANT_REMATCH)) {
							startGameSetup(id);
						}
					}
				});
			}
			break;
		}
	}
 	
 	private void openDataExchangerBluetooth() {
		startActivity(BluetoothDataExchangeActivity.newInstance(this, new int[] {mGameKey}));
	}
	
	private void openFileWriterExchanger() {
		Collection<Long> checkedStarttimes = mOverviewFragment.getCheckedGamesStarttimes();
		Intent i = FileWriteDataExchangeActivity.newInstance(this, mGameKey, true, checkedStarttimes);
		startActivity(i);
	}
	
	private void renamePlayerDialog() {
		closeDetailView(false, false);
        DialogFragment dialog = RenamePlayerDialogFragment.newInstance(new int[] {mGameKey}, GameKey.toArray(mOverviewFragment.getCheckedIds()));
        dialog.show(getSupportFragmentManager(), "RenamePlayerDialogFragment");
	}

	@Override
	public void playerRenamed(String oldName, String newName, boolean success) {
		if (success) {
			Toast.makeText(this, getResources().getString(R.string.rename_success), Toast.LENGTH_SHORT).show();			
		}
	}
	
	@Override
	public void closeDetailView(boolean error, boolean rematch) {
		if (mHandlesDetailFragment) {
			boolean displayedId = mDetailsFragment != null;
			long idToDisplay = Game.NO_ID;
			if (displayedId) {
				idToDisplay = mDetailsFragment.getDisplayedGameId();
			}
			selectGame(Game.NO_ID);
			if (displayedId) {
				setHighlightedGame(idToDisplay);
				if (rematch) {
					startGameSetup(idToDisplay);
				}
			}
		}
	}

	@Override
	public void selectGame(long gameId) {
		if (mHandlesDetailFragment) {
			// Check what fragment is currently shown, replace if needed.
            if (mDetailsFragment == null || gameId == Game.NO_ID || mDetailsFragment.getDisplayedGameId() != gameId) {
                // Execute a transaction, replacing any existing fragment
                // with this one inside the frame.
                FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                if (Game.isValidId(gameId)) {              
                	// Make new fragment to show this selection.
                	if (mDetailsFragment == null) {
                        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
                	} else {
                		ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
                	}
                	mDetailsFragment = GameKey.getNewGameDetailFragmentInstance(mGameKey, gameId);
                    ft.replace(R.id.game_detail_frame, mDetailsFragment);
                } else if (mDetailsFragment != null) {
                    ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE);
                	ft.remove(mDetailsFragment);
                	mDetailsFragment = null;
                }
                ft.commit();
            }
    		setHighlightedGame(gameId);
		} else if (Game.isValidId(gameId)) {
			Intent i = new Intent(this, GameKey.getGameDetailActivity(mGameKey));
			i.putExtra(GameKey.EXTRA_GAMEKEY, mGameKey);
			i.putExtra(GameStorageHelper.getCursorItemType(mGameKey), gameId);
			startActivityForResult(i, GAME_DETAIL_ACTIVITY);			
		}
	}

	@Override
	public long getSelectedGameId() {
		return getHighlightedGame();
	}
	
	protected void loadGameDetails(Bundle extras) {
		if (extras == null) {
			return;
		}
		if (mHandlesDetailFragment) {
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            mDetailsFragment = GameKey.getNewGameDetailFragmentInstance(mGameKey, extras);
        	if (mDetailsFragment == null) {
                ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
        	} else {
        		ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        	}
            ft.replace(R.id.game_detail_frame, mDetailsFragment);
            ft.commit();
    		setHighlightedGame(extras.getLong(GameStorageHelper.getCursorItemType(mGameKey), Game.NO_ID));
		} else {
			Intent i = new Intent(this, GameKey.getGameDetailActivity(mGameKey));
			i.putExtra(GameKey.EXTRA_GAMEKEY, mGameKey);
			i.putExtra(GameDetailActivity.EXTRA_PARAMETER_BUNDLE, extras);
			startActivityForResult(i, GAME_DETAIL_ACTIVITY);
		}
	}
	
	private void setHighlightedGame(long highlightedGameIdHint) {
		long selectedId = (highlightedGameIdHint != Game.NO_ID || mDetailsFragment == null) ? 
				highlightedGameIdHint : 
				mDetailsFragment.getDisplayedGameId();
		mOverviewFragment.setHighlightedGameId(selectedId);
	}
	
	protected long getHighlightedGame() {
		GamesOverviewListFragment frag = (GamesOverviewListFragment) getSupportFragmentManager().findFragmentById(R.id.game_list);
		return frag.getHighlightedGameId();
	}

	@Override
	public PlayerPool getPool() {
		if (mDetailsFragment != null) {
			return mDetailsFragment.getPool();
		} else {
			return null;
		}
	}
	

	@Override
	public List<Player> toFilter() {
		if (mDetailsFragment != null) {
			return mDetailsFragment.toFilter();
		} else {
			return null;
		}
	}

	@Override
	public void playerChosen(int playerIndex, Player chosen) {
		if (mDetailsFragment != null) {
			mDetailsFragment.playerChosen(playerIndex, chosen);
		}
	}

	@Override
	public void onGamesLoaded(int count) {
		if (mIsActivityInitialLaunch && count == 0) {
			startGameSetup(Game.NO_ID);
		}
	}

	@Override
	public void setInfo(CharSequence main, CharSequence extra) {
		if (mDetailsFragment != null) {
			mDetailsFragment.setInfoText(main, extra);
		}
	}

	@Override
	public void onPlayerColorChanged(int arg, Player concernedPlayer) {
		if (mDetailsFragment != null) {
			mDetailsFragment.onPlayerColorChanged(arg, concernedPlayer);
		}
	}

	@Override
	public int getGameKey() {
		return mGameKey;
	}
	
}