package dan.dit.gameMemo.dataExchange;

import java.util.List;

import android.content.ContentResolver;
/**
 * A egoistic {@link GameDataExchanger}. Does not offer any data and requests
 * all available games. Used to fetch data from a normal GameDataExchanger without
 * sending anything itself.
 * @author Daniel
 *
 */
public class GameDataExchangerEgoistic extends GameDataExchanger {

	private boolean mIgnoreReceivedGames;

	public GameDataExchangerEgoistic(ContentResolver resolver,
			ExchangeService service, int gameKey) {
		super(resolver, service, gameKey);
	}

	protected List<Long> filterReceivedOffer(List<Long> receivedTimes) {
		return receivedTimes; // request all games
	}
	
	public void setIgnoreReceivedGames(boolean ignore) {
		mIgnoreReceivedGames = ignore;
	}

	protected List<Long> getOffer() {
		return null; // do not offer anything
	}

	@Override
	protected void onReceiveGames(String message) {
		if (!mIgnoreReceivedGames) {
			super.onReceiveGames(message);
		}
	}
}
