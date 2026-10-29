package com.gabarit.core.user;

import com.gabarit.core.exam.EducationLevel;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Preferências de estudo de um usuário. Identidade: o "sub" do Keycloak.
 * Guarda só ids do catálogo de cargos: o pacote exam é dono do catálogo.
 */
@Entity
@Table(name = "user_profile")
public class UserProfile {

	@Id
	private String userId;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(nullable = false, columnDefinition = "text[]")
	private List<String> ufs = new ArrayList<>();

	// nomes do EducationLevel; vazio = todos. varchar[] (ver V6)
	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(nullable = false, columnDefinition = "varchar[]")
	private List<String> educationLevels = new ArrayList<>();

	@ElementCollection
	@CollectionTable(name = "user_profile_position", joinColumns = @JoinColumn(name = "user_id"))
	@Column(name = "catalog_position_id", nullable = false)
	private Set<UUID> catalogPositionIds = new LinkedHashSet<>();

	// chave do objeto no bucket de avatares (MinIO); nulo = sem foto
	private String avatarKey;

	// cópia do token do Keycloak (dono desses dados), para o resumo por e-mail
	private String email;

	private boolean emailVerified;

	private String givenName;

	private boolean emailDigest = true;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	protected UserProfile() {
		// JPA
	}

	UserProfile(String userId) {
		this.userId = userId;
	}

	void update(Collection<UUID> catalogPositionIds, Collection<String> ufs,
			Collection<EducationLevel> educationLevels) {
		this.catalogPositionIds.clear();
		this.catalogPositionIds.addAll(catalogPositionIds);
		this.ufs = new ArrayList<>(new LinkedHashSet<>(ufs));
		// ordem do enum (fundamental → superior), sem repetição
		Set<EducationLevel> levels = EnumSet.noneOf(EducationLevel.class);
		levels.addAll(educationLevels);
		this.educationLevels = levels.stream().map(Enum::name).collect(Collectors.toCollection(ArrayList::new));
		// a coleção de ids não marca a entidade como alterada: força o updated_at
		this.updatedAt = Instant.now();
	}

	/** Troca a foto e devolve a chave da anterior (o arquivo antigo é apagado por quem chamou). */
	String changeAvatar(String avatarKey) {
		String previous = this.avatarKey;
		this.avatarKey = avatarKey;
		return previous;
	}

	/** Atualiza o contato com o que veio no token. @return false se nada mudou (não precisa gravar) */
	boolean rememberContact(String email, boolean emailVerified, String givenName) {
		if (Objects.equals(this.email, email) && this.emailVerified == emailVerified
				&& Objects.equals(this.givenName, givenName)) {
			return false;
		}
		this.email = email;
		this.emailVerified = emailVerified;
		this.givenName = givenName;
		return true;
	}

	void changeEmailDigest(boolean emailDigest) {
		this.emailDigest = emailDigest;
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
		updatedAt = createdAt;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}

	public String getUserId() {
		return userId;
	}

	public List<String> getUfs() {
		return Collections.unmodifiableList(ufs);
	}

	public List<EducationLevel> getEducationLevels() {
		return educationLevels.stream().map(EducationLevel::valueOf).toList();
	}

	public Set<UUID> getCatalogPositionIds() {
		return Collections.unmodifiableSet(catalogPositionIds);
	}

	public String getAvatarKey() {
		return avatarKey;
	}

	public String getEmail() {
		return email;
	}

	public boolean isEmailVerified() {
		return emailVerified;
	}

	public String getGivenName() {
		return givenName;
	}

	public boolean isEmailDigest() {
		return emailDigest;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
