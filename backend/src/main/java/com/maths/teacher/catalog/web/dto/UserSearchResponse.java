package com.maths.teacher.catalog.web.dto;

public class UserSearchResponse {

    private final Long id;
    private final String firstName;
    private final String lastName;
    private final String email;
    private final String mobileNumber;

    public UserSearchResponse(Long id, String firstName, String lastName, String email, String mobileNumber) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.mobileNumber = mobileNumber;
    }

    public Long getId() { return id; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public String getMobileNumber() { return mobileNumber; }
    public String getFullName() { return firstName + " " + lastName; }
}
