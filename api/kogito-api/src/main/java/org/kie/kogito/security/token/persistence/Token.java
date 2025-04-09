package org.kie.kogito.security.token.persistence;

import org.kie.kogito.process.ProcessInstance;

public interface Token {
  String id();

  /**
   * Returns name of the provider
   *
   * @return provider name
   */
  String getProviderName();

  /**
   * Returns the value of the token
   *
   * @return token value
   */
  String getToken();

  /**
   * Returns the process instance associated with the token
   *
   * @return Process instance
   */
  ProcessInstance getProcessInstance();
}
