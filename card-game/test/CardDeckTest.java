import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CardDeckTest {
    @Test
    void drawsFromTheTopAndAddsToTheBottom() {
        CardDeck deck = new CardDeck(1, new Object());
        Card first = new Card(3);
        Card second = new Card(5);
        Card third = new Card(7);

        deck.addToBottom(first);
        deck.addToBottom(second);
        assertFalse(deck.isEmpty());
        assertSame(first, deck.drawFromTop());

        // A newly discarded card must go behind cards already in the deck.
        deck.addToBottom(third);
        assertEquals(List.of(second, third), deck.getContents());
        assertSame(second, deck.drawFromTop());
        assertSame(third, deck.drawFromTop());
        assertTrue(deck.isEmpty());
    }

    @Test
    void drawingFromAnEmptyDeckReturnsNull() {
        CardDeck deck = new CardDeck(1, new Object());

        assertNull(deck.drawFromTop());
        assertNull(deck.drawFromTop());
        assertTrue(deck.isEmpty());
        assertEquals(List.of(), deck.getContents());
    }
}
