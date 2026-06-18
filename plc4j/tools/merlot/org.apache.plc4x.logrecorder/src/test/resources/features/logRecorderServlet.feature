Feature: Create a fault report
  Scenario: Maintenance Report
    Given The user enters their credentials: username: "operator" password: "operator"
    And The user selects the "Maintenance" tag and the "Breakdown" category
    And Attached image from the maintenance screen "olog_2132687163876.png"
    And Add a title: "Request for Replacement Parts for Machine XX-YY-ZZ"
    When When the user clicks the submit button
    Then The service  saves that report to the database
    And Returns the JSON from the created report and an HTTP 200 response