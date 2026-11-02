# Card Game
A multithreaded Java card game where players will exchange card using a ring of shared decks. 
Each player will be run on a thread of their own, drawing and discard cards until a player hold 4 cards with the same number.

## Requirements
- JDK 21
- JUnit 5.14.4 (included in project)

Check if java is available
```sh
java -version
javac -version
```

## Project structure
```
ECM2414-Project/
|-- cards.jar
|-- pack.txt
|-- ECM2414 Software Development Coursework Report.pdf
|-- cardsTest.zip
|-- cardsTest/                 # Created when cardsTest.zip is extracted
    |-- README.md
    |-- card-game/
        |-- src/
        |   |-- Card.java
        |   |-- CardDeck.java
        |   |-- Player.java
        |   |-- CardGame.java
        |-- test/
        |   |-- CardTest.java
        |   |-- CardDeckTest.java
        |   |-- PlayerTest.java
        |   |-- CardGameTest.java
        |-- lib/
        |   |-- junit-platform-console-standalone-1.14.4.jar
        |-- build/            # Generated .class files for the game and tests
```

## Run the program using cards.jar file (Windwos/Mac/Linux)
Open a terminal/powershell in the ECM2414-Prohject folder, then run command:
```sh
java -jar cards.jar
```
For the supplied pack, enter `4` for number of players.
Enter `pack.txt` for the pack location. A different pack can be also used.


## Pack format and game behaviour
- For `n` players, a pack must have `8 * n` lines.
- Each line contains one non-negative integer.
- The first `4 * n` cards are dealt to the players in a round-robin fashion. 
The remaining cards are dealt round-robin to the decks.
- Player `i` draws from deck `i` and discards to the next deck, wrapping from
  the last deck to deck 1.
- Player will keep the cards matching their player number. And discards the 
oldest card that is not their player number.
- When a player from a hand with 4 of the cards with the same, that player wins, 
even if that number is different from the player's number.
- Starting hands are checked before any turn. When a winner is declared,
  the player threads stops and the final output files are genertated.

A valid pack does not guaranteed to produce a winner. The thread scheduling 
can also change the winner, number of turns and final deck sizes.

## Output files
The game creates these files after the game finishes.

- `playerN_output.txt`: initial hands, draws, discards, current hands, 
the result, exit messages and final hands.

- `deckN_output.txt`: each deck's final contents, in draw order from top to bottom.


## run the tests files (Windwos/Mac/Linux) 
Form the ECM2414 folder, locate the cardsTest.zip folder. Extract the folder and open a terminal/powershell form the cardsTest.zip folder, then run the command:
```sh
java -jar lib/junit-platform-console-standalone-1.14.4.jar execute --class-path card-game/build --scan-class-path --fail-if-no-tests
```

## Test Coverage
There are 12 methods for testing @Test, using JUnit:

CardTest: has 2 tests: 1. Testing for accepted non-negative values 2.  Testing for rejected negative values.

CardDeckTest has 2 tests: 1. Testing for Drawing from the top and adding to the bottom 
2.⁠ ⁠Testing for drawing from an empty deck.

PlayerTest has 3 tests: 1. Testing for Winning hands, preferred-card retention and discarding. 
2.⁠ ⁠Testing for Card Conservation 3. Testing for unchanged state when the input deck is empty.

CardGameTest has 5 tests: 1. Testing for Input validation and retries 2. Testing for round-robin dealing and the connections of decks 3. Testing for immediate wins 4. Testing for termination with one winner and card conservation 5. Testing for deck order and replacement of old logs. 