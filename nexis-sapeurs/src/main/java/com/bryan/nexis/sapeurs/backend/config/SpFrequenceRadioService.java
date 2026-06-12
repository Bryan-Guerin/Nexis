package com.bryan.nexis.sapeurs.backend.config;

import com.bryan.nexis.sapeurs.backend.dto.SpFrequenceRadioDto;
import com.bryan.nexis.sapeurs.datamodel.SpFrequenceRadio;
import com.bryan.nexis.sapeurs.datarepository.SpFrequenceRadioRepository;
import io.micronaut.data.model.Sort;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.UUID;

@Singleton
public class SpFrequenceRadioService {

    private static final Sort BY_POSITION = Sort.of(Sort.Order.asc("position"));

    private final SpFrequenceRadioRepository repo;

    public SpFrequenceRadioService(SpFrequenceRadioRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public List<SpFrequenceRadioDto> listAll() {
        return repo.findAll(BY_POSITION).stream().map(SpFrequenceRadioDto::from).toList();
    }

    @Transactional
    public SpFrequenceRadioDto create(String description, String frequence) {
        var f = new SpFrequenceRadio(description, frequence);
        f.setPosition((int) repo.count());
        return SpFrequenceRadioDto.from(repo.save(f));
    }

    @Transactional
    public void delete(UUID id) {
        repo.deleteById(id);
    }
}
