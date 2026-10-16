import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class Player implements Runnable {
    private final int playerNumber;
    private final List<Card> hand;

    private final CardDeck inputDeck;
    private final CardDeck outputDeck;
    private final Object gameLock;
    private final CardGame game;
    private final CountDownLatch initialChecks;
    private final AtomicBoolean stopRequested;

    public Player(int playerNumber, List<Card> initialHand) {
        this(playerNumber, initialHand, null, null, new Object());
    }

    public Player(
            int playerNumber,
            List<Card> initialHand,
            CardDeck inputDeck,
            CardDeck outputDeck,
            Object gameLock) {
        this(playerNumber, initialHand, inputDeck, outputDeck,
                gameLock, null, null, null);
    }

    public Player(
            int playerNumber,
            List<Card> initialHand,
            CardDeck inputDeck,
            CardDeck outputDeck,
            Object gameLock,
            CardGame game,
            CountDownLatch initialChecks,
            AtomicBoolean stopRequested) {

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
        this.game = game;
        this.initialChecks = initialChecks;
        this.stopRequested = stopRequested;
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

    @Override
    public void run() {
        if (inputDeck == null || outputDeck == null || game == null
                || initialChecks == null || stopRequested == null) {
            throw new IllegalStateException(
                "Runnable players need decks, game, initial checks and stop flag"
            );
        }

        boolean initialCheckReported = false;
        try {
            synchronized (gameLock) {
                if (!stopRequested.get() && hasWinningHand()) {
                    announceWin();
                }
            }
            initialChecks.countDown();
            initialCheckReported = true;
            initialChecks.await();

            while (true) {
                synchronized (gameLock) {
                    while (!shouldStop() && inputDeck.isEmpty()) {
                        gameLock.wait();
                    }

                    if (shouldStop()) {
                        return;
                    }

                    if (Thread.currentThread().isInterrupted()) {
                        throw new InterruptedException();
                    }

                    takeTurn();
                    gameLock.notifyAll();

                    if (hasWinningHand()) {
                        announceWin();
                    }
                }

                Thread.yield();
            }
        } catch (InterruptedException e) {
            requestStop();
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            requestStop();
            throw e;
        } finally {
            if (!initialCheckReported) {
                initialChecks.countDown();
            }
        }
    }

    private boolean shouldStop() {
        return stopRequested.get() || game.getWinner() != 0;
    }

    private void announceWin() {
        if (game.declareWinner(playerNumber)) {
            System.out.println("player " + playerNumber + " wins");
        }
    }

    private void requestStop() {
        synchronized (gameLock) {
            stopRequested.set(true);
            gameLock.notifyAll();
        }
    }
}
