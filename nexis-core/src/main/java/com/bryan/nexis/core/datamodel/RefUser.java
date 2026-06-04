package com.bryan.nexis.core.datamodel;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "ref_user")
@Serdeable
public class RefUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    // Nullable : les comptes Steam-only n'ont pas de mot de passe local
    @Column(name = "password_hash")
    private String passwordHash;

    // Réservé pour la future connexion via Steam OpenID 2.0
    @Column(name = "steam_id", unique = true)
    private Long steamId;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "ref_user_role",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<RefRole> roles = new HashSet<>();

    /** Avatar de profil (emoji choisi dans une liste prédéfinie). */
    @Column(length = 20)
    private String avatar;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected RefUser() {}

    public RefUser(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
    }

    public UUID getId()                   { return id; }
    public String getUsername()           { return username; }
    public String getPasswordHash()       { return passwordHash; }
    public Long getSteamId()              { return steamId; }
    public Set<RefRole> getRoles()         { return roles; }
    public String getAvatar()             { return avatar; }
    public boolean isEnabled()            { return enabled; }
    public Instant getCreatedAt()         { return createdAt; }

    public void setUsername(String username)       { this.username = username; }
    public void setPasswordHash(String hash)       { this.passwordHash = hash; }
    public void setSteamId(Long steamId)           { this.steamId = steamId; }
    public void setRoles(Set<RefRole> roles)       { this.roles = roles; }
    public void setAvatar(String avatar)           { this.avatar = avatar; }
    public void setEnabled(boolean enabled)        { this.enabled = enabled; }
}
