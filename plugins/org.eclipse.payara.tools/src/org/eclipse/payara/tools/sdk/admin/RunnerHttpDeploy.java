/******************************************************************************
 * Copyright (c) 2018 Oracle
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/

/******************************************************************************
 * Copyright (c) 2018-2021 Payara Foundation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/

package org.eclipse.payara.tools.sdk.admin;

import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.eclipse.payara.tools.sdk.logging.Logger;
import org.eclipse.payara.tools.sdk.utils.Utils;
import org.eclipse.payara.tools.server.PayaraServer;

/**
 * GlassFish Server <code>deploy</code> Administration Command Execution using HTTP interface.
 * <p/>
 * Class implements GlassFish server administration functionality trough HTTP interface.
 * <p/>
 *
 * @author Tomas Kraus, Peter Benedikovic
 */
public class RunnerHttpDeploy extends RunnerHttp {

    ////////////////////////////////////////////////////////////////////////////
    // Class attributes //
    ////////////////////////////////////////////////////////////////////////////

    /** Logger instance for this class. */
    private static final Logger LOGGER = new Logger(RunnerHttpDeploy.class);

    /** Deploy command <code>DEFAULT</code> parameter name. */
    private static final String DEFAULT_PARAM = "DEFAULT";

    /** Deploy command <code>target</code> parameter name. */
    private static final String TARGET_PARAM = "target";

    /** Deploy command <code>name</code> parameter name. */
    private static final String NAME_PARAM = "name";

    /** Deploy command <code>contextroot</code> parameter name. */
    private static final String CTXROOT_PARAM = "contextroot";

    /** Deploy command <code>force</code> parameter name. */
    private static final String FORCE_PARAM = "force";

    /** Deploy command <code>properties</code> parameter name. */
    private static final String PROPERTIES_PARAM = "properties";

    /** Deploy command <code>libraries</code> parameter name. */
    private static final String LIBRARIES_PARAM = "libraries";

    /** Deploy command <code>force</code> parameter value. */
    private static final boolean FORCE_VALUE = true;

    /** Deploy command <code>hotDeploy</code> parameter name. */
    private static final String HOT_DEPLOY_PARAM = "hotDeploy";

    ////////////////////////////////////////////////////////////////////////////
    // Static methods //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Builds deploy query string for given command.
     * <p/>
     * <code>QUERY :: "DEFAULT" '=' &lt;path&gt; <br/>
     * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; '&' "force" '=' true | false <br/>
     * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ['&' "name" '=' &lt;name&gt; ] <br/>
     * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ['&' "target" '=' &lt;target&gt; ] <br/>
     * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ['&' "contextroot" '=' &lt;contextRoot&gt; ] <br/>
     * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ['&' "properties" '=' &lt;pname&gt; '=' &lt;pvalue&gt;
     *                                                  { ':' &lt;pname&gt; '=' &lt;pvalue&gt;} ]</code>
     * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ['&' "libraries" '=' &lt;lname&gt; '='
     * &lt;lvalue&gt; { ':' &lt;lname&gt; '=' &lt;lvalue&gt;} ]</code>
     * <p/>
     *
     * @param command GlassFish server administration deploy command entity.
     * @return Deploy query string for given command.
     */
    private static String query(final Command command) {
        // Prepare values
        String name;
        String path;
        String target;
        String ctxRoot;
        String force = Boolean.toString(FORCE_VALUE);
        String hotDeploy;
        CommandDeploy deploy;
        if (command instanceof CommandDeploy) {
            deploy = (CommandDeploy) command;
            if (deploy.path == null) {
                throw new CommandException(CommandException.ILLEGAL_NULL_VALUE);
            }
            name = Utils.sanitizeName(deploy.name);
            path = deploy.path.getAbsolutePath();
            target = deploy.target;
            ctxRoot = deploy.contextRoot;
            hotDeploy = Boolean.toString(deploy.hotDeploy);
        } else {
            throw new CommandException(
                    CommandException.ILLEGAL_COMAND_INSTANCE);
        }
        // Calculate StringBuilder initial length to avoid resizing
        StringBuilder sb = new StringBuilder(
                DEFAULT_PARAM.length() + 1 + path.length() +
                        1 + FORCE_PARAM.length() + 1 + force.length()
                        + queryPropertiesLength(
                                deploy.properties, PROPERTIES_PARAM)
                        + queryLibrariesLength(
                        		deploy.libraries, LIBRARIES_PARAM)
                        + (name != null && name.length() > 0
                                ? 1 + NAME_PARAM.length() + 1 + name.length()
                                : 0)
                        + (target != null
                                ? 1 + TARGET_PARAM.length() + 1 + target.length()
                                : 0)
                        + (ctxRoot != null && ctxRoot.length() > 0
                                ? 1 + CTXROOT_PARAM.length() + 1 + ctxRoot.length()
                                : 0)
        				+ (deploy.hotDeploy
				                ? HOT_DEPLOY_PARAM.length() + 1 + hotDeploy.length()
				                : 0));
        // Build query string
        sb.append(DEFAULT_PARAM).append(PARAM_ASSIGN_VALUE).append(path);
        sb.append(PARAM_SEPARATOR);
        sb.append(FORCE_PARAM).append(PARAM_ASSIGN_VALUE).append(force);
        if (name != null && name.length() > 0) {
            sb.append(PARAM_SEPARATOR);
            sb.append(NAME_PARAM).append(PARAM_ASSIGN_VALUE).append(name);
        }
        if (target != null) {
            sb.append(PARAM_SEPARATOR);
            sb.append(TARGET_PARAM).append(PARAM_ASSIGN_VALUE).append(target);
        }
        if (ctxRoot != null && ctxRoot.length() > 0) {
            sb.append(PARAM_SEPARATOR);
            sb.append(CTXROOT_PARAM).append(PARAM_ASSIGN_VALUE).append(ctxRoot);
        }
        if (deploy.hotDeploy) {
            sb.append(PARAM_SEPARATOR);
            sb.append(HOT_DEPLOY_PARAM);
            sb.append(PARAM_ASSIGN_VALUE).append(hotDeploy);
        }
        // Add properties into query string.
        queryPropertiesAppend(sb, deploy.properties,
                PROPERTIES_PARAM, true);
        queryLibrariesAppend(sb, deploy.libraries,
                LIBRARIES_PARAM, true);

        return sb.toString();
    }

    ////////////////////////////////////////////////////////////////////////////
    // Instance attributes //
    ////////////////////////////////////////////////////////////////////////////

    /** Holding data for command execution. */
    @SuppressWarnings("FieldNameHidesFieldInSuperclass")
    final CommandDeploy command;

    ////////////////////////////////////////////////////////////////////////////
    // Constructors //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Constructs an instance of administration command executor using HTTP interface.
     * <p>
     *
     * @param server GlassFish server entity object.
     * @param command GlassFish server administration command entity.
     */
    public RunnerHttpDeploy(final PayaraServer server,
            final Command command) {
        super(server, command, query(command));
        this.command = (CommandDeploy) command;
    }

    ////////////////////////////////////////////////////////////////////////////
    // Implemented Abstract Methods //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Send deployed file to the server via HTTP POST when it's not a directory deployment.
     * <p/>
     *
     * @return <code>true</code> if using HTTP POST to send to server or <code>false</code> otherwise
     */
    @Override
    public boolean getDoOutput() {
        return !command.dirDeploy;
    }

    /**
     * HTTP request method used for this command is <code>POST</code> for file deployment and
     * <code>GET</code> for directory deployment.
     *
     * @return HTTP request method used for this command.
     */
    @Override
    public String getRequestMethod() {
        return command.dirDeploy ? super.getRequestMethod() : "POST";
    }

    /**
     * Handle sending data to server using HTTP command interface.
     * <p/>
     * This is based on reading the code of <code>CLIRemoteCommand.java</code> from the server's code
     * repository. Since some asadmin commands need to send multiple files, the server assumes the input
     * is a ZIP stream.
     */
    @Override
    protected void handleSend(HttpURLConnection hconn) throws IOException {
        final String METHOD = "handleSend";
        InputStream istream = getInputStream();
        if (istream != null) {
            ZipOutputStream ostream = null;
            try {
                ostream = new ZipOutputStream(new BufferedOutputStream(
                        hconn.getOutputStream(), 1024 * 1024));
                ZipEntry e = new ZipEntry(command.path.getName());
                e.setExtra(getExtraProperties());
                ostream.putNextEntry(e);
                byte buffer[] = new byte[1024 * 1024];
                while (true) {
                    int n = istream.read(buffer);
                    if (n < 0) {
                        break;
                    }
                    ostream.write(buffer, 0, n);
                }
                ostream.closeEntry();
                ostream.flush();
            } finally {
                try {
                    istream.close();
                } catch (IOException ex) {
                    LOGGER.log(Level.INFO, METHOD, "ioException", ex);
                }
                if (ostream != null) {
                    try {
                        ostream.close();
                    } catch (IOException ex) {
                        LOGGER.log(Level.INFO, METHOD, "ioException", ex);
                    }
                }
            }
        } else if ("POST".equalsIgnoreCase(getRequestMethod())) {
            LOGGER.log(Level.INFO, METHOD, "noData");
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    // Fake Getters //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Set the content-type of information sent to the server. Returns <code>application/zip</code> for
     * file deployment and <code>null</code> (not set) for directory deployment.
     *
     * @return content-type of data sent to server via HTTP POST
     */
    @Override
    public String getContentType() {
        return command.dirDeploy ? null : "application/zip";
    }

    /**
     * Provide the lastModified date for data source whose <code>InputStream</code> is returned by
     * getInputStream.
     * <p/>
     *
     * @return String format of long integer from lastModified date of source.
     */
    @Override
    public String getLastModified() {
        return Long.toString(command.path.lastModified());
    }

    /**
     * Get <code>InputStream</code> object for deployed file.
     * <p/>
     *
     * @return <code>InputStream</code> object for deployed file or <code>null</code> for directory
     * deployment.
     */
    public InputStream getInputStream() {
        final String METHOD = "getInputStream";
        if (command.dirDeploy) {
            return null;
        } else {
            try {
                return new FileInputStream(command.path);
            } catch (FileNotFoundException fnfe) {
                LOGGER.log(Level.INFO, METHOD, "fileNotFound", fnfe);
                return null;
            }
        }
    }

}
