package org.kie.security.token.persistence.repository;

import jakarta.persistence.EntityManager;

public interface TokenJPAContext {
  EntityManager getEntityManager();

}
