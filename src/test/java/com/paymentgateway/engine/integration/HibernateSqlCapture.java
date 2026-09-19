package com.paymentgateway.engine.integration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Captura las sentencias que Hibernate emite (logger "org.hibernate.SQL")
 * durante el alcance de un test — para verificar explícitamente cláusulas
 * como "for update" (Sección 9 del plan: "no asumir, comprobar").
 * Reutilizable en cualquier fase futura que necesite la misma verificación.
 */
public final class HibernateSqlCapture implements AutoCloseable {

    private final Logger logger;
    private final Level previousLevel;
    private final ListAppender<ILoggingEvent> appender;

    private HibernateSqlCapture(Logger logger, Level previousLevel, ListAppender<ILoggingEvent> appender) {
        this.logger = logger;
        this.previousLevel = previousLevel;
        this.appender = appender;
    }

    public static HibernateSqlCapture start() {
        Logger logger = (Logger) LoggerFactory.getLogger("org.hibernate.SQL");
        Level previousLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
        return new HibernateSqlCapture(logger, previousLevel, appender);
    }

    public List<String> capturedStatements() {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.toList());
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        logger.setLevel(previousLevel);
    }
}