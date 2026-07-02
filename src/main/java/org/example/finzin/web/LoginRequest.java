package org.example.finzin.web;

public class LoginRequest {
    public String usernameOrEmail;
    public String password;
    
    public LoginRequest() {}
    
    // Getters
    public String getUsernameOrEmail() { return usernameOrEmail; }
    public String getPassword() { return password; }
    
    // Setters
    public void setUsernameOrEmail(String usernameOrEmail) { this.usernameOrEmail = usernameOrEmail; }
    public void setPassword(String password) { this.password = password; }
}

