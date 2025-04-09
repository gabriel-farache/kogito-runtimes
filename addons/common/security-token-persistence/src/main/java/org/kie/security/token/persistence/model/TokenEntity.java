package org.kie.security.token.persistence.model;

import org.kie.api.runtime.process.ProcessInstance;

import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "token")
public class TokenEntity {
  @Id
  private String id;

  private String provider;

  private String token;

  @JoinColumn(name = "process_instance_id", foreignKey = @ForeignKey(name = "fk_token_process_instance_id"))
  private ProcessInstance processInstance;
}
