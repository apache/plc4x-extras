Feature: Create a fault report
    This test simulates what would happen if you created a log from the 
    CsStudio/Phoebus graphical interface, as well as viewing it from its
    logbook interface.

  Scenario: Maintenance Report
    Given The user enters their credentials: username: "operator" password: "operator"
    And The user selects the "URGENT" reporting level
    And The user selects the "Maintenance" tag and the "Breakdown" category
    And The user adds a description: "Check the power wiring on the local panel"
    And Attached image from the maintenance screen "olog_2132687163876.png"
    And Add a title: "Request for Replacement Parts for Machine XX-YY-ZZ"
    When The user clicks the submit button
    Then Returns the JSON from the created report and an HTTP 200 response


  Scenario: Retrieve reports from the database
    Given The user navigates to the "Log Books" option
    And No search parameters are specified
    When The UI is being updated
    Then The records stored over the last 12 hours are returned

