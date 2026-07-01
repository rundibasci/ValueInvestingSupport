package it.mazzoni.vis.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_competence_preferences")
public class UserCompetencePreferences {
    @Id
    @Column(name = "user_id")
    private UUID userId;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;
    @Column(columnDefinition = "TEXT")
    private String preferredSectors;
    @Column(columnDefinition = "TEXT")
    private String competenceIndustries;
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() { updatedAt = LocalDateTime.now(); }

    public UUID getUserId() { return userId; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getPreferredSectors() { return preferredSectors; }
    public void setPreferredSectors(String preferredSectors) { this.preferredSectors = preferredSectors; }
    public String getCompetenceIndustries() { return competenceIndustries; }
    public void setCompetenceIndustries(String competenceIndustries) { this.competenceIndustries = competenceIndustries; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
