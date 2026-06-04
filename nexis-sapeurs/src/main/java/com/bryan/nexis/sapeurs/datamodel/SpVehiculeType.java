package com.bryan.nexis.sapeurs.datamodel;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "sp_vehicule_type")
public class SpVehiculeType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(unique = true, nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String label;

    /** Natures d'intervention pour lesquelles ce type est pertinent (proposition d'engins). */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "sp_vehicule_type_nature",
        joinColumns = @JoinColumn(name = "type_id"),
        inverseJoinColumns = @JoinColumn(name = "nature_id")
    )
    private Set<SpNatureIntervention> natures = new HashSet<>();

    protected SpVehiculeType() {}

    public SpVehiculeType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public UUID getId()      { return id; }
    public String getCode()  { return code; }
    public String getLabel() { return label; }
    public Set<SpNatureIntervention> getNatures() { return natures; }

    public void setCode(String code)   { this.code = code; }
    public void setLabel(String label) { this.label = label; }
}
