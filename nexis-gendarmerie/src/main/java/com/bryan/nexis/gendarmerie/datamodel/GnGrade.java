package com.bryan.nexis.gendarmerie.datamodel;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "gn_grade")
public class GnGrade {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String label;

    protected GnGrade() {}

    public GnGrade(String code, String label) {
        this.code  = code;
        this.label = label;
    }

    public UUID getId()    { return id; }
    public String getCode()  { return code; }
    public String getLabel() { return label; }
}
