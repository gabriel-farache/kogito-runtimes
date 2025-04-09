package org.kie.addons.security.token.persistence.runtime.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.kie.security.token.persistence.repository.TokenRepository;

@ApplicationScoped
@Transactional
public class QuarkusTokenRepository extends TokenRepository {

  QuarkusTokenRepository() {
    this(null);
  }

  @Inject
  public QuarkusTokenRepository(QuarkusTokenJPAContext context) {
    super(context);
  }
}