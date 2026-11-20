Feature: Package changes
  As an eInk device user
  I want the launcher to react immediately when apps are installed or uninstalled
  So that the drawer, the home screen links and the eBoard stay truthful

  Background:
    Given usage access onboarding is already done
    And the launcher has these apps installed:
      | Alpha |
      | Beta  |
      | Gamma |

  Scenario: An app installed while the drawer is open appears immediately
    Given the launcher is started
    And the app drawer is open
    When "Delta" is installed
    Then "Delta" appears in the drawer

  Scenario: An app uninstalled while the drawer is open disappears immediately
    Given the launcher is started
    And the app drawer is open
    When "Beta" is uninstalled
    Then "Beta" does not appear in the drawer

  Scenario: An app updated while the drawer is open stays listed
    Given the launcher is started
    And the app drawer is open
    When "Beta" is updated
    Then "Beta" appears in the drawer

  Scenario: Uninstalling an app resets its home slot to the default
    Given the home screen has 2 slots configured
    And home slot 0 is assigned to "Beta"
    And the launcher is started
    When "Beta" is uninstalled
    Then home slot 0 is labeled "App"
    And the assignment for slot 0 is cleared

  Scenario: Widgets of an uninstalled app are removed from the board
    Given a widget provider app "Widgetapp" is installed
    And a widget of that provider is on the board
    And the launcher is started
    When "Widgetapp" is uninstalled
    Then the widget is forgotten by the board
