package com.gabarit.core.simulation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

interface QuestionTopicRepository extends JpaRepository<QuestionTopic, UUID> {

	/** Quantas questões de cada tópico, nas provas dadas, para o conteúdo dado (ordinais a partir de 0). */
	interface TopicCount {

		int getSubjectOrdinal();

		int getTopicOrdinal();

		long getQuestions();

	}

	@Query(nativeQuery = true, value = """
			SELECT s.ordinal AS subject_ordinal, t.ordinal AS topic_ordinal, count(DISTINCT q.id) AS questions
			FROM question q
			JOIN question_topic qt ON qt.question_id = q.id
			JOIN syllabus_topic t ON t.id = qt.topic_id
			JOIN syllabus_subject s ON s.id = t.subject_id
			WHERE q.extraction_id IN (:examReadings) AND s.extraction_id = :syllabusReading
			GROUP BY s.ordinal, t.ordinal""")
	List<TopicCount> countByTopic(Collection<UUID> examReadings, UUID syllabusReading);

	/** Tópico de uma questão no conteúdo de um cargo. */
	interface QuestionTopicName {

		UUID getQuestionId();

		String getSubject();

		String getTopic();

	}

	/** O tópico de cada questão no conteúdo dado (questão sem tópico fica de fora). */
	@Query(nativeQuery = true, value = """
			SELECT qt.question_id AS question_id, s.name AS subject, t.text AS topic
			FROM question_topic qt
			JOIN syllabus_topic t ON t.id = qt.topic_id
			JOIN syllabus_subject s ON s.id = t.subject_id
			WHERE qt.question_id IN (:questions) AND s.extraction_id = :syllabusReading""")
	List<QuestionTopicName> topicsOf(Collection<UUID> questions, UUID syllabusReading);

	/**
	 * Questões das leituras dadas (provas lidas ou questões geradas) ligadas a tópicos do conteúdo, que
	 * servem para praticar (objetivas, não anuladas, com resposta no gabarito, não reportadas pela
	 * pessoa nem por {@link QuestionReport#REPORTS_TO_HIDE} pessoas): reais primeiro, na ordem das
	 * provas.
	 */
	@Query(nativeQuery = true, value = """
			SELECT qt.question_id AS question_id, s.name AS subject, t.text AS topic
			FROM question_topic qt
			JOIN question q ON q.id = qt.question_id
			JOIN syllabus_topic t ON t.id = qt.topic_id
			JOIN syllabus_subject s ON s.id = t.subject_id
			WHERE q.extraction_id IN (:sources) AND s.extraction_id = :syllabusReading
			  AND q.kind <> 'ESSAY' AND NOT q.annulled AND q.answer IS NOT NULL
			  AND NOT EXISTS (SELECT 1 FROM question_report r WHERE r.question_id = q.id AND r.user_id = :userId)
			  AND (SELECT count(*) FROM question_report r WHERE r.question_id = q.id) < 3
			ORDER BY q.generated, q.extraction_id, q.ordinal""")
	List<QuestionTopicName> practiceQuestions(Collection<UUID> sources, UUID syllabusReading, String userId);

	boolean existsByQuestionIdAndTopicId(UUID questionId, UUID topicId);

	/** Questão gerada ligada a um tópico. */
	interface GeneratedQuestion {

		UUID getTopicId();

		UUID getQuestionId();

	}

	/** As questões geradas (com gabarito; sem as reportadas, como na prática) dos tópicos, na ordem da geração. */
	@Query(nativeQuery = true, value = """
			SELECT qt.topic_id AS topic_id, q.id AS question_id
			FROM question_topic qt JOIN question q ON q.id = qt.question_id
			WHERE qt.topic_id IN (:topics) AND q.generated AND q.answer IS NOT NULL
			  AND NOT EXISTS (SELECT 1 FROM question_report r WHERE r.question_id = q.id AND r.user_id = :userId)
			  AND (SELECT count(*) FROM question_report r WHERE r.question_id = q.id) < 3
			ORDER BY q.extraction_id, q.ordinal""")
	List<GeneratedQuestion> generatedFor(Collection<UUID> topics, String userId);

	/** Desfaz as ligações das questões da prova com os tópicos do conteúdo (antes de ligar de novo). */
	@Modifying
	@Query(nativeQuery = true, value = """
			DELETE FROM question_topic qt USING question q, syllabus_topic t, syllabus_subject s
			WHERE qt.question_id = q.id AND qt.topic_id = t.id AND t.subject_id = s.id
			  AND q.extraction_id = :examReading AND s.extraction_id = :syllabusReading""")
	void unlink(UUID examReading, UUID syllabusReading);

}
