public class Card {
    private final int denomination;

    public Card(int denomination) {
        if (denomination < 0 ){
            throw new IllegalArgumentException(
                "Card denomination cannot be negative"
            );
        }
        this.denomination = denomination;    
    }

    public int getDenomination() {
        return denomination;
    }
    //main method to just test the functionality of denomination
    public static void main(String[] args) {
    Card card = new Card(4);
    System.out.println(card.getDenomination());
    }
}

    