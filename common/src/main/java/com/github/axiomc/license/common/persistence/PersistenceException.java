package com.github.axiomc.license.common.persistence;

import java.sql.SQLException;

public final class PersistenceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    PersistenceException(SQLException cause) {
        super(cause.getMessage(), cause);
    }
}
