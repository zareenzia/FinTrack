package org.example.finzin.web;

public class ProfileUpdateRequest {
    public String fullName;
    public String username;
    
    public ProfileUpdateRequest() {}
    
    // Getters
    public String getFullName() { return fullName; }
    public String getUsername() { return username; }
    
    // Setters
    public void setFullName(String fullName) { this.fullName = fullName; }
    public void setUsername(String username) { this.username = username; }
}
