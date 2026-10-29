package com.gabarit.core.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// package-private: outros pacotes usam o NotificationService
interface NotificationRepository extends JpaRepository<Notification, UUID> {

	Page<Notification> findByUserIdOrderByCreatedAtDescIdDesc(String userId, Pageable pageable);

	long countByUserIdAndReadAtIsNull(String userId);

	Optional<Notification> findByIdAndUserId(UUID id, String userId);

	@Modifying
	@Query("update Notification n set n.readAt = :at where n.userId = :userId and n.readAt is null")
	int markAllRead(String userId, Instant at);

	@Query("select distinct n.userId from Notification n where n.emailedAt is null and n.createdAt >= :since")
	List<String> findUsersWithPendingEmail(Instant since);

	/**
	 * Avisos de um usuário que ainda não foram por e-mail, travados até o fim da transação. SKIP
	 * LOCKED: se outra instância do core já está enviando o resumo desse usuário, esta pula.
	 */
	@Query(value = """
			SELECT * FROM notification
			WHERE user_id = :userId AND emailed_at IS NULL AND created_at >= :since
			ORDER BY created_at, id
			FOR UPDATE SKIP LOCKED
			""", nativeQuery = true)
	List<Notification> lockPendingEmail(String userId, Instant since);

	@Modifying
	@Query("delete from Notification n where n.createdAt < :before")
	int deleteOlderThan(Instant before);

}
