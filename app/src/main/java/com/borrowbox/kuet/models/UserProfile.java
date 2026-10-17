package com.borrowbox.kuet.models;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class UserProfile implements Serializable {
    private String uid;
    private String fullName;
    private String email;
    private String phoneNumber;
    private String whatsappNumber;
    private String presentAddress;
    private String department;
    private String rollNumber;
    private String crName;
    private String profilePictureUrl;
    private String verificationStatus; // "Not Submitted", "Pending", "Approved", "Rejected"
    private double trustScore = 5.0;
    private long createdAt;
    private long updatedAt;

    public UserProfile() {
        this.verificationStatus = "Not Submitted";
        this.trustScore = 5.0;
    }

    public UserProfile(String uid, String fullName, String email, String phoneNumber,
                       String whatsappNumber, String presentAddress, String department,
                       String rollNumber, String crName, String profilePictureUrl,
                       String verificationStatus) {
        this.uid = uid;
        this.fullName = fullName;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.whatsappNumber = whatsappNumber;
        this.presentAddress = presentAddress;
        this.department = department;
        this.rollNumber = rollNumber;
        this.crName = crName;
        this.profilePictureUrl = profilePictureUrl;
        this.verificationStatus = verificationStatus != null ? verificationStatus : "Not Submitted";
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getFullName() { return fullName != null ? fullName : ""; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email != null ? email : ""; }
    public void setEmail(String email) { this.email = email; }

    public String getPhoneNumber() { return phoneNumber != null ? phoneNumber : ""; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getWhatsappNumber() { return whatsappNumber != null ? whatsappNumber : ""; }
    public void setWhatsappNumber(String whatsappNumber) { this.whatsappNumber = whatsappNumber; }

    public String getPresentAddress() { return presentAddress != null ? presentAddress : ""; }
    public void setPresentAddress(String presentAddress) { this.presentAddress = presentAddress; }

    public String getDepartment() { return department != null ? department : ""; }
    public void setDepartment(String department) { this.department = department; }

    public String getRollNumber() { return rollNumber != null ? rollNumber : ""; }
    public void setRollNumber(String rollNumber) { this.rollNumber = rollNumber; }

    public String getCrName() { return crName != null ? crName : ""; }
    public void setCrName(String crName) { this.crName = crName; }

    public String getProfilePictureUrl() { return profilePictureUrl != null ? profilePictureUrl : ""; }
    public void setProfilePictureUrl(String profilePictureUrl) { this.profilePictureUrl = profilePictureUrl; }

    public String getVerificationStatus() { return verificationStatus != null ? verificationStatus : "Not Submitted"; }
    public void setVerificationStatus(String verificationStatus) { this.verificationStatus = verificationStatus; }

    public boolean isVerified() {
        return "Approved".equalsIgnoreCase(verificationStatus);
    }

    public double getTrustScore() { return trustScore; }
    public void setTrustScore(double trustScore) { this.trustScore = trustScore; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("uid", uid);
        map.put("fullName", fullName);
        map.put("email", email);
        map.put("phoneNumber", phoneNumber);
        map.put("whatsappNumber", whatsappNumber);
        map.put("presentAddress", presentAddress);
        map.put("department", department);
        map.put("rollNumber", rollNumber);
        map.put("crName", crName);
        map.put("profilePictureUrl", profilePictureUrl);
        map.put("verificationStatus", verificationStatus);
        map.put("trustScore", trustScore);
        map.put("createdAt", createdAt);
        map.put("updatedAt", updatedAt);
        return map;
    }
}
