package com.github.axiomc.license.common.persistence;

import java.sql.Connection;
import java.sql.SQLException;

@FunctionalInterface
public interface SqlWork<T> {

    T run(Connection connection) throws SQLException;
}
