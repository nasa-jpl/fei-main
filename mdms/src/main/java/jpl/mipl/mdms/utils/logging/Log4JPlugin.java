/*******************************************************************************
 * Copyright (C) 1999 California Institute of Technology. All rights reserved US
 * Government Sponsorship under NASA contract NAS7-918 is acknowledged
 ******************************************************************************/

package jpl.mipl.mdms.utils.logging;

import jpl.mipl.mdms.FileService.komodo.api.SessionException;
import jpl.mipl.mdms.FileService.komodo.util.ConfigFileURLResolver;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractOutputStreamAppender;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.SmtpAppender;
import org.apache.logging.log4j.core.appender.rolling.DirectWriteRolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.TimeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.filter.LevelRangeFilter;
import org.apache.logging.log4j.core.filter.ThresholdFilter;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.core.util.FileUtils;
import org.apache.logging.log4j.spi.StandardLevel;
import org.apache.logging.log4j.util.Strings;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;

/**
 * This class implements the Log4J plugin inteface.
 *
 * @author T. Huang {Thomas.Huang@jpl.nasa.gov}
 * @author N. Toole {Nicholas.T.Toole@jpl.nasa.gov}
 * @author A. Tinio {Adrian.Tinio@jpl.nasa.gov}
 * @version $Id: Log4JPlugin.java,v 1.17 2009/09/21 17:46:44 ntt Exp $
 */
public class Log4JPlugin implements LoggerPlugin {
    public static final Level BENCH = Level.forName("BENCH", StandardLevel.DEBUG.intLevel() + 50);

    private transient org.apache.logging.log4j.Logger _logger;

    private String _logFilename = null;
    private int _rolling = Logger.ROLLING_NEVER;

    private AbstractOutputStreamAppender _fileAppender = null;
    private SmtpAppender _emailAppender = null;
    private final String _LAYOUT = "%m%n";

    private final String BASIC_LAYOUT = "%m%n";
    private final String COMPLEX_LAYOUT = "%-5p [%t] (%F:%L) - %m%n";

    private final String _name = Logger.class.getName();

    private static boolean __configured = false;

    private synchronized static URL getConfigURL() {
        ConfigFileURLResolver resolver = new ConfigFileURLResolver();
        URL cfgURL = null;

        try {
            cfgURL = resolver.getLoggingConfigFile();
        } catch (SessionException se) {
            System.err.println("[LOG_CONF_ERR] Error accessing log config file: " + se.getMessage());
        }
        return cfgURL;
    }

    private synchronized static void configureFromXml() {
        URL cfgURL = getConfigURL();
        if (cfgURL == null) {
            return;
        }
        try {
            ConfigurationSource source = new ConfigurationSource(new FileInputStream(cfgURL.getFile()));
            Configurator.initialize(null, source);
        } catch (Exception e) {
            System.err.println(e.toString());
        }
        // Update: 2022-02-16: William: ignoring config Delay since it is no longer supported.
//        String configDelay = System.getProperty(LOG_CONFIG_DELAY_PROPERTY);
//        if (configDelay == null) {
//            return;
//        }
//        Long delay = null;
//        try {
//            delay = new Long(configDelay);
//        } catch (NumberFormatException e) {
//            System.err.println("[LOG_CONF_ERR] Invalid delay value");
//        }
//
//        if (delay == null || delay.longValue() < 1) {
//            org.apache.logging.log4j.xml.DOMConfigurator.configure(cfgURL);
//            return;
//        }
//        org.apache.log4j.xml.DOMConfigurator.configureAndWatch(
//                cfgURL.getFile(), delay.longValue() * 60);
    }

    /**
     * Examines properties for the log configuration and delay.
     * Parses configuration file setting the Log4J framework.
     * Also examines MDMS debug property and will set logger
     * accordingly.
     */
    private synchronized static void loadConfiguration() {
        configureFromXml();
        if (System.getProperty(ENABLE_DEBUG_PROPERTY) != null) {
            org.apache.logging.log4j.Logger rootLogger = LogManager.getRootLogger();
            if (rootLogger.getLevel().intLevel() >= Level.DEBUG.intLevel()) {
                rootLogger.atLevel(Level.DEBUG);
            }
        }
        Log4JPlugin.__configured = true;
    }

    /**
     * Returns true if the logger has already been configured.
     *
     * @return True if configured, false otherwise
     */
    private synchronized static boolean isConfigured() {
        return Log4JPlugin.__configured;
    }

    /**
     * Method to initialize the Log4J logger using its DOMConfigurator to parse
     * the log4j XML configuration file
     *
     * @param name   the name of the logger.
     * @param reinit if true, the configuration will be reloaded, else
     *               configuration is only loaded if not already.
     */
    public synchronized void init(String name, boolean reinit) {

        //check if the plugin configuration has been loaded.
        //if not, or if reinit is true, then load the configuration
        if (!Log4JPlugin.isConfigured() || reinit) {
            Log4JPlugin.loadConfiguration();
        }

        //--------------------------

        this._logger = LogManager.getLogger(name);
    }

    /**
     * Method to init an instance of the Log4J logger.  This is a
     * convenience method that is the same as calling init(name, false).
     *
     * @param name the name of the logger.
     */

    public void init(String name) {
        init(name, false);
    }

    public void severe(Object message) {
        this._logger.fatal(message);
    }

    public void severe(Object message, Throwable t) {
        this._logger.fatal(message, t);
    }

    public void error(Object message) {
        this._logger.error(message);
    }

    public void error(Object message, Throwable t) {
        this._logger.error(message, t);
    }

    public void warn(Object message) {
        this._logger.warn(message);
    }

    public void warn(Object message, Throwable t) {
        this._logger.warn(message, t);
    }

    public boolean isInfoEnabled() {
        return this._logger.isEnabled(Level.INFO);
    }

    public void info(Object message) {
        this._logger.info(message);
    }

    public void info(Object message, Throwable t) {
        this._logger.info(message, t);
    }

    public boolean isDebugEnabled() {
        return this._logger.isEnabled(Level.DEBUG);
    }

    public void debug(Object message) {
        this._logger.debug(message);
    }

    public void debug(Object message, Throwable t) {
        this._logger.debug(message, t);
    }

    public boolean isTraceEnabled() {
        return this._logger.isEnabled(Level.TRACE);
    }

    public void trace(Object message) {
        this._logger.trace(message);
    }

    public void trace(Object message, Throwable t) {
        this._logger.trace(message, t);
    }

    public boolean isBenchEnabled() {
        if (!this._logger.isEnabled(BENCH)) {
            return false;
        }
        return BENCH.intLevel() >= this._logger.getLevel().intLevel();
    }

    public void bench(Object message) {
        this._logger.log(BENCH, message);
    }

    public void bench(Object message, Throwable t) {
        this._logger.log(BENCH, message, t);
    }

    public synchronized void setLogFileName(String filename) {
        this._logFilename = filename;
    }

    public synchronized void setLogFileRolling(int rolling) {
        this._rolling = rolling;
    }

    private void createFileAppender(final org.apache.logging.log4j.core.Logger root) throws IOException {
        if (_fileAppender != null) {
            return;
        }
        String layoutPattern = BASIC_LAYOUT;
        if (root.getLevel().intLevel() < Level.INFO.intLevel()) {
            layoutPattern = COMPLEX_LAYOUT;
        }
        PatternLayout patternLayout = PatternLayout.newBuilder().withPattern(layoutPattern).build();

        String datePattern = "";
        if (_rolling == Logger.ROLLING_MONTHLY) {
            datePattern = ".%d{yyyy-MM}";
        } else if (_rolling == Logger.ROLLING_WEEKLY) {
            datePattern = ".%d{yyyy-ww}";
        } else if (_rolling == Logger.ROLLING_DAILY) {
            datePattern = ".%d{yyyy-MM-dd}";
        } else if (_rolling == Logger.ROLLING_HALF_DAILY) {
            datePattern = ".%d{yyyy-MM-dd-a}";
        } else if (_rolling == Logger.ROLLING_HOURLY) {
            datePattern = ".%d{yyyy-MM-dd-HH}";
        } else if (_rolling == Logger.ROLLING_MINUTELY) {
            datePattern = ".%d{yyyy-MM-dd-HH-mm}";
        }
        if (!Strings.isEmpty(datePattern)) {
            RollingFileAppender.Builder bb = RollingFileAppender.newBuilder();
            bb.withFileName(_logFilename);
            bb.withFilePattern(_logFilename + datePattern);
            bb.setLayout(patternLayout);
            bb.setName("RollingFileAppender-" + datePattern);
            bb.withImmediateFlush(true);
            bb.withAppend(true);
            bb.withPolicy(TimeBasedTriggeringPolicy.newBuilder().withInterval(1).withModulate(true).build());
            _fileAppender = bb.build();
            _fileAppender.start();
            return;
        }
        FileAppender.Builder bb = FileAppender.newBuilder();
        bb.setName("FileAppender");
        bb.withImmediateFlush(true);
        bb.withAppend(true);
        bb.withFileName(_logFilename);
        bb.setLayout(patternLayout);
        _fileAppender = bb.build();
        _fileAppender.start();
    }

    /**
     * Method to log message to an external log file. This implementation does
     * not provide any message formatting to the input message. For server
     * processes that needs to log data to a file, it should be done using the
     * external log4j configuration file to customize log message layout.
     */
    public synchronized void enableLogToFile() {
        if (this._logFilename == null) {
            return;
        }
        try {
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            org.apache.logging.log4j.core.Logger root = ctx.getRootLogger();
            createFileAppender(root);
            //allow it to activate
//           _fileAppender.activateOptions();  // TODO

//           //theory - file appender needs to be attached to root so that everything goes there
//           //maybe consider creating itermediary logger 'jpl.mipl.mdms' as a common parent
//           org.apache.log4j.Logger root = org.apache.log4j.Logger.getRootLogger();

            //logger adds appender only if not already present
            root.addAppender(_fileAppender);
        } catch (IOException e) {
            this.severe("Unable to create log file [" + this._logFilename + "]", e);
        }
    }

    public synchronized void enableLogToFile(String filename) {
        this.enableLogToFile(filename, Logger.ROLLING_NEVER);
    }

    public synchronized void enableLogToFile(String filename, int rolling) {
        this._logFilename = filename;
        this._rolling = rolling;
        this.enableLogToFile();
    }

    public synchronized void setMail(String from, String to, String smtpserver,
                                     String subject) {
        _emailAppender = SmtpAppender.newBuilder()
                .setName(smtpserver)
                .setFrom(from)
                .setTo(to)
                .setSubject(subject)
                .setSmtpHost(smtpserver)
                .setBufferSize(512)
                .setLayout(PatternLayout.newBuilder().withPattern(_LAYOUT).build())
                .setFilter(ThresholdFilter.createFilter(_logger.getLevel(), Filter.Result.ACCEPT, Filter.Result.DENY))
                .build();
        _emailAppender.start();
//      this._emailAppender.activateOptions();  // TODO
    }

    public synchronized void enableSendMail() {
        if (this._emailAppender == null) {
            return;
        }
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        org.apache.logging.log4j.core.Logger root = ctx.getRootLogger();
        root.addAppender(_emailAppender);
        root.setAdditive(false);
    }

    public synchronized void disableSendMail() {
        if (this._emailAppender == null) {
            return;
        }
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        org.apache.logging.log4j.core.Logger root = ctx.getRootLogger();
        root.removeAppender(_emailAppender);
        root.setAdditive(true);
    }

    public void sendMail(String from, String to, String smtpserver,
                         Object subject, Object message) {
        SmtpAppender emailAppender = SmtpAppender.newBuilder()
                .setName(smtpserver)
                .setFrom(from)
                .setTo(to)
                .setSubject(subject.toString())
                .setSmtpHost(smtpserver)
                .setBufferSize(512)
                .setLayout(PatternLayout.newBuilder().withPattern(_LAYOUT).build())
                .setFilter(ThresholdFilter.createFilter(_logger.getLevel(), Filter.Result.ACCEPT, Filter.Result.DENY))
                .build();
//      emailAppender.activateOptions();  // TODO
        this.error(message);
        ((org.apache.logging.log4j.core.Logger) _logger).removeAppender(emailAppender);
    }

    public void sendMail(String from, String to, String smtpserver,
                         Object subject, Object message, Throwable t) {
        SmtpAppender emailAppender = SmtpAppender.newBuilder()
                .setName(smtpserver)
                .setFrom(from)
                .setTo(to)
                .setSubject(subject.toString())
                .setSmtpHost(smtpserver)
                .setBufferSize(512)
                .setLayout(PatternLayout.newBuilder().withPattern(_LAYOUT).build())
                .setFilter(ThresholdFilter.createFilter(_logger.getLevel(), Filter.Result.ACCEPT, Filter.Result.DENY))
                .build();
//      emailAppender.activateOptions();  // TODO
        this.info(message, t);
        ((org.apache.logging.log4j.core.Logger) _logger).removeAppender(emailAppender);
    }
}
