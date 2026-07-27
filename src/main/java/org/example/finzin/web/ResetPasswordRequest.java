package org.example.finzin.web;

public class ResetPasswordRequest {
    public String token;
    public String newPassword;
    public String confirmPassword;
    public String getToken() { return token; }
    public String getNewPassword() { return newPassword; }
    public String getConfirmPassword() { return confirmPassword; }
}
