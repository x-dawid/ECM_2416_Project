# Card Game
A multithreaded Java card game where players will exchange card using a ring of shared decks. 
Each player will be run on a thread of their own, drawing and discard cards until a player hold 4 cards with the same number.

## Requirements
- JDK 21
- JUnit 5 (included in project)

Check if java is available

```sh
java -version
javac -version
```

## Project structure
```
ECM_2416_Project/
|-- README.md
|-- .gitignore
|-- settings.json
|-- pack.txt
|-- card-game/
    |-- src/
    |   |-- Card.java
    |   |-- CardDeck.java
    |   |-- Player.java
    |   |-- CardGame.java
    |--test/
    |   |-- CardTest.java
    |   |-- CardDeckTest.java
    |   |-- PlayerTest.java
    |   |-- CardGameTest.java
    |-- lib/
    |   |-- junit-platform-console-standalone-1.14.4.jar
    |-- build/       #Generated .class files for the game and tests
```


## Compile and run on macOS/Linux
Open a Terminal in the repository root.

```sh
mkdir -p card-game/build
javac --release 21 -encoding UTF-8 -d card-game/build card-game/src/*.java
java -cp card-game/build CardGame
```

For the supplied pack, enter `4` for number of players.
Enter `pack.txt` for the pack location. A different pack can be also used.


## Compile and run on Windows
Open PowerShell in the repository root:

```powershell
New-Item -ItemType Directory -Force card-game/build | Out-Null
$gameSources = (Get-ChildItem card-game/src/*.java).FullName
javac --release 21 -encoding UTF-8 -d card-game/build $gameSources
java -cp card-game/build CardGame
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


## How to run the tests if using macOS or Linux: 
Run this command in terminal and run the tests.

```sh
javac --release 21 -encoding UTF-8 -cp 'card-game/build:card-game/lib/junit-platform-console-standalone-1.14.4.jar' -d card-game/build card-game/test/*.java
java -jar card-game/lib/junit-platform-console-standalone-1.14.4.jar execute --class-path card-game/build --select-class CardTest --select-class CardDeckTest --select-class PlayerTest --select-class CardGameTest --fail-if-no-tests
```


## How to run the tests if using Windows:
Run this command in Windows PowerShell and run tests.

```powershell
$testSources = (Get-ChildItem card-game/test/*.java).FullName
javac --release 21 -encoding UTF-8 -cp 'card-game/build;card-game/lib/junit-platform-console-standalone-1.14.4.jar' -d card-game/build $testSources
java -jar card-game/lib/junit-platform-console-standalone-1.14.4.jar execute --class-path card-game/build --select-class CardTest --select-class CardDeckTest --select-class PlayerTest --select-class CardGameTest --fail-if-no-tests
```


## Test Coverage
There are 12 methods for testing @Test, using JUnit:

CardTest: has 2 tests: 1. Testing for accepted non-negative values 2.  Testing for rejected negative values.

CardDeckTest has 2 tests: 1. Testing for Drawing from the top and adding to the bottom 
2.⁠ ⁠Testing for drawing from an empty deck.

PlayerTest has 3 tests: 1. Testing for Winning hands, preferred-card retention and discarding. 
2.⁠ ⁠Testing for Card Conservation 3. Testing for unchanged state when the input deck is empty.

CardGameTest has 5 tests: 1. Testing for Input validation and retries 2. Testing for round-robin dealing and the connections of decks 3. Testing for immediate wins 4. Testing for termination with one winner and card conservation 5. Testing for deck order and replacement of old logs. 


## Running in VS Code
Open the complete folder containing the repository. The shared .vscode/settings.json configures the source folders, JUnit library and the single card-game/build/ output folder.

To use the Run Test buttons or the Testing panel, install the Test Runner for Java in VS Code, its also included in the regular extension pack for java. Make sure to select your own JDK21 use the command with ctrl p, Java: Configure Java Runtime.