package it.mazzoni.vis.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "oauth_identity",
       uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "providerSubject"}))
public class OAuthIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 30)
    private String provider;

    @Column(nullable = false)
    private String providerSubject;

    private String providerEmail;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getProviderSubject() { return providerSubject; }
    public void setProviderSubject(String providerSubject) { this.providerSubject = providerSubject; }

    public String getProviderEmail() { return providerEmail; }
    public void setProviderEmail(String providerEmail) { this.providerEmail = providerEmail; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
