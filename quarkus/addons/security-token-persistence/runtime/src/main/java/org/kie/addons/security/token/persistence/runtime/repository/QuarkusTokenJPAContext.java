package org.kie.addons.security.token.persistence.runtime.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.kie.security.token.persistence.repository.TokenJPAContext;


@ApplicationScoped
@Transactional
public class QuarkusTokenJPAContext implements TokenJPAContext {

  @PersistenceContext
  private EntityManager em;

  @Override
  public EntityManager getEntityManager() {
    return em;
  }
}