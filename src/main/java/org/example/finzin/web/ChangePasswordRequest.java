package org.example.finzin.web;

public class ChangePasswordRequest {
    public String currentPassword;
    public String newPassword;
    public String confirmPassword;
    public String getCurrentPassword() { return currentPassword; }
    public String getNewPassword() { return newPassword; }
    public String getConfirmPassword() { return confirmPassword; }
}
