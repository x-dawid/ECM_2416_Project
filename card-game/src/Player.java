import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Player {
    private final int playerNumber;
    private final List<Card> hand;

    private final CardDeck inputDeck;
    private final CardDeck outputDeck;
    private final Object gameLock;

    public Player(int playerNumber, List<Card> initialHand) {
        this(playerNumber, initialHand, null, null, new Object());
    }

    public Player(
            int playerNumber,
            List<Card> initialHand,
            CardDeck inputDeck,
            CardDeck outputDeck,
            Object gameLock) {

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

        if ((inputDeck == null) != (outputDeck == null)) {
            throw new IllegalArgumentException(
                "Provide both decks or neither deck"
            );
        }

        this.playerNumber = playerNumber;
        this.hand = new ArrayList<>(initialHand);
        this.inputDeck = inputDeck;
        this.outputDeck = outputDeck;
        this.gameLock = Objects.requireNonNull(
            gameLock, "Game lock cannot be null"
        );
    }

    public int getPlayerNumber() {
        return playerNumber;
    }

    public List<Card> getHand() {
        synchronized (gameLock) {
            return List.copyOf(hand);
        }
    }

    public boolean hasWinningHand() {
        synchronized (gameLock) {
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

    public boolean takeTurn() {
        if (inputDeck == null || outputDeck == null) {
            throw new IllegalStateException(
                "Player must be connected to decks before playing"
            );
        }

        synchronized (gameLock) {
            if (hasWinningHand()) {
                return false;
            }

            Card drawnCard = inputDeck.drawFromTop();

            if (drawnCard == null) {
                return false;
            }

            hand.add(drawnCard);

            int discardIndex = findOldestNonPreferredCard();
            Card discardedCard = hand.remove(discardIndex);

            outputDeck.addToBottom(discardedCard);

            return true;
        }
    }

    private int findOldestNonPreferredCard() {
        for (int i = 0; i < hand.size(); i++) {
            if (hand.get(i).getDenomination() != playerNumber) {
                return i;
            }
        }

        throw new IllegalStateException(
            "No non-preferred card is available to discard"
        );
    }
}