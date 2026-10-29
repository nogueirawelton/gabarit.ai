package com.gabarit.core.user;

import com.gabarit.core.exam.CatalogPosition;
import com.gabarit.core.exam.CatalogPositionService;
import com.gabarit.core.exam.EducationLevel;
import com.gabarit.core.shared.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/** Preferências de estudo do usuário logado. */
@Service
public class UserProfileService {

	public static final int MAX_POSITIONS = 30;

	static final Set<String> UFS = Set.of("AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS",
			"MG", "PA", "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO");

	/** Perfil pronto para a API: cargos já resolvidos no catálogo. {@code avatarKey} nulo = sem foto. */
	public record ProfileView(List<CatalogPosition> positions, List<String> ufs,
			List<EducationLevel> educationLevels, String avatarKey, boolean emailDigest) {

		static final ProfileView EMPTY = new ProfileView(List.of(), List.of(), List.of(), null, true);

		/** Cargos do catálogo seguidos, agrupados por fonte (slug só vale dentro da fonte). */
		public Map<String, Set<String>> catalogSlugsBySource() {
			return positions.stream()
				.collect(Collectors.groupingBy(CatalogPosition::getSource,
						Collectors.mapping(CatalogPosition::getSlug, Collectors.toSet())));
		}

	}

	/** Para quem mandar o resumo diário: e-mail confirmado e resumo ligado. */
	public record Contact(String userId, String email, String givenName) {
	}

	private final UserProfileRepository repository;

	private final CatalogPositionService catalogService;

	UserProfileService(UserProfileRepository repository, CatalogPositionService catalogService) {
		this.repository = repository;
		this.catalogService = catalogService;
	}

	/** Quem nunca salvou o perfil recebe um perfil vazio (não é erro). */
	@Transactional(readOnly = true)
	public ProfileView get(String userId) {
		return repository.findById(userId).map(this::view).orElse(ProfileView.EMPTY);
	}

	/**
	 * Substitui as preferências (o corpo é o estado completo, não um patch).
	 *
	 * @throws BadRequestException cargo que não existe no catálogo, UF inválida ou cargos demais
	 */
	@Transactional
	public ProfileView update(String userId, Collection<UUID> positionIds, Collection<String> ufs,
			Collection<EducationLevel> educationLevels) {
		Set<UUID> ids = new LinkedHashSet<>(positionIds);
		if (ids.size() > MAX_POSITIONS) {
			throw new BadRequestException("escolha no máximo %d cargos".formatted(MAX_POSITIONS));
		}
		List<CatalogPosition> positions = catalogService.findAll(ids);
		if (positions.size() != ids.size()) {
			Set<UUID> unknown = new TreeSet<>(ids);
			positions.forEach(p -> unknown.remove(p.getId()));
			throw new BadRequestException("cargos que não existem no catálogo: " + unknown);
		}
		Set<String> normalizedUfs = new TreeSet<>();
		for (String uf : ufs) {
			String normalized = uf.trim().toUpperCase();
			if (!UFS.contains(normalized)) {
				throw new BadRequestException("UF inválida: " + uf);
			}
			normalizedUfs.add(normalized);
		}

		UserProfile profile = repository.findById(userId).orElseGet(() -> new UserProfile(userId));
		profile.update(ids, normalizedUfs, educationLevels);
		repository.save(profile);
		return new ProfileView(positions, List.copyOf(normalizedUfs), profile.getEducationLevels(),
				profile.getAvatarKey(), profile.isEmailDigest());
	}

	/** Cargos do catálogo que o usuário segue, agrupados por fonte (slug só vale dentro da fonte). */
	@Transactional(readOnly = true)
	public Map<String, Set<String>> followedCatalogSlugs(String userId) {
		return get(userId).catalogSlugsBySource();
	}

	/**
	 * Guarda o e-mail e o nome do token (o Keycloak é o dono; aqui é cópia para o resumo por e-mail).
	 * Só grava se mudou, e só para quem já tem perfil: sem perfil não há cargo, logo não há aviso.
	 */
	@Transactional
	public void rememberContact(String userId, String email, boolean emailVerified, String givenName) {
		repository.findById(userId).ifPresent(profile -> profile.rememberContact(email, emailVerified, givenName));
	}

	/** Liga ou desliga o resumo diário por e-mail (o sininho no app continua). */
	@Transactional
	public ProfileView changeEmailDigest(String userId, boolean emailDigest) {
		UserProfile profile = repository.findById(userId).orElseGet(() -> repository.save(new UserProfile(userId)));
		profile.changeEmailDigest(emailDigest);
		return view(profile);
	}

	/**
	 * Usuários interessados num concurso novo: seguem algum dos cargos (mesma fonte), estão nas UFs
	 * dele e têm algum nível de escolaridade em comum. Mesmas regras do GET /me/exams.
	 */
	@Transactional(readOnly = true)
	public List<String> findInterested(String source, Set<String> catalogSlugs, String uf,
			Set<EducationLevel> educationLevels) {
		if (catalogSlugs.isEmpty()) {
			return List.of();
		}
		return repository.findInterested(source, catalogSlugs.toArray(String[]::new), uf,
				educationLevels.stream().map(Enum::name).toArray(String[]::new));
	}

	/** Dos usuários pedidos, os que recebem resumo: e-mail confirmado e resumo ligado. */
	@Transactional(readOnly = true)
	public List<Contact> digestContacts(Collection<String> userIds) {
		return repository.findAllById(userIds)
			.stream()
			.filter(p -> p.isEmailDigest() && p.isEmailVerified() && p.getEmail() != null)
			.map(p -> new Contact(p.getUserId(), p.getEmail(), p.getGivenName()))
			.toList();
	}

	private ProfileView view(UserProfile profile) {
		return new ProfileView(catalogService.findAll(profile.getCatalogPositionIds()), profile.getUfs(),
				profile.getEducationLevels(), profile.getAvatarKey(), profile.isEmailDigest());
	}

}
