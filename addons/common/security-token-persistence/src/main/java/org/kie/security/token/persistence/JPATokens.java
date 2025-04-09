package org.kie.security.token.persistence;

import io.vertx.pgclient.PgPool;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.security.token.persistence.Token;
import org.kie.kogito.security.token.persistence.Tokens;
import org.kie.security.token.persistence.repository.TokenRepository;

import java.util.List;
import java.util.Optional;

public class JPATokens implements Tokens {
  TokenRepository tokenRepository;

  public JPATokens(TokenRepository tokenRepository) {
    this.tokenRepository = tokenRepository;
  }
  @Override
  public boolean exists(String id) {
    return false;
  }

  @Override
  public void create(String id, org.kie.kogito.security.token.persistence.Tokens token) {

  }

  @Override
  public void update(String id, org.kie.kogito.security.token.persistence.Tokens token) {

  }

  @Override
  public void remove(String id) {

  }

  @Override
  public Optional<Token> findById(String id) {
    return Optional.empty();
  }

  @Override
  public Optional<List<Token>> findByProcessInstanceId(String processInstanceId) {
    return Optional.empty();
  }

  @Override
  public Optional<List<Token>> findByProcessInstance(ProcessInstance processInstance) {
    return Optional.empty();
  }
}
