package org.example.finzin.web;

public class SimplifiedRegisterRequest {
    public String email;
    public String password;
    public String confirmPassword;
    
    public SimplifiedRegisterRequest() {}
    
    // Getters
    public String getEmail() { return email; }
    public String getPassword() { return password; }
    public String getConfirmPassword() { return confirmPassword; }
    
    // Setters
    public void setEmail(String email) { this.email = email; }
    public void setPassword(String password) { this.password = password; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
}
