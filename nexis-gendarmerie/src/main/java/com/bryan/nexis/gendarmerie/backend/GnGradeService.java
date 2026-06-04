package com.bryan.nexis.gendarmerie.backend;

import com.bryan.nexis.gendarmerie.backend.dto.GnGradeDto;
import com.bryan.nexis.gendarmerie.datamodel.GnGrade;
import com.bryan.nexis.gendarmerie.datarepository.GnGradeRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.List;

@Singleton
public class GnGradeService {

    private final GnGradeRepository repo;

    public GnGradeService(GnGradeRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public List<GnGradeDto> listAll() {
        return repo.findAll().stream().map(GnGradeDto::from).toList();
    }

    @Transactional
    public GnGradeDto create(String code, String label) {
        return GnGradeDto.from(repo.save(new GnGrade(code, label)));
    }
}
