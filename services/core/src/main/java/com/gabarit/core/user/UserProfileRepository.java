package com.gabarit.core.user;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

// package-private: outros pacotes acessam pelo UserProfileService
interface UserProfileRepository extends JpaRepository<UserProfile, String> {

	/** Trava a linha até o fim da transação: dois uploads simultâneos não perdem a chave da foto anterior. */
	/**
	 * Quem segue algum dos cargos (mesma fonte), nas UFs do perfil (concurso nacional, uf nula, vale
	 * para todos) e com algum nível de escolaridade em comum (vazio de qualquer lado = qualquer um).
	 * Mesmas regras do "meus concursos" (ExamSpecifications), do lado do usuário.
	 * Casts explícitos: parâmetro nulo ou array sem tipo confunde o Postgres.
	 */
	@Query(value = """
			SELECT DISTINCT p.user_id
			FROM user_profile p
			JOIN user_profile_position upp ON upp.user_id = p.user_id
			JOIN position_catalog c ON c.id = upp.catalog_position_id
			WHERE c.source = :source
			  AND c.slug = ANY (CAST(:slugs AS TEXT[]))
			  AND (cardinality(p.ufs) = 0 OR CAST(:uf AS TEXT) IS NULL OR CAST(:uf AS TEXT) = ANY (p.ufs))
			  AND (cardinality(p.education_levels) = 0 OR cardinality(CAST(:levels AS VARCHAR[])) = 0
			       OR p.education_levels && CAST(:levels AS VARCHAR[]))
			""", nativeQuery = true)
	List<String> findInterested(String source, String[] slugs, String uf, String[] levels);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from UserProfile p where p.userId = :userId")
	Optional<UserProfile> findForUpdate(String userId);

}
