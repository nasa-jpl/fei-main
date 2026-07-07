/*******************************************************************************
 * Copyright (C) 2001 California Institute of Technology. All rights reserved US
 * Government Sponsorship under NASA contract NAS7-918 is acknowledged
 ******************************************************************************/

package jpl.mipl.mdms.FileService.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.Semaphore;

import jpl.mipl.mdms.FileService.io.BoundedBufferedReader;
import jpl.mipl.mdms.utils.Constants;
import jpl.mipl.mdms.utils.logging.Logger;


/**
 * This utility class is a wrapper class to all system-related processes.
 *
 */

public class SystemProcess
{
    protected final static boolean PRE_ACQUIRE_SEMAPHORE = true;
    protected final static int DEFAULT_AYSNC_LIMIT = 15;

    protected static Semaphore semaphore;
    static {
        initSemaphore();
    }

    //---------------------------------------------------------------------

    protected static void initSemaphore()
    {
        int count = DEFAULT_AYSNC_LIMIT;
        String propVal = System.getProperty(Constants.PROPERTY_ASYNC_INVOKE_LIMIT, "");
        if (propVal != null && !propVal.isEmpty()) //null not possible but better to be safe
        {
            try {
                count = Integer.parseInt(propVal);
                if (count < 1)
                    count = DEFAULT_AYSNC_LIMIT;
            } catch (NumberFormatException nfEx) {
                Logger logger = Logger.getLogger(SystemProcess.class.getName());
                logger.error(nfEx.getMessage());
                logger.trace(null, nfEx);
                logger.warn("Using default semaphore count value: "+DEFAULT_AYSNC_LIMIT);
                count = DEFAULT_AYSNC_LIMIT;
            }
        }
        SystemProcess.semaphore = new Semaphore(count);
    }

    //---------------------------------------------------------------------

   /**
    * Method to execute a system command and capture its standard errors.
    * @param command the system command to be executed
    * @return the Errno object reference.
    */

    public static final Errno execute(String command)
    {
        Logger logger = Logger.getLogger(SystemProcess.class.getName());
        return SystemProcess.execute(command, logger);
    }

    //---------------------------------------------------------------------

    /**
     * Method to execute a system command asynchronously
     * @param command the system command to be executed
     * @return the Errno object reference. Aysnchronous message
     * if process start was successful, error message otherwise
     */

    public static final Errno executeAsync(String command)
    {
        Logger logger = Logger.getLogger(SystemProcess.class.getName());
        return SystemProcess.executeAsync(command, logger);
    }

    //---------------------------------------------------------------------

   /**
    * Method to execute a system command and capture its standard errors.
    * @param command the system command to be executed
    * @param logger the message logger
    * @return the Errno object reference.
    */

    public static final Errno execute(String command, Logger logger)
    {
        Errno errno = null;
        ProcessRunner runner = new ProcessRunner(command, logger);
        Thread p = new Thread(runner);
        try {
            p.start();
            p.join();
            errno = runner.getErrno();
        } catch (Exception e) {
            logger.error(e.getMessage());
            logger.trace(null, e);
            errno = new Errno(-1, e.getMessage());
        }
        return errno;
    }

    //---------------------------------------------------------------------

    /**
     * Method to execute a system command asynchronously.
     * @param command the system command to be executed
     * @param logger the message logger
     * @return the Errno object reference. Aysnchronous message
     * if process start was successful, error message otherwise
     */

    public static final Errno executeAsync(String command, Logger logger)
    {
        Errno errno = null;

        if (SystemProcess.PRE_ACQUIRE_SEMAPHORE)
        {
            try {
                SystemProcess.semaphore.acquire();
            } catch (InterruptedException intEx) {
                logger.error(intEx.getMessage());
                logger.trace(null, intEx);
                errno = new Errno(-1, intEx.getMessage());
            }
        }

        ProcessRunner runner = new ProcessRunner(command, logger,
                                      SystemProcess.semaphore,
                                      SystemProcess.PRE_ACQUIRE_SEMAPHORE);
        Thread p = new Thread(runner);
        try {
            p.start();
            errno = new Errno(0, "Asynchronous invocation");
        } catch (Exception e) {
            logger.error(e.getMessage());
            logger.trace(null, e);
            errno = new Errno(-1, e.getMessage());
        }

        return errno;
    }

    //---------------------------------------------------------------------

   /**
    * Run process in a separate thread just in case we get into trouble, we can
    * still recover from the application main thread.
    */

    static final class ProcessRunner implements Runnable
    {
        private String _command = null;
        private Logger _logger = null;
        private Errno _errno = null;

        private Semaphore _semaphore = null;
        private boolean _semaphorePreAcquired = false;

        //-----------------------------------------------------------------

        public ProcessRunner(String command, Logger logger)
        {
            this._command = command;
            this._logger = logger;
        }

        //-----------------------------------------------------------------

        public ProcessRunner(String command, Logger logger,
                             Semaphore semaphore)
        {
            this(command, logger);
            this._semaphore = semaphore;
        }

        //-----------------------------------------------------------------

        public ProcessRunner(String command, Logger logger,
                             Semaphore semaphore, boolean preAcquired)
        {
            this(command, logger, semaphore);
            this._semaphorePreAcquired = preAcquired;
        }

        //-----------------------------------------------------------------

        public Errno getErrno()
        {
            return this._errno;
        }

        //-----------------------------------------------------------------

        public void run()
        {
            StringBuffer errMsg = new StringBuffer();
            Process p = null;

            try {

                if (this._semaphore != null && !this._semaphorePreAcquired)
                    this._semaphore.acquire();

                p = Runtime.getRuntime().exec(this._command);

                BufferedReader is = new BoundedBufferedReader(
                                    new InputStreamReader(p.getInputStream()));

                String line;
                boolean newline = false;
                while ((line = is.readLine()) != null)
                {
                    this._logger.info(line);
                    newline = true;
                }
                if (newline)
                    this._logger.info("");
                is.close();

                newline = false;
                int stderr = p.waitFor();

                is = new BoundedBufferedReader(new InputStreamReader(
                                               p.getErrorStream()));
                while ((line = is.readLine()) != null)
                {
                    errMsg.append(line);
                    this._logger.error(line);
                    newline = true;
                }
                if (newline)
                    this._logger.error("");
                is.close();

                // close output stream
                p.getOutputStream().close();

                this._errno = new Errno(stderr, errMsg.toString());
            } catch (Exception e) {
                this._errno = new Errno(-1, e.getMessage());
            } finally {

                //destroy process object
                if (p != null)
                    p.destroy();

                //release semaphore
                if (this._semaphore != null)
                    this._semaphore.release();
            }
        }

        //-----------------------------------------------------------------
    }

   //---------------------------------------------------------------------

}