package com.bryan.nexis.gendarmerie.datamodel;

import com.bryan.nexis.core.datamodel.RefUser;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "gn_membre")
public class GnMembre {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private RefUser user;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "grade_id", nullable = false)
    private GnGrade grade;

    @Column(nullable = false, unique = true, length = 20)
    private String matricule;

    @Column(nullable = false)
    private boolean actif = true;

    protected GnMembre() {}

    public GnMembre(RefUser user, GnGrade grade, String matricule) {
        this.user      = user;
        this.grade     = grade;
        this.matricule = matricule;
    }

    public UUID getId()          { return id; }
    public RefUser getUser()     { return user; }
    public GnGrade getGrade()    { return grade; }
    public String getMatricule() { return matricule; }
    public boolean isActif()     { return actif; }

    public void setGrade(GnGrade grade)         { this.grade = grade; }
    public void setMatricule(String matricule)  { this.matricule = matricule; }
    public void setActif(boolean actif)         { this.actif = actif; }
}
