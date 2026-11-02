import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CardTest {
    @Test
    void storesNonNegativeDenominations() {
        // Zero and numbers above the player count are valid card values.
        for (int value : new int[] {0, 1, 4, 99, Integer.MAX_VALUE}) {
            Card card = new Card(value);
            assertEquals(value, card.getDenomination());
        }
    }

    @Test
    void rejectsNegativeDenominations() {
        assertThrows(IllegalArgumentException.class, () -> new Card(-1));
        assertThrows(IllegalArgumentException.class, () -> new Card(Integer.MIN_VALUE));
    }
}
