import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Player {
    private final int playerNumber;
    private final List<Card> hand;

    private final CardDeck inputDeck;
    private final CardDeck outputDeck;
    private final Object gameLock;
    private final Path outputFile;
    private boolean outputStarted = false;

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
        this.outputFile = Path.of("player" + playerNumber + "_output.txt");
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

            if (outputStarted) {
                writeTurnOutput(drawnCard, discardedCard);
            }

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

    public void writeInitialHand() throws IOException {
        synchronized (gameLock) {
            Files.writeString(
                outputFile,
                "player " + playerNumber + " initial hand "
                    + handToString() + System.lineSeparator()
            );
            outputStarted = true;
        }
    }

    public void writeFinalHand(int winningPlayer) throws IOException {
        synchronized (gameLock) {
            if (!outputStarted) {
                throw new IllegalStateException("Write the initial hand first");
            }

            StringBuilder ending = new StringBuilder();

            if (winningPlayer == playerNumber) {
                ending.append("player ").append(playerNumber).append(" wins");
            } else if (winningPlayer > 0) {
                ending.append("player ").append(winningPlayer)
                    .append(" has informed player ").append(playerNumber)
                    .append(" that player ").append(winningPlayer)
                    .append(" has won");
            } else {
                ending.append("player ").append(playerNumber)
                    .append(" game stopped without a winner");
            }

            ending.append(System.lineSeparator())
                .append("player ").append(playerNumber).append(" exits")
                .append(System.lineSeparator())
                .append("player ").append(playerNumber);

            if (winningPlayer == playerNumber) {
                ending.append(" final hand: ");
            } else {
                ending.append(" hand: ");
            }

            ending.append(handToString()).append(System.lineSeparator());
            appendOutput(ending.toString());
        }
    }

    private void writeTurnOutput(Card drawnCard, Card discardedCard) {
        String newline = System.lineSeparator();

        String actions =
            "player " + playerNumber + " draws a "
            + drawnCard.getDenomination() + " from deck "
            + inputDeck.getDeckNumber() + newline
            + "player " + playerNumber + " discards a "
            + discardedCard.getDenomination() + " to deck "
            + outputDeck.getDeckNumber() + newline
            + "player " + playerNumber + " current hand is "
            + handToString() + newline;

        try {
            appendOutput(actions);
        } catch (IOException e) {
            throw new UncheckedIOException(
                "Cannot write player " + playerNumber + " output", e
            );
        }
    }

    private void appendOutput(String text) throws IOException {
        Files.writeString(outputFile, text, StandardOpenOption.APPEND);
    }
    private String handToString() {
        StringBuilder values = new StringBuilder();

        for (Card card : hand) {
            if (values.length() > 0) {
                values.append(' ');
            }
            values.append(card.getDenomination());
        }

        return values.toString();
    }
}
