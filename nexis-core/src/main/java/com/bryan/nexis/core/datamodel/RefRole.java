package com.bryan.nexis.core.datamodel;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "ref_role")
public class RefRole {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(unique = true, nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String label;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "parent_id")
    private RefRole parent;

    protected RefRole() {}

    public RefRole(String code, String label, RefRole parent) {
        this.code = code;
        this.label = label;
        this.parent = parent;
    }

    public UUID getId()        { return id; }
    public String getCode()    { return code; }
    public String getLabel()   { return label; }
    public RefRole getParent() { return parent; }
}
