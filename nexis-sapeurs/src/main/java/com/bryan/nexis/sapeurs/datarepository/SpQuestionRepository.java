package com.bryan.nexis.sapeurs.datarepository;

import com.bryan.nexis.sapeurs.datamodel.SpQuestion;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;
import io.micronaut.data.model.Sort;

import java.util.List;
import java.util.UUID;

@Repository
public interface SpQuestionRepository extends JpaRepository<SpQuestion, UUID> {

    List<SpQuestion> findAll(Sort sort);

    /** Questions référençant une question donnée comme condition (pour empêcher sa suppression). */
    @Query("SELECT COUNT(q) FROM SpQuestion q WHERE q.conditionQuestion.id = :questionId")
    long countByConditionQuestionId(UUID questionId);
}
