package com.gabarit.core.user;

import com.gabarit.core.exam.CatalogPosition;
import com.gabarit.core.exam.CatalogPositionService;
import com.gabarit.core.shared.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Preferências de estudo do usuário logado. */
@Service
public class UserProfileService {

	public static final int MAX_POSITIONS = 30;

	static final Set<String> UFS = Set.of("AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS",
			"MG", "PA", "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO");

	/** Perfil pronto para a API: cargos já resolvidos no catálogo. */
	public record ProfileView(List<CatalogPosition> positions, List<String> ufs) {

		static final ProfileView EMPTY = new ProfileView(List.of(), List.of());

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
	public ProfileView update(String userId, Collection<Long> positionIds, Collection<String> ufs) {
		Set<Long> ids = new LinkedHashSet<>(positionIds);
		if (ids.size() > MAX_POSITIONS) {
			throw new BadRequestException("escolha no máximo %d cargos".formatted(MAX_POSITIONS));
		}
		List<CatalogPosition> positions = catalogService.findAll(ids);
		if (positions.size() != ids.size()) {
			Set<Long> unknown = new TreeSet<>(ids);
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
		profile.update(ids, normalizedUfs);
		repository.save(profile);
		return new ProfileView(positions, List.copyOf(normalizedUfs));
	}

	private ProfileView view(UserProfile profile) {
		return new ProfileView(catalogService.findAll(profile.getCatalogPositionIds()), profile.getUfs());
	}

}
