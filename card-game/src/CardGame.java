import java.io.EOFException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class CardGame {
    private final Object gameLock = new Object();
    private final List<Player> players = new ArrayList<>();
    private final List<CardDeck> decks = new ArrayList<>();
    private int winningPlayer = 0;
    private final List<Thread> playerThreads = new ArrayList<>();
    private int initialHandsChecked = 0;
    private boolean stopping = false;
    private RuntimeException playerFailure;

    private CardGame(int numberOfPlayers, List<Card> pack) {
        for (int i = 0; i < numberOfPlayers; i++) {
            decks.add(new CardDeck(i + 1, gameLock));
        }

        for (int i = 0; i < numberOfPlayers; i++) {
            List<Card> hand = new ArrayList<>();

            for (int round = 0; round < 4; round++) {
                int cardIndex = round * numberOfPlayers + i;
                hand.add(pack.get(cardIndex));
            }

            players.add(new Player(
                i + 1,
                hand,
                decks.get(i),
                decks.get((i + 1) % numberOfPlayers),
                gameLock
            ));
        }

        int firstDeckCard = 4 * numberOfPlayers;

        for (int i = firstDeckCard; i < pack.size(); i++) {
            int deckIndex = (i - firstDeckCard) % numberOfPlayers;
            decks.get(deckIndex).addToBottom(pack.get(i));
        }
    }

    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in)) {
            int numberOfPlayers = readPlayerCount(scanner);
            List<Card> pack = requestPack(scanner, numberOfPlayers);

            CardGame game = new CardGame(numberOfPlayers, pack);

            game.play();
        } catch (EOFException e) {
            System.out.println("Input closed. Exiting.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Game interrupted. All players have stopped.");
        } catch (IOException e) {
            System.err.println("Cannot write deck output: " + e.getMessage());
        } catch (IllegalStateException e) {
            System.err.println("Game stopped: " + e.getMessage());
        }
    }

    private static int readPlayerCount(Scanner scanner) throws EOFException {
        while (true) {
            System.out.println("Please enter the number of players:");

            if (!scanner.hasNextLine()) {
                throw new EOFException();
            }

            try {
                int number = Integer.parseInt(
                    scanner.nextLine().trim()
                );

                if (number > 0) {
                    return number;
                }
            } catch (NumberFormatException e) {
                // Invalid text falls through to the message below.
            }

            System.out.println("Please enter a positive whole number.");
        }
    }

    private static List<Card> requestPack(
            Scanner scanner, int numberOfPlayers) throws EOFException {
        while (true) {
            System.out.println(
                "Please enter the location of the pack file:"
            );

            if (!scanner.hasNextLine()) {
                throw new EOFException();
            }

            String filename = scanner.nextLine().trim();

            try {
                return loadPack(filename, numberOfPlayers);
            } catch (IOException e) {
                System.out.println(
                    "Cannot read the file: " + e.getMessage()
                );
            } catch (IllegalArgumentException e) {
                System.out.println(
                    "Invalid pack: " + e.getMessage()
                );
            }
        }
    }

    private static List<Card> loadPack(
            String filename, int numberOfPlayers) throws IOException {
        List<String> lines = Files.readAllLines(Path.of(filename));
        long expectedCards = 8L * numberOfPlayers;

        if (lines.size() != expectedCards) {
            throw new IllegalArgumentException(
                "The pack must contain exactly "
                + expectedCards + " lines."
            );
        }

        List<Card> pack = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            int value;

            try {
                value = Integer.parseInt(lines.get(i).trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                    "Line " + (i + 1)
                    + " must contain a whole number."
                );
            }

            pack.add(new Card(value));
        }

        return pack;
    }

    private void play() throws InterruptedException, IOException {
        for (Player player : players) {
            Thread thread = new Thread(
                () -> runPlayer(player),
                "player" + player.getPlayerNumber()
            );
            playerThreads.add(thread);
        }

        for (Thread thread : playerThreads) {
            thread.start();
        }

        waitForPlayers();

        if (playerFailure != null) {
            throw new IllegalStateException(
                "A player failed: " + playerFailure.getMessage(),
                playerFailure
            );
        }

        writeDeckFiles();
    }

    private void runPlayer(Player player) {
        try {
            synchronized (gameLock) {
                if (player.hasWinningHand()
                        && declareWinner(player.getPlayerNumber())) {
                    System.out.println(
                        "player " + player.getPlayerNumber() + " wins"
                    );
                }

                initialHandsChecked++;
                gameLock.notifyAll();

                // No ordinary moves until every starting hand is checked.
                while (initialHandsChecked < players.size()
                        && winningPlayer == 0 && !stopping) {
                    gameLock.wait();
                }
            }

            while (true) {
                synchronized (gameLock) {
                    if (winningPlayer != 0 || stopping) {
                        return;
                    }

                    if (!player.takeTurn()) {
                        // Waiting releases the lock so other players can move.
                        gameLock.wait();
                        continue;
                    }

                    if (player.hasWinningHand()
                            && declareWinner(player.getPlayerNumber())) {
                        System.out.println(
                            "player " + player.getPlayerNumber() + " wins"
                        );
                    }

                    // A discard may allow a player with an empty deck to move.
                    gameLock.notifyAll();
                }
            }
        } catch (InterruptedException e) {
            stopGame();
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            synchronized (gameLock) {
                if (playerFailure == null) {
                    playerFailure = e;
                }
                stopGame();
            }
        }
    }

    private void waitForPlayers() throws InterruptedException {
        boolean interrupted = false;

        // Join outside gameLock: players need that lock to finish.
        for (Thread thread : playerThreads) {
            while (thread.isAlive()) {
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    interrupted = true;
                    stopGame();
                    for (Thread playerThread : playerThreads) {
                        playerThread.interrupt();
                    }
                }
            }
        }

        if (interrupted) {
            Thread.currentThread().interrupt();
            throw new InterruptedException("Game interrupted");
        }
    }

    private void stopGame() {
        synchronized (gameLock) {
            stopping = true;
            gameLock.notifyAll();
        }
    }

    private void writeDeckFiles() throws IOException {
        for (CardDeck deck : decks) {
            StringBuilder contents = new StringBuilder(
                "deck" + deck.getDeckNumber() + " contents:"
            );

            for (Card card : deck.getContents()) {
                contents.append(' ').append(card.getDenomination());
            }

            Path output = Path.of(
                "deck" + deck.getDeckNumber() + "_output.txt"
            );
            Files.writeString(output, contents + System.lineSeparator());
        }
    }

    public int getWinner() {
        synchronized (gameLock) {
            return winningPlayer;
        }
    }

    public boolean declareWinner(int playerNumber) {
        synchronized (gameLock) {
            if (playerNumber < 1 || playerNumber > players.size()) {
                throw new IllegalArgumentException(
                    "Invalid player number"
                );
            }

            if (winningPlayer != 0 || stopping) {
                return false;
            }

            winningPlayer = playerNumber;
            gameLock.notifyAll();
            return true;
        }
    }
}
