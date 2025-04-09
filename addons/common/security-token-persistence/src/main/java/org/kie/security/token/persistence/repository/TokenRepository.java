package org.kie.security.token.persistence.repository;

import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.security.token.persistence.Token;

import java.util.List;
import java.util.Optional;

public class TokenRepository {
  protected TokenJPAContext context;

  public TokenRepository(TokenJPAContext context) {
    this.context = context;
  }

  public boolean exists(String id) {
    return false;
  }

  public void create(String id, org.kie.kogito.security.token.persistence.Tokens token) {

  }

  public void update(String id, org.kie.kogito.security.token.persistence.Tokens token) {

  }

  public void remove(String id) {

  }

  public Optional<Token> findById(String id) {
    return Optional.empty();
  }

  public Optional<List<Token>> findByProcessInstanceId(String processInstanceId) {
    return Optional.empty();
  }

  public Optional<List<Token>> findByProcessInstance(ProcessInstance processInstance) {
    return Optional.empty();
  }
}
