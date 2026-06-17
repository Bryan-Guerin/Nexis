package com.bryan.nexis.sapeurs.datamodel;

import com.bryan.nexis.core.datamodel.RefUser;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "sp_membre")
public class SpMembre {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private RefUser user;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "grade_id", nullable = false)
    private SpGrade grade;

    /** SPP ou SPV */
    @Column(name = "contrat", nullable = false, length = 3)
    private String contrat;

    /** Numéro de casier 0–30 */
    @Column(name = "numero_casier", nullable = false)
    private int numeroCasier;

    /**
     * Compteur commun SPP/SPV, débutant à 352.
     * Le matricule est toujours dérivé : {@code contrat + "-" + sprintf("%03d", numeroCompteur)}.
     */
    @Column(name = "numero_compteur", nullable = false, unique = true)
    private int numeroCompteur;

    @Column(nullable = false, unique = true, length = 20)
    private String matricule;

    @Column(nullable = false)
    private boolean actif = true;

    /** Nom/prénom libre (un seul champ), distinct du login. */
    @Column(name = "nom_complet", length = 100)
    private String nomComplet;

    /** Numéro de téléphone (optionnel, 10 chiffres). */
    @Column(name = "telephone", length = 10)
    private String telephone;

    /** Date d'entrée dans la caserne (= création de l'effectif). */
    @Column(name = "date_integration", nullable = false, updatable = false)
    private Instant dateIntegration = Instant.now();

    /** Date du dernier changement de grade (promotion). */
    @Column(name = "date_derniere_promotion")
    private Instant dateDernierePromotion = Instant.now();

    /** Qualifications (habilitations) du membre, avec date et délivreur. */
    @OneToMany(mappedBy = "membre", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<SpMembreQualification> qualifications = new HashSet<>();

    /** Fonctions d'organigramme cumulées par le membre (Chef de centre, RH, Formateur…). */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "sp_membre_fonction_orga",
            joinColumns = @JoinColumn(name = "membre_id"),
            inverseJoinColumns = @JoinColumn(name = "fonction_orga_id"))
    private Set<SpFonctionOrga> fonctionsOrga = new HashSet<>();

    protected SpMembre() {}

    public SpMembre(RefUser user, SpGrade grade, String contrat, int numeroCasier, int numeroCompteur) {
        this.user           = user;
        this.grade          = grade;
        this.contrat        = contrat;
        this.numeroCasier   = numeroCasier;
        this.numeroCompteur = numeroCompteur;
        this.matricule      = buildMatricule(contrat, numeroCompteur);
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public UUID getId()                                 { return id; }
    public RefUser getUser()                            { return user; }
    public SpGrade getGrade()                           { return grade; }
    public String getContrat()                          { return contrat; }
    public int getNumeroCasier()                        { return numeroCasier; }
    public int getNumeroCompteur()                      { return numeroCompteur; }
    public String getMatricule()                        { return matricule; }
    public boolean isActif()                            { return actif; }
    public String getNomComplet()                       { return nomComplet; }
    public String getTelephone()                        { return telephone; }
    public Instant getDateIntegration()                 { return dateIntegration; }
    public Instant getDateDernierePromotion()           { return dateDernierePromotion; }
    public Set<SpMembreQualification> getQualifications() { return qualifications; }
    public Set<SpFonctionOrga> getFonctionsOrga()         { return fonctionsOrga; }

    // ── Setters ──────────────────────────────────────────────────────────────

    public void setGrade(SpGrade grade)                             { this.grade = grade; }
    public void setActif(boolean actif)                             { this.actif = actif; }
    public void setNomComplet(String nomComplet)                    { this.nomComplet = nomComplet; }
    public void setTelephone(String telephone)                      { this.telephone = telephone; }
    public void setNumeroCasier(int numeroCasier)                   { this.numeroCasier = numeroCasier; }
    public void setDateDernierePromotion(Instant d)                 { this.dateDernierePromotion = d; }

    /** Recalcule automatiquement le matricule lors du changement de contrat. */
    public void setContrat(String contrat) {
        this.contrat   = contrat;
        this.matricule = buildMatricule(contrat, this.numeroCompteur);
    }

    // ── Représentations ───────────────────────────────────────────────────────

    /** Libellé lisible pour l'historique / la main courante / le BIP (sans identifiants). */
    @Override
    public String toString() {
        return matricule + " " + (nomComplet != null && !nomComplet.isBlank() ? nomComplet : user.getUsername());
    }

    /** Représentation technique pour les logs applicatifs (avec identifiants). */
    public String toStringLog() {
        return "SpMembre[id=" + id + ", matricule=" + matricule
                + ", user=" + user.getUsername() + "#" + user.getId()
                + ", grade=" + grade.getLabel() + "]";
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static String buildMatricule(String contrat, int compteur) {
        return contrat + "-" + String.format("%03d", compteur);
    }
}
