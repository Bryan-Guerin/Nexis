package com.bryan.nexis.gendarmerie.datarepository;

import com.bryan.nexis.gendarmerie.datamodel.GnGrade;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.UUID;

@Repository
public interface GnGradeRepository extends JpaRepository<GnGrade, UUID> {}
