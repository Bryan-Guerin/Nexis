package com.bryan.nexis.gendarmerie.datamodel;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "gn_vehicule_type")
public class GnVehiculeType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(unique = true, nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String label;

    protected GnVehiculeType() {}

    public GnVehiculeType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public UUID getId()      { return id; }
    public String getCode()  { return code; }
    public String getLabel() { return label; }

    public void setCode(String code)   { this.code = code; }
    public void setLabel(String label) { this.label = label; }
}
