package com.j7.session;

import java.io.IOException;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Loader;
import org.apache.catalina.Session;
import org.apache.catalina.Valve;
import org.apache.catalina.session.ManagerBase;
import org.apache.catalina.session.StandardSession;
import org.apache.catalina.util.LifecycleSupport;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

import com.j7.cache.J7Manager;
import com.j7.jdbc.driver.ResultSet;

public class J7SessionManager extends ManagerBase implements Lifecycle {

	protected byte[] NULL_SESSION = "null".getBytes();

	private final Log log = LogFactory.getLog(J7SessionManager.class);

	protected String host = "127.0.0.1";
	protected int port = 55555;
	protected String database = "SESSIONS";
	protected String password = null;
	protected int timeout = 120;

	protected J7Manager J7M = null;

	protected J7SessionHandlerValve handlerValve;
	protected ThreadLocal<J7Session> currentSession = new ThreadLocal<J7Session>();
	protected ThreadLocal<String> currentSessionId = new ThreadLocal<String>();
	protected ThreadLocal<Boolean> currentSessionIsPersisted = new ThreadLocal<Boolean>();
	protected Serializer serializer;

	protected static String name = "J7SessionManager";

	protected String serializationStrategyClass = "com.j7.session.JavaSerializer";

	protected LifecycleSupport lifecycle = new LifecycleSupport(this);

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public String getDatabase() {
		return database;
	}

	public void setDatabase(String database) {
		this.database = database;
	}

	public int getTimeout() {
		return timeout;
	}

	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public void setSerializationStrategyClass(String strategy) {
		this.serializationStrategyClass = strategy;
	}

	@Override
	public int getRejectedSessions() {
		return 0;
	}

	public void setRejectedSessions(int i) {
	}

	@Override
	public void load() throws ClassNotFoundException, IOException {

	}

	@Override
	public void unload() throws IOException {

	}

	@Override
	public void addLifecycleListener(LifecycleListener listener) {
		lifecycle.addLifecycleListener(listener);
	}

	@Override
	public LifecycleListener[] findLifecycleListeners() {
		return lifecycle.findLifecycleListeners();
	}

	@Override
	public void removeLifecycleListener(LifecycleListener listener) {
		lifecycle.removeLifecycleListener(listener);
	}

	@Override
	protected synchronized void startInternal() throws LifecycleException {
		super.startInternal();

		setState(LifecycleState.STARTING);

		Boolean attachedToValve = false;
		for (Valve valve : getContainer().getPipeline().getValves()) {
			if (valve instanceof J7SessionHandlerValve) {
				this.handlerValve = (J7SessionHandlerValve) valve;
				this.handlerValve.setJ7SessionManager(this);
				log.info("Attached to J7SessionHandlerValve");
				attachedToValve = true;
				break;
			}
		}

		if (!attachedToValve) {
			String error = "Unable to attach to session handling valve; sessions cannot be saved after the request without the valve starting properly.";
			log.fatal(error);
			throw new LifecycleException(error);
		}

		try {
			initializeSerializer();
		} catch (ClassNotFoundException e) {
			log.fatal("Unable to load serializer", e);
			throw new LifecycleException(e);
		} catch (InstantiationException e) {
			log.fatal("Unable to load serializer", e);
			throw new LifecycleException(e);
		} catch (IllegalAccessException e) {
			log.fatal("Unable to load serializer", e);
			throw new LifecycleException(e);
		}

		log.info("Will expire sessions after " + getMaxInactiveInterval() + " seconds");

		initializeDatabaseConnection();

		setDistributable(true);
	}

	@Override
	protected synchronized void stopInternal() throws LifecycleException {
		if (log.isDebugEnabled()) {
			log.debug("Stopping");
		}

		setState(LifecycleState.STOPPING);

		try {
			J7M.close();
		} catch (Exception e) {
		}

		super.stopInternal();
	}

	@Override
	public Session createSession(String sessionId) {

		J7Session session = (J7Session) createEmptySession();

		session.setNew(true);
		session.setValid(true);
		session.setCreationTime(System.currentTimeMillis());
		session.setMaxInactiveInterval(getMaxInactiveInterval());

		String jvmRoute = getJvmRoute();

		try {
			if (null == sessionId) {
				sessionId = generateSessionId();
			}

			if (jvmRoute != null) {
				sessionId += '.' + jvmRoute;
			}

			session.setId(sessionId);
			session.tellNew();

			currentSession.set(session);
			currentSessionId.set(sessionId);
			currentSessionIsPersisted.set(false);
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		return session;
	}

	@Override
	public Session createEmptySession() {
		return new J7Session(this);
	}

	@Override
	public void add(Session session) {
		try {
			save(session);
		} catch (IOException ex) {
			log.warn("Unable to add to session manager store: " + ex.getMessage());
			throw new RuntimeException("Unable to add to session manager store.", ex);
		}
	}

	@Override
	public Session findSession(String id) throws IOException {
		J7Session session;

		if (id == null) {
			session = null;
			currentSessionIsPersisted.set(false);
		} else if (id.equals(currentSessionId.get())) {
			session = currentSession.get();
		} else {
			session = loadSessionFromJ7(id);

			if (session != null) {
				currentSessionIsPersisted.set(true);
			}
		}

		currentSession.set(session);
		currentSessionId.set(id);

		return session;
	}

	public void clear() {
		// J7M.clear(database);
	}

	public String[] keys() throws IOException {
		return J7M.get_all_session_id();
	}

	public J7Session loadSessionFromJ7(String id) throws IOException {

		if ((id == null) || (id.length() == 0)) {
			return (J7Session) createEmptySession();
		}

		StandardSession session = currentSession.get();

		if (session != null) {
			if (id.equals(session.getId())) {
				return (J7Session) session;
			} else {
				currentSession.remove();
			}
		}
		try {
			log.trace("Loading session " + id + " from J7Cache");

			byte[] data = J7M.load_a_session(id);
			if (data == null) {
				log.trace("Session " + id + " not found in J7Cache");
				J7Session ret = (J7Session) createEmptySession();
				ret.setId(id);
				currentSession.set(ret);
				return ret;
			}

			session = (J7Session) createEmptySession();
			session.setId(id);
			session.setManager(this);
			serializer.deserializeInto(data, session);

			session.setMaxInactiveInterval(getMaxInactiveInterval());
			session.access();
			session.setValid(true);
			session.setNew(false);

			currentSession.set((J7Session) session);
			return (J7Session) session;
		} catch (IOException e) {
			throw e;
		} catch (ClassNotFoundException ex) {
			log.trace("Unable to deserialize session ");
			throw new IOException("Unable to deserializeInto session", ex);
		}
	}

	public void save(Session session) throws IOException {

		try {
			log.trace("Saving session " + session + " into J7Cache");

			StandardSession standardsession = (J7Session) session;

			byte[] data = serializer.serializeFrom(standardsession);

			J7M.save_or_update_a_session(data, standardsession.getId());

			log.trace("Updated session with id " + session.getIdInternal());

			currentSessionIsPersisted.set(true);

		} catch (IOException e) {
			log.error(e.getMessage());

			throw e;
		}
	}

	@Override
	public void remove(Session session) {
		remove(session, false);
	}

	@Override
	public void remove(Session session, boolean update) {
		log.trace("Removing session ID : " + session.getId());
		J7M.remove_a_session(session.getId());

	}

	public void afterRequest() {
		J7Session J7Session = currentSession.get();
		if (J7Session != null) {
			currentSession.remove();
			currentSessionId.remove();
			currentSessionIsPersisted.remove();
			log.trace("Session removed from ThreadLocal :" + J7Session.getIdInternal());
		}
	}

	@Override
	public void processExpires() {

		ResultSet rsset = J7M.get_all_MaxInactiveInterval();

		if(rsset == null)
		{
			log.info("J7Cache has restart,now Tomcat will reconnecte" );
			J7M.clear(database);
		}
		else
		{
			long timeNow = System.currentTimeMillis();
	
			while (rsset.next() != null) {
				long timeEnd = Long.parseLong(rsset.getString("ACC_TIME"));
				long inactive = (timeNow - timeEnd) / 1000;
				if (inactive > (this.getMaxInactiveInterval())) {
					log.info("End expire sessions  processingTime " + inactive + " expired sessions: "
							+ rsset.getString("KEY"));
					log.info("Now InactiveInterval:" + inactive);
					log.info("Max InactiveInterval:" + (this.getMaxInactiveInterval()));
					J7M.remove_a_session(rsset.getString("KEY"));
				}
	
			}
		}
	}

	private void initializeDatabaseConnection() throws LifecycleException {
		try {
			J7M = new J7Manager(getHost(), getPort(), getDatabase());
			J7M.createSessionDataBase(database);
			J7M.createSessionMaxInactiveInterval();
		} catch (Exception e) {
			e.printStackTrace();
			throw new LifecycleException("Error Connecting to J7", e);
		}
	}

	private void initializeSerializer() throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		log.info("Attempting to use serializer :" + serializationStrategyClass);
		serializer = (Serializer) Class.forName(serializationStrategyClass).newInstance();

		Loader loader = null;

		if (container != null) {
			loader = container.getLoader();
		}

		ClassLoader classLoader = null;

		if (loader != null) {
			classLoader = loader.getClassLoader();
		}
		serializer.setClassLoader(classLoader);
	}
}
