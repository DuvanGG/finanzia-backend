package com.finanzia.application.port.out;

import com.finanzia.domain.common.Email;
import com.finanzia.domain.common.UserId;
import com.finanzia.domain.user.User;

import java.util.Optional;

/**
 * Repositorio de usuarios (outbound port). Implementado por integración en
 * infraestructura; el dominio y la aplicación no conocen JPA.
 */
public interface UserRepository {

    Optional<User> findById(UserId id);

    Optional<User> findByEmail(Email email);

    User save(User user);
}