Feature: eBoard widgets
  Widgets picked from the system widget picker are hosted on the eBoard for
  good: they persist across restarts and can be removed with a long press.

  Background:
    Given a widget provider is installed
    And binding widgets is allowed
    And the eBoard is started

  Scenario: A freshly opened board is empty
    Then the board shows the empty hint

  Scenario: The board is offered as a launcher entry named eBoard
    Then the launcher lists an "eBoard" entry

  Scenario: Adding a widget places it on the board
    When I tap the add widget button
    And the widget adder popover opens
    And I select the widget kind from the widget adder
    Then a widget row is on the board
    And the widget is remembered by the board

  Scenario: Cancelling the popover keeps the board empty
    When I tap the add widget button
    And the widget adder popover opens
    And I dismiss the widget adder popover
    Then the board shows the empty hint

  Scenario: A widget whose provider is gone is dropped from the board
    Given the board remembers a widget without a provider
    When the eBoard is restarted
    Then the board shows the empty hint

  Scenario: Widgets persist on the board across restarts
    When I tap the add widget button
    And the widget adder popover opens
    And I select the widget kind from the widget adder
    When the eBoard is restarted
    Then a widget row is on the board
    And the widget is remembered by the board

  Scenario: Long pressing a widget shows the remove handle and it removes it
    When I tap the add widget button
    And the widget adder popover opens
    And I select the widget kind from the widget adder
    When I long press the widget row
    Then the widget shows a remove handle
    When I tap the remove handle
    Then the board shows the empty hint
    And the widget is forgotten by the board

  Scenario: Adding a configurable widget opens its configuration
    Given the widget kind requires configuration
    When I tap the add widget button
    And the widget adder popover opens
    And I select the widget kind from the widget adder
    Then the widget configuration is requested
    When the configuration succeeds
    Then a widget row is on the board
    And the widget is remembered by the board

  Scenario: Cancelling the widget configuration adds nothing
    Given the widget kind requires configuration
    When I tap the add widget button
    And the widget adder popover opens
    And I select the widget kind from the widget adder
    When the configuration is cancelled
    Then the board shows the empty hint
    And the widget is forgotten by the board

  Scenario: The adder groups widget kinds by app in a grid
    Given another app's widget provider offering "An other widget" is installed
    When I tap the add widget button
    And the widget adder popover opens
    Then the adder groups the kinds under their apps
    And the adder previews sit in a 2-column grid
