/*
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.wookie.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.StringTokenizer;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.apache.wookie.util.hibernate.DBManagerFactory;
import org.apache.wookie.util.hibernate.IDBManager;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.security.HashUserRealm;
import org.mortbay.jetty.security.UserRealm;
import org.mortbay.jetty.webapp.WebAppContext;

public class Start {
	static final private Logger logger = Logger.getLogger(Start.class);
	private static int port = 8080;

	private static Server server;

	public static void main(String[] args) throws Exception {
	  boolean initDB = true;
		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			System.out.println("Runtime argument: " + arg);
			if (arg.startsWith("port=")) {
			  port = new Integer(arg.substring(5));
			} else if (arg.startsWith("initDB=")) {
			  initDB = !arg.substring(7).toLowerCase().equals("false");
			} else {
			  System.out.println("argument UNRECOGNISED - ignoring");
			}
		}
		
		if (initDB) {
  		try {
  			configureDatabase();
  		} catch (Exception e) {
  			if (e.getCause().getMessage().contains("duplicate key value")){
  			  StringBuilder sb = new StringBuilder("There was a problem setting up the database.\n");
  			  sb.append("If this is not the first time you are running Wookie in");
  			  sb.append(" standalone mode, then you should run \"ant clean-db\" before \"ant run\"");
  			  sb.append(" to clear the database.\n");
  			  sb.append("To run without re-configuring the database set \"initDB=false\" in the command line");
  				throw new IOException(sb.toString());
  			} else {
  				throw e;
  			}
  		}
		}
		
		configureServer();
		startServer();
	}

	/**
	 * Create the database by reading in the file widgetdb_derby.sql and executing all SQL found within.
	 * 
	 * @throws IOException  if the file is not found or is unreadable
	 */
	private static void configureDatabase() throws Exception {
		logger.debug("Configuring Derby Database");
		String sqlScript = IOUtils.toString(Start.class.getClassLoader().getResourceAsStream("widgetdb.sql"));
		final IDBManager dbManager = DBManagerFactory.getDBManager();
		StringTokenizer st = new StringTokenizer(sqlScript, ";"); 
		while (st.hasMoreTokens()) { 
			dbManager.beginTransaction(); 
			dbManager.createSQLQuery(st.nextToken()).executeUpdate(); 
			dbManager.commitTransaction(); 
		} 
	}

	private static void startServer() throws Exception, InterruptedException {
		logger.info("Starting Wookie Server");
		logger.info("point your browser at http://localhost:" + port + "/wookie");
		// The monitor thread will end this server instance when it receives a \n\r on port 8079
		Thread monitor = new MonitorThread();
	    monitor.start();
		server.start(); 			
		server.join();  			
		monitor = null;
		System.exit(0);
	}

	private static void configureServer() throws Exception {
		logger.info("Configuring Jetty server");
		server = new Server(port);
		WebAppContext context = new WebAppContext();
		context.setServer(server);
		context.setContextPath("/wookie");
		context.setWar("build/webapp/wookie");
		server.addHandler(context);
		
		HashUserRealm authedRealm = new HashUserRealm("Authentication Required","etc/jetty-realm.properties");
		server.setUserRealms(new UserRealm[]{authedRealm});
	}
	
	private static class MonitorThread extends Thread {

		private ServerSocket socket;

		public MonitorThread() {
			setDaemon(true);
			setName("StopMonitor");
			try {
				socket = new ServerSocket(8079, 1, InetAddress.getByName("127.0.0.1"));
			} catch(Exception e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public void run() {
			System.out.println("*** running jetty 'stop' thread");
			Socket accept;
			try {
				accept = socket.accept();
				BufferedReader reader = new BufferedReader(new InputStreamReader(accept.getInputStream()));
				reader.readLine();
				System.out.println("*** stopping jetty embedded server");
				server.stop();
				accept.close();
				socket.close();	                	                
			} catch(Exception e) {
				throw new RuntimeException(e);
			}
		}
	}
}
