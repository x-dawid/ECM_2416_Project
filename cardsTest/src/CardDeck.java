import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

public class CardDeck {
    private final int deckNumber;
    private final Deque<Card> cards;
    private final Object gameLock;

    public CardDeck(int deckNumber, Object gameLock) {
        if (deckNumber < 1) {
            throw new IllegalArgumentException(
                "Deck number must be positive"
            );
        }

        this.deckNumber = deckNumber;
        this.gameLock = Objects.requireNonNull(gameLock);
        this.cards = new ArrayDeque<>();
    }

    public int getDeckNumber() {
        return deckNumber;
    }

    public void addToBottom(Card card) {
        synchronized (gameLock) {
            cards.addLast(Objects.requireNonNull(card));
        }
    }

    public Card drawFromTop() {
        synchronized (gameLock) {
            return cards.pollFirst();
        }
    }

    public boolean isEmpty() {
        synchronized (gameLock) {
            return cards.isEmpty();
        }
    }

    public List<Card> getContents() {
        synchronized (gameLock) {
            return new ArrayList<>(cards);
        }
    }
}