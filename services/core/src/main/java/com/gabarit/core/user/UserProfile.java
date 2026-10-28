package com.gabarit.core.user;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

	@ElementCollection
	@CollectionTable(name = "user_profile_position", joinColumns = @JoinColumn(name = "user_id"))
	@Column(name = "catalog_position_id", nullable = false)
	private Set<Long> catalogPositionIds = new LinkedHashSet<>();

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

	void update(Collection<Long> catalogPositionIds, Collection<String> ufs) {
		this.catalogPositionIds.clear();
		this.catalogPositionIds.addAll(catalogPositionIds);
		this.ufs = new ArrayList<>(new LinkedHashSet<>(ufs));
		// a coleção de ids não marca a entidade como alterada: força o updated_at
		this.updatedAt = Instant.now();
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

	public Set<Long> getCatalogPositionIds() {
		return Collections.unmodifiableSet(catalogPositionIds);
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
