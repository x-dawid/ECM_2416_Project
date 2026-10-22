import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class PlayerTest {
    @Test
    void recognisesFourEqualCardsIncludingNonPreferredValues() {
        assertTrue(new Player(1, hand(1, 1, 1, 1)).hasWinningHand());
        assertTrue(new Player(1, hand(7, 7, 7, 7)).hasWinningHand());
        assertTrue(new Player(1, hand(0, 0, 0, 0)).hasWinningHand());
        assertFalse(new Player(1, hand(2, 2, 3, 4)).hasWinningHand());
        assertFalse(new Player(1, hand(2, 2, 2, 3)).hasWinningHand());
        assertFalse(new Player(1, hand(1, 2, 3, 4)).hasWinningHand());
    }

    @Test
    void turnsKeepPreferredCardsAndConserveCards() {
        Object gameLock = new Object();
        CardDeck input = new CardDeck(1, gameLock);
        CardDeck output = new CardDeck(2, gameLock);
        List<Card> initialHand = hand(3, 1, 2, 1);
        Card firstDraw = new Card(1);
        Card secondDraw = new Card(1);
        Card alreadyInOutput = new Card(9);
        input.addToBottom(firstDraw);
        input.addToBottom(secondDraw);
        output.addToBottom(alreadyInOutput);
        Player player = new Player(1, initialHand, input, output, gameLock);
        Map<Card, Integer> originalCards = countCards(player, input, output);

        assertTrue(player.takeTurn());
        // This implementation discards the oldest unwanted card: first 3, then 2.
        assertEquals(List.of(initialHand.get(1), initialHand.get(2),
            initialHand.get(3), firstDraw), player.getHand());
        assertEquals(List.of(secondDraw), input.getContents());
        assertEquals(List.of(alreadyInOutput, initialHand.get(0)), output.getContents());
        assertEquals(4, player.getHand().size());
        assertEquals(originalCards, countCards(player, input, output));

        assertTrue(player.takeTurn());
        assertEquals(List.of(initialHand.get(1), initialHand.get(3),
            firstDraw, secondDraw), player.getHand());
        assertEquals(List.of(alreadyInOutput, initialHand.get(0), initialHand.get(2)),
            output.getContents());
        assertTrue(input.isEmpty());
        assertEquals(4, player.getHand().size());
        assertTrue(player.hasWinningHand());
        assertEquals(originalCards, countCards(player, input, output));
    }

    @Test
    void anEmptyInputDeckLeavesTheHandAndDecksUnchanged() {
        Object gameLock = new Object();
        CardDeck input = new CardDeck(1, gameLock);
        CardDeck output = new CardDeck(2, gameLock);
        List<Card> initialHand = hand(1, 2, 3, 4);
        Card existingCard = new Card(8);
        output.addToBottom(existingCard);
        Player player = new Player(1, initialHand, input, output, gameLock);

        assertFalse(player.takeTurn());
        assertEquals(initialHand, player.getHand());
        assertTrue(input.isEmpty());
        assertEquals(List.of(existingCard), output.getContents());
    }

    private List<Card> hand(int... values) {
        List<Card> cards = new ArrayList<>();
        for (int value : values) {
            cards.add(new Card(value));
        }
        return cards;
    }

    private Map<Card, Integer> countCards(Player player, CardDeck input, CardDeck output) {
        List<Card> allCards = new ArrayList<>(player.getHand());
        allCards.addAll(input.getContents());
        allCards.addAll(output.getContents());
        Map<Card, Integer> counts = new HashMap<>();
        for (Card card : allCards) {
            counts.merge(card, 1, Integer::sum);
        }
        return counts;
    }
}
