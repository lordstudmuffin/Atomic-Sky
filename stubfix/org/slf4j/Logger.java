package org.slf4j;
/** Compile-only shim; the real SLF4J ships with the game. */
public interface Logger {
    void info(String msg); void info(String fmt, Object arg); void info(String fmt, Object a, Object b); void info(String fmt, Object... args);
    void warn(String msg); void warn(String fmt, Object arg); void warn(String fmt, Object a, Object b); void warn(String fmt, Object... args);
    void error(String msg); void error(String fmt, Object arg); void error(String fmt, Object a, Object b); void error(String fmt, Object... args);
    void debug(String msg); void debug(String fmt, Object arg); void debug(String fmt, Object... args);
}
