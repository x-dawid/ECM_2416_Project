import java.util.ArrayList;
import java.util.List;

public class Player {
    private final int playerNumber;
    private final List<Card> hand;

    public Player(int playerNumber, List<Card> initialHand) {
        if (playerNumber <= 0) {
            throw new IllegalArgumentException(
                "Player number must be positive"
            );
        }

        if (initialHand == null || initialHand.size() != 4) {
            throw new IllegalArgumentException(
                "A player must start with exactly four cards"
            );
        }

        for (Card card : initialHand) {
            if (card == null) {
                throw new IllegalArgumentException(
                    "A hand cannot contain a null card"
                );
            }
        }

        this.playerNumber = playerNumber;
        this.hand = new ArrayList<>(initialHand);
    }

    public int getPlayerNumber() {
        return playerNumber;
    }

    public synchronized List<Card> getHand() {
        return List.copyOf(hand);
    }

    public synchronized boolean hasWinningHand() {
        if (hand.size() != 4) {
            return false;
        }

        int firstValue = hand.get(0).getDenomination();

        for (Card card : hand) {
            if (card.getDenomination() != firstValue) {
                return false;
            }
        }

        return true;
    }
}