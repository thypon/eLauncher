Feature: Home screen slots
  As an eInk device user
  I want a configurable set of app slots on the home screen
  So that my favourite apps are one tap away

  Background:
    Given usage access onboarding is already done
    And the launcher has these apps installed:
      | Alpha |
      | Beta  |
      | Gamma |

  Scenario: The home screen shows one slot per configured app
    Given the home screen has 3 slots configured
    And the launcher is started
    Then the home screen shows 3 slots
    And home slot 0 is labeled "App"

  Scenario: No slots are shown when the count is set to zero
    Given the home screen has 0 slots configured
    And the launcher is started
    Then the home screen shows 0 slots

  Scenario: An unassigned slot launches nothing
    Given the home screen has 2 slots configured
    And the launcher is started
    When I tap home slot 0
    Then no app was launched

  Scenario: An assigned slot launches its app
    Given the home screen has 2 slots configured
    And home slot 0 is assigned to "Beta"
    And the launcher is started
    When I tap home slot 0
    Then the "com.fixtures.beta" app is launched

  Scenario: Long-pressing a slot opens the app picker and persists the assignment
    Given the home screen has 2 slots configured
    And the launcher is started
    When I long-press home slot 0
    And I select "Beta" in the app picker
    And I confirm the suggested name
    Then home slot 0 is labeled "Beta"
    And the assignment for slot 0 is stored with app "Beta"

  Scenario: Assigning a slot with a custom name persists the custom name
    Given the home screen has 2 slots configured
    And the launcher is started
    When I long-press home slot 1
    And I select "Gamma" in the app picker
    And I replace the name with "My Gamma" and confirm
    Then home slot 1 is labeled "My Gamma"
    And the assignment for slot 1 is stored with app "Gamma"