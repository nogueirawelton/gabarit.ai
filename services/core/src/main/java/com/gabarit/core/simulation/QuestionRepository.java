package com.gabarit.core.simulation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

interface QuestionRepository extends JpaRepository<Question, UUID> {

	List<Question> findByExtractionIdOrderByOrdinal(UUID extractionId);

	boolean existsByExtractionId(UUID extractionId);

	/** As questões escolhidas de um simulado de prática, na ordem. */
	@Query(nativeQuery = true, value = """
			SELECT q.* FROM question q JOIN simulation_question sq ON sq.question_id = q.id
			WHERE sq.simulation_id = :simulationId ORDER BY sq.ordinal""")
	List<Question> findBySimulation(UUID simulationId);

	/**
	 * Quantas alternativas as questões de múltipla escolha das provas dadas mais têm (para gerar
	 * questões no mesmo formato); nulo sem questão.
	 */
	@Query(nativeQuery = true, value = """
			SELECT jsonb_array_length(options) FROM question
			WHERE extraction_id IN (:extractions) AND kind = 'MULTIPLE_CHOICE'
			GROUP BY 1 ORDER BY count(*) DESC, 1 DESC LIMIT 1""")
	Integer mostCommonOptionCount(Collection<UUID> extractions);

	/** As questões geradas ligadas ao tópico, da geração mais antiga para a mais nova. */
	@Query(nativeQuery = true, value = """
			SELECT q.* FROM question q JOIN question_topic qt ON qt.question_id = q.id
			WHERE qt.topic_id = :topicId AND q.generated ORDER BY q.extraction_id, q.ordinal""")
	List<Question> generatedForTopic(UUID topicId);

	/** Questões que a pessoa já recebeu em simulados de prática ou inéditos deste concurso. */
	@Query(nativeQuery = true, value = """
			SELECT DISTINCT sq.question_id FROM simulation_question sq JOIN simulation s ON s.id = sq.simulation_id
			WHERE s.study_target_id = :targetId AND s.user_id = :userId""")
	List<UUID> seenInSimulations(String userId, UUID targetId);

	@Modifying
	@Query(nativeQuery = true, value = """
			INSERT INTO simulation_question (simulation_id, question_id, ordinal) VALUES (:simulationId, :questionId, :ordinal)""")
	void addToSimulation(UUID simulationId, UUID questionId, int ordinal);

}
