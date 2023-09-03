/*******************************************************************************
 * Freeciv-web - the web version of Freeciv. http://play.freeciv.org/
 * Copyright (C) 2009-2017 The Freeciv-web project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package org.freeciv.servlet;

import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;

import java.sql.*;

import javax.sql.*;

import org.freeciv.persistence.DbManager;
import org.freeciv.util.Constants;

import javax.naming.*;


/**
 * This class is responsible for finding an available freeciv-web server for clients
 * based on information in the metaserver database.
 *
 * URL: /civclientlauncher
 */
public class CivclientLauncher extends HttpServlet {
	
	private static final long serialVersionUID = 1L;

	public void doPost(HttpServletRequest request, HttpServletResponse response)
			throws IOException, ServletException {

		// Parse input parameters ...
		String action = request.getParameter("action");
		if (action == null) {
			action = "new";
		}

		String civServerPort = request.getParameter("civserverport");

		Connection conn = null;
		try {
			Context env = (Context) (new InitialContext().lookup(Constants.JNDI_CONNECTION));
			DataSource ds = (DataSource) env.lookup(Constants.JNDI_DDBBCON_MYSQL);
			conn = ds.getConnection();
			
			String gameType;
			switch (action) {
			case "new":
			case "load":
			case "observe":
			case "multi":
			case "hotseat":
			case "earthload":
				gameType = "singleplayer";
				break;
			case "pbem":
				gameType = "pbem";
				break;
			default:
				response.setHeader("result", "invalid port validation");
				response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, Constants.ERRMSG_SERVERUNAVAILABLE
						);
				return;
			}
			

			if (civServerPort == null || civServerPort.length() == 0) {
				// If the user requested a new game, then get host and port for an available
				// server from the metaserver DB, and use that one.
				String lookupQuery = DbManager.getQuerySelectPort();
				PreparedStatement lookupStmt = conn.prepareStatement(lookupQuery);
				lookupStmt.setString(1, gameType);
				ResultSet lookupRs = lookupStmt.executeQuery();
				if (lookupRs.next()) {
					civServerPort = Integer.toString(lookupRs.getInt(1));
				} else {
					response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, Constants.ERRMSG_SERVERUNAVAILABLE2);
					return;
				}
			}

			/* Validate port */
			String validateQuery = DbManager.getQueryCountServers();
			PreparedStatement validateStmt = conn.prepareStatement(validateQuery);
			if (civServerPort == null || civServerPort.length() == 0) {
				response.setHeader("result", "invalid port validation");
				response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, Constants.ERRMSG_SERVERUNAVAILABLE);
				return;
			}

			validateStmt.setInt(1, Integer.parseInt(civServerPort));
			ResultSet validateRs = validateStmt.executeQuery();
			validateRs.next();
			if (validateRs.getInt(1) != 1) {
				response.setHeader("result", "invalid port validation");
				response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, Constants.ERRMSG_INVALIDINPUT);
				return;
			}

		} catch (Exception err) {
			response.setHeader("result", err.getMessage());
			err.printStackTrace();
			return;
		} finally {
			if (conn != null)
				try {
					conn.close();
				} catch (SQLException e) {
					e.printStackTrace();
				}
		}

		response.setHeader("port", civServerPort);
		response.setHeader("result", "success");
		response.setHeader("action", action);
		response.getOutputStream().print("success");

	}

	public void doGet(HttpServletRequest request, HttpServletResponse response)
			throws IOException, ServletException {

		response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, Constants.ERRMSG_POST);

	}

}
