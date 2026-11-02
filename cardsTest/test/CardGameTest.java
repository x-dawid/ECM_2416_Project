import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class CardGameTest {
    private static final Pattern WINNER = Pattern.compile(
        "^player (\\d+) wins$", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE
    );
    private static final Pattern DRAW = Pattern.compile(
        "player (\\d+) draws a (\\d+) from deck (\\d+)"
    );
    private static final Pattern DISCARD = Pattern.compile(
        "player (\\d+) discards a (\\d+) to deck (\\d+)"
    );

    @TempDir
    Path temporaryDirectory;

    @Test
    void validatesInputAndAllowsTheUserToTryAgain() throws Exception {
        Path directory = newRunDirectory();
        List<Integer> validValues = immediatePack(4);
        Path valid = writePack(directory, "valid.txt", validValues);
        Path tooShort = writePack(directory, "short.txt", validValues.subList(0, 31));
        List<Integer> extraValues = new ArrayList<>(validValues);
        extraValues.add(1);
        Path tooLong = writePack(directory, "long.txt", extraValues);

        List<String> negativeValues = valuesAsLines(validValues);
        negativeValues.set(5, "-1");
        Path negative = writeLines(directory, "negative.txt", negativeValues);
        List<String> textValues = valuesAsLines(validValues);
        textValues.set(5, "not-a-number");
        Path text = writeLines(directory, "text.txt", textValues);
        List<String> blankValues = valuesAsLines(validValues);
        blankValues.set(5, "");
        Path blank = writeLines(directory, "blank.txt", blankValues);

        String input = "hello\n0\n-2\n4\n"
            + directory.resolve("missing.txt") + "\n"
            + tooShort + "\n" + tooLong + "\n" + negative + "\n"
            + text + "\n" + blank + "\n" + valid + "\n";
        String console = runGame(directory, input);

        assertEquals(3, occurrences(console, "Please enter a positive whole number."));
        assertTrue(console.contains("Cannot read the file:"));
        assertEquals(5, occurrences(console, "Invalid pack:"));
        // This valid pack includes zero and values greater than the player count.
        assertEquals(1, winnerFrom(console, 4));
        assertOutputFiles(directory, 4);

        for (String closedInput : List.of("", "4\n", "hello\n")) {
            Path closedDirectory = newRunDirectory();
            String closedConsole = runGame(closedDirectory, closedInput);
            assertTrue(closedConsole.contains("Input closed. Exiting."));
            assertFalse(WINNER.matcher(closedConsole).find());
            assertFalse(Files.exists(closedDirectory.resolve("player1_output.txt")));
        }
    }

    @Test
    void dealsRoundRobinAndConnectsTheRingIncludingOnePlayer() throws Exception {
        Path directory = newRunDirectory();
        List<Integer> pack = immediatePack(4);
        runWithPack(directory, 4, pack);

        // An immediate win leaves all dealt cards in their original positions.
        for (int player = 1; player <= 4; player++) {
            List<Integer> expectedHand = new ArrayList<>();
            List<Integer> expectedDeck = new ArrayList<>();
            for (int round = 0; round < 4; round++) {
                expectedHand.add(pack.get(round * 4 + player - 1));
                expectedDeck.add(pack.get(16 + round * 4 + player - 1));
            }
            assertEquals(expectedHand, initialHand(playerLines(directory, player), player));
            assertEquals(expectedDeck, deckContents(directory, player));
        }

        for (int numberOfPlayers : List.of(1, 4)) {
            Path ringDirectory = newRunDirectory();
            runWithPack(ringDirectory, numberOfPlayers, circulatingPack(numberOfPlayers));
            for (int player = 1; player <= numberOfPlayers; player++) {
                List<String> lines = playerLines(ringDirectory, player);
                List<Integer> draws = actionValues(lines, DRAW, player, player);
                List<Integer> discards = actionValues(
                    lines, DISCARD, player, player % numberOfPlayers + 1
                );
                assertFalse(draws.isEmpty(), "The ring fixture should involve every player");
                assertEquals(draws.size(), discards.size());
            }
        }
    }

    @Test
    void anImmediateWinPreventsMovesAndWritesAllFiles() throws Exception {
        for (int numberOfPlayers : List.of(1, 4)) {
            Path directory = newRunDirectory();
            String console = runWithPack(directory, numberOfPlayers, immediatePack(numberOfPlayers));

            assertEquals(1, winnerFrom(console, numberOfPlayers));
            assertOutputFiles(directory, numberOfPlayers);
            for (int player = 1; player <= numberOfPlayers; player++) {
                List<String> lines = playerLines(directory, player);
                assertEquals(4, lines.size(), "An immediate win needs four lines per player");
                assertTrue(lines.stream().noneMatch(line -> line.contains(" draws a ")));
                assertTrue(lines.stream().noneMatch(line -> line.contains(" discards a ")));
                assertEquals(initialHand(lines, player), finalHand(lines));
                assertEnding(lines, player, 1);
            }
            assertEquals(List.of(7, 7, 7, 7), finalHand(playerLines(directory, 1)));
        }
    }

    @Test
    void aNormalGameStopsAfterOneWinnerAndConservesEveryCard() throws Exception {
        for (int numberOfPlayers : List.of(1, 2, 4)) {
            Path directory = newRunDirectory();
            List<Integer> pack = circulatingPack(numberOfPlayers);
            // The child process can exit only after its non-daemon player threads finish.
            String console = runWithPack(directory, numberOfPlayers, pack);
            int winner = winnerFrom(console, numberOfPlayers);
            List<Integer> allFinalCards = new ArrayList<>();
            int totalMoves = 0;

            for (int player = 1; player <= numberOfPlayers; player++) {
                List<String> lines = playerLines(directory, player);
                List<Integer> hand = finalHand(lines);
                assertEquals(4, hand.size());
                assertEnding(lines, player, winner);
                if (player == winner) {
                    assertEquals(1, new HashSet<>(hand).size());
                }
                totalMoves += actionValues(lines, DRAW, player, player).size();
                allFinalCards.addAll(hand);
                allFinalCards.addAll(deckContents(directory, player));
            }

            assertTrue(totalMoves > 0, "This fixture must not win immediately");
            assertEquals(8 * numberOfPlayers, allFinalCards.size());
            assertEquals(frequencies(pack), frequencies(allFinalCards));
        }
    }

    @Test
    void outputFilesDescribeTheMovesAndFinalDeckOrderAndAreOverwritten() throws Exception {
        int numberOfPlayers = 4;
        Path directory = newRunDirectory();
        List<Integer> pack = circulatingPack(numberOfPlayers);
        int winner = winnerFrom(runWithPack(directory, numberOfPlayers, pack), numberOfPlayers);
        assertOutputFiles(directory, numberOfPlayers);

        List<List<Integer>> draws = new ArrayList<>();
        List<List<Integer>> discards = new ArrayList<>();
        for (int player = 1; player <= numberOfPlayers; player++) {
            List<String> lines = playerLines(directory, player);
            assertTrue(lines.size() >= 4);
            assertEquals(0, (lines.size() - 4) % 3, "Each move needs three action lines");
            List<Integer> hand = new ArrayList<>(initialHand(lines, player));
            assertEquals(4, hand.size());

            for (int line = 1; line < lines.size() - 3; line += 3) {
                Matcher draw = DRAW.matcher(lines.get(line));
                Matcher discard = DISCARD.matcher(lines.get(line + 1));
                assertTrue(draw.matches(), "Invalid draw line: " + lines.get(line));
                assertTrue(discard.matches(), "Invalid discard line: " + lines.get(line + 1));
                hand.add(Integer.parseInt(draw.group(2)));
                assertTrue(hand.remove(Integer.valueOf(discard.group(2))), "Discarded card was not held");
                assertEquals(4, hand.size());
                assertEquals(hand, handAfterPrefix(
                    lines.get(line + 2), "player " + player + " current hand is "
                ));
            }

            assertEquals(hand, finalHand(lines));
            assertEnding(lines, player, winner);
            draws.add(actionValues(lines, DRAW, player, player));
            discards.add(actionValues(lines, DISCARD, player, player % numberOfPlayers + 1));
        }

        for (int deck = 0; deck < numberOfPlayers; deck++) {
            List<Integer> queue = new ArrayList<>();
            for (int round = 0; round < 4; round++) {
                queue.add(pack.get(4 * numberOfPlayers + round * numberOfPlayers + deck));
            }
            int previousPlayer = (deck + numberOfPlayers - 1) % numberOfPlayers;
            queue.addAll(discards.get(previousPlayer));
            int drawnCount = draws.get(deck).size();
            assertTrue(drawnCount <= queue.size());
            // Each deck has one producer and one consumer, so their logs determine its FIFO order.
            assertEquals(queue.subList(0, drawnCount), draws.get(deck));
            assertEquals(queue.subList(drawnCount, queue.size()), deckContents(directory, deck + 1));
        }

        runWithPack(directory, numberOfPlayers, immediatePack(numberOfPlayers));
        assertOutputFiles(directory, numberOfPlayers);
        for (int player = 1; player <= numberOfPlayers; player++) {
            assertEquals(4, playerLines(directory, player).size(), "Old move logs must be overwritten");
        }
    }

    private Path newRunDirectory() throws IOException {
        return Files.createTempDirectory(temporaryDirectory, "game-");
    }

    private String runWithPack(Path directory, int numberOfPlayers, List<Integer> pack)
            throws Exception {
        Path file = writePack(directory, "pack.txt", pack);
        return runGame(directory, numberOfPlayers + "\n" + file.toAbsolutePath() + "\n");
    }

    private String runGame(Path directory, String input) throws Exception {
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        Path java = Path.of(System.getProperty("java.home"), "bin", executable);
        Path productionClasses = Path.of(CardGame.class.getProtectionDomain()
            .getCodeSource().getLocation().toURI()).toAbsolutePath();
        Path consoleFile = directory.resolve("console.log");
        Process process = new ProcessBuilder(java.toString(), "-cp", productionClasses.toString(), "CardGame")
            .directory(directory.toFile())
            .redirectErrorStream(true)
            .redirectOutput(consoleFile.toFile())
            .start();

        try {
            try (Writer writer = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)) {
                writer.write(input);
            }
            assertTrue(process.waitFor(10, TimeUnit.SECONDS), "Game did not terminate within 10 seconds");
            String console = Files.readString(consoleFile);
            assertEquals(0, process.exitValue(), console);
            assertFalse(console.contains("Exception in thread"), console);
            return console;
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        }
    }

    private Path writePack(Path directory, String name, List<Integer> values) throws IOException {
        return writeLines(directory, name, valuesAsLines(values));
    }

    private Path writeLines(Path directory, String name, List<String> lines) throws IOException {
        Path file = directory.resolve(name);
        Files.writeString(file, String.join("\n", lines) + "\n");
        return file;
    }

    private List<String> valuesAsLines(List<Integer> values) {
        return values.stream().map(String::valueOf).collect(Collectors.toList());
    }

    private List<Integer> immediatePack(int numberOfPlayers) {
        List<Integer> pack = new ArrayList<>();
        for (int round = 0; round < 4; round++) {
            for (int player = 1; player <= numberOfPlayers; player++) {
                pack.add(player == 1 ? 7 : 100 + round * numberOfPlayers + player);
            }
        }
        for (int value = 0; value < 4 * numberOfPlayers; value++) {
            pack.add(value);
        }
        return pack;
    }

    private List<Integer> circulatingPack(int numberOfPlayers) {
        if (numberOfPlayers == 1) {
            return List.of(1, 2, 3, 4, 1, 1, 1, 1);
        }
        List<Integer> pack = new ArrayList<>();
        for (int round = 0; round < 4; round++) {
            for (int player = 0; player < numberOfPlayers; player++) {
                pack.add((player + round) % numberOfPlayers + 1);
            }
        }
        for (int round = 0; round < 4; round++) {
            for (int deck = 0; deck < numberOfPlayers; deck++) {
                pack.add((deck + numberOfPlayers - 1) % numberOfPlayers + 1);
            }
        }
        return pack;
    }

    private int winnerFrom(String console, int numberOfPlayers) {
        Matcher matcher = WINNER.matcher(console);
        assertTrue(matcher.find(), "No winner announcement:\n" + console);
        int winner = Integer.parseInt(matcher.group(1));
        assertTrue(winner >= 1 && winner <= numberOfPlayers);
        assertFalse(matcher.find(), "More than one winner announcement:\n" + console);
        return winner;
    }

    private int occurrences(String text, String target) {
        return (text.length() - text.replace(target, "").length()) / target.length();
    }

    private void assertOutputFiles(Path directory, int numberOfPlayers) throws IOException {
        Set<String> expected = new HashSet<>();
        for (int i = 1; i <= numberOfPlayers; i++) {
            expected.add("player" + i + "_output.txt");
            expected.add("deck" + i + "_output.txt");
        }
        try (Stream<Path> files = Files.list(directory)) {
            Set<String> actual = files.map(path -> path.getFileName().toString())
                .filter(name -> name.endsWith("_output.txt")).collect(Collectors.toSet());
            assertEquals(expected, actual);
        }
    }

    private List<String> playerLines(Path directory, int player) throws IOException {
        return Files.readAllLines(directory.resolve("player" + player + "_output.txt"));
    }

    private List<Integer> initialHand(List<String> lines, int player) {
        return handAfterPrefix(lines.get(0), "player " + player + " initial hand ");
    }

    private List<Integer> finalHand(List<String> lines) {
        String last = lines.get(lines.size() - 1);
        assertTrue(last.contains(": "), "Missing final hand: " + last);
        return numbers(last.substring(last.indexOf(":") + 1));
    }

    private List<Integer> handAfterPrefix(String line, String prefix) {
        assertTrue(line.startsWith(prefix), "Expected '" + prefix + "' in: " + line);
        return numbers(line.substring(prefix.length()));
    }

    private List<Integer> numbers(String text) {
        List<Integer> result = new ArrayList<>();
        if (!text.trim().isEmpty()) {
            for (String number : text.trim().split("\\s+")) {
                result.add(Integer.parseInt(number));
            }
        }
        return result;
    }

    private List<Integer> deckContents(Path directory, int deck) throws IOException {
        List<String> lines = Files.readAllLines(directory.resolve("deck" + deck + "_output.txt"));
        assertEquals(1, lines.size());
        return handAfterPrefix(lines.get(0), "deck" + deck + " contents:");
    }

    private List<Integer> actionValues(List<String> lines, Pattern pattern, int player, int deck) {
        List<Integer> values = new ArrayList<>();
        for (String line : lines) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.matches()) {
                assertEquals(player, Integer.parseInt(matcher.group(1)));
                assertEquals(deck, Integer.parseInt(matcher.group(3)));
                values.add(Integer.parseInt(matcher.group(2)));
            }
        }
        return values;
    }

    private void assertEnding(List<String> lines, int player, int winner) {
        assertTrue(lines.size() >= 4);
        String expected = player == winner ? "player " + player + " wins"
            : "player " + winner + " has informed player " + player
                + " that player " + winner + " has won";
        assertEquals(expected, lines.get(lines.size() - 3));
        assertEquals("player " + player + " exits", lines.get(lines.size() - 2));
        String prefix = "player " + player + (player == winner ? " final hand: " : " hand: ");
        assertTrue(lines.get(lines.size() - 1).startsWith(prefix));
    }

    private Map<Integer, Integer> frequencies(List<Integer> cards) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (int card : cards) {
            counts.merge(card, 1, Integer::sum);
        }
        return counts;
    }
}
