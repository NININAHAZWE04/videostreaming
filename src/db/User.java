package db;

import java.time.LocalDateTime;
import java.util.Objects;

/** Modèle utilisateur avec statut d'abonnement calculé. */
public final class User {

    private int id;
    private String email;
    private String username;
    private String passwordHash;
    private String passwordSalt;
    private String role;          // "user" | "admin"
    private boolean active;
    private String avatarColor;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;

    // Champs abonnement (jointure, pas en DB directement)
    private String  subPlan;       // "trial" | "monthly" | "annual" | "free" | null
    private String  subStatus;     // "active" | "expired" | "cancelled" | "pending"
    private LocalDateTime subEndsAt;
    private boolean trialUsed;

    public User() {}

    // ─── Accès rôle ────────────────────────────────────────
    public boolean isAdmin() { return "admin".equals(role); }
    public boolean isUser()  { return "user".equals(role) || "admin".equals(role); }

    // ─── Statut abonnement ──────────────────────────────────

    /** True si l'abonnement est actif (trial ou payant, non expiré). */
    public boolean hasActiveSubscription() {
        if (subStatus == null || !"active".equals(subStatus)) return false;
        if (subEndsAt == null) return true; // pas de date de fin = illimité
        return LocalDateTime.now().isBefore(subEndsAt);
    }

    /** Jours restants dans le trial/abonnement. -1 si illimité, 0 si expiré. */
    public int daysRemaining() {
        if (subEndsAt == null) return -1;
        if (!hasActiveSubscription()) return 0;
        long days = java.time.temporal.ChronoUnit.DAYS.between(LocalDateTime.now(), subEndsAt);
        return (int) Math.max(0, days);
    }

    /** True si un nouveau trial est disponible pour cet utilisateur. */
    public boolean canStartTrial() { return !trialUsed && subPlan == null; }

    /** Initiales pour l'avatar. */
    public String initials() {
        if (username == null || username.isBlank()) return "?";
        String[] parts = username.trim().split("\\s+");
        if (parts.length >= 2) return String.valueOf(parts[0].charAt(0)).toUpperCase() + String.valueOf(parts[1].charAt(0)).toUpperCase();
        return String.valueOf(username.charAt(0)).toUpperCase();
    }

    // ─── Getters / Setters ──────────────────────────────────
    public int getId()                       { return id; }
    public void setId(int id)                { this.id = id; }
    public String getEmail()                 { return email; }
    public void setEmail(String email)       { this.email = email; }
    public String getUsername()              { return username; }
    public void setUsername(String u)        { this.username = u; }
    public String getPasswordHash()          { return passwordHash; }
    public void setPasswordHash(String h)    { this.passwordHash = h; }
    public String getPasswordSalt()          { return passwordSalt; }
    public void setPasswordSalt(String s)    { this.passwordSalt = s; }
    public String getRole()                  { return role; }
    public void setRole(String role)         { this.role = role; }
    public boolean isActive()                { return active; }
    public void setActive(boolean active)    { this.active = active; }
    public String getAvatarColor()           { return avatarColor; }
    public void setAvatarColor(String c)     { this.avatarColor = c; }
    public LocalDateTime getCreatedAt()      { return createdAt; }
    public void setCreatedAt(LocalDateTime t){ this.createdAt = t; }
    public LocalDateTime getLastLoginAt()    { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime t){ this.lastLoginAt = t; }
    public String getSubPlan()               { return subPlan; }
    public void setSubPlan(String p)         { this.subPlan = p; }
    public String getSubStatus()             { return subStatus; }
    public void setSubStatus(String s)       { this.subStatus = s; }
    public LocalDateTime getSubEndsAt()      { return subEndsAt; }
    public void setSubEndsAt(LocalDateTime t){ this.subEndsAt = t; }
    public boolean isTrialUsed()             { return trialUsed; }
    public void setTrialUsed(boolean t)      { this.trialUsed = t; }
}
