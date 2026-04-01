package com.maths.teacher.catalog.web.dto;

public class RegisterStudentRequest {

    private String firstName;
    private String lastName;
    private String mobileNumber;
    private String email;
    private String password;

    public RegisterStudentRequest() {}

    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getMobileNumber() { return mobileNumber; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }
}
