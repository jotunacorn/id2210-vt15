# id2210-vt15 - SWIM through the NATs

The tests we described in the report are all available and set up.
All you have to do is go to the file SwimMain.java and uncomment the scenario you wish to execute.

Scenarios:
* SwimScenario.simpleBoot - Tests bootstrap without failures.
* SwimScenario.withNodeDeaths - Tests bootstrap and after a defined time nodes will die.
* SwimScenario.withLinkDeaths - Tests bootrstrap and after a defined time links will die.

Test cases by figure number of the report:

Figure 1
* Test cases on lines 75-79 of SwimMain.java.

Figure 2
* Test case on line 77 of SwimMain.java.
* Message size set by constant PIGGYBACK_MESSAGE_SIZE of SwimComp.java
 
Figure 3
* Test case on line 82 of SwimMain.java.
* Message size set by constant PIGGYBACK_MESSAGE_SIZE of SwimComp.java
 
Figure 4
* Test case on line 85 of SwimMain.java.

Figure 5
* Test case on line 77 compared to 97 of SwimMain.java.
* Message size set by constant PIGGYBACK_MESSAGE_SIZE of SwimComp.java

Figure 6
* Test case on line 82 compared to 102 of SwimMain.java.
* Message size set by constant PIGGYBACK_MESSAGE_SIZE of SwimComp.java

Figure 7
* Test case on line 85 compared to 105 of SwimMain.java.

Tests for link deaths are provided on line 88 (100% open) and 108 (50% NATed).
The parameters for the tests are explained further in comments in the code.
